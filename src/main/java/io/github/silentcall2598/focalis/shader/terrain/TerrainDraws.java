// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.shader.terrain;

import io.github.silentcall2598.focalis.render.lifecycle.RenderDrawKind;
import io.github.silentcall2598.focalis.render.lifecycle.RenderStage;
import io.github.silentcall2598.focalis.shader.program.DrawSuspension;
import io.github.silentcall2598.focalis.shader.program.TerrainStreamGate;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.Objects;

/**
 * The draw side of the stream. A terrain program that reads the stream starts its stage stepped aside, and a chunk
 * draw binds it again only after pointing the arrays at that chunk's stream. A chunk without a usable stream, or
 * terrain drawn by something other than the hooked renderers, draws the vanilla way. The array state the stream
 * changes is captured right before its first change in a layer and put back when the layer is done. A chunk without
 * a stream that follows a streamed one gets the arrays that were on put back for its own draw. Client thread only.
 */
final class TerrainDraws implements TerrainStreamGate {

    /** What the draws need to know about the stream right now. */
    interface Status {

        /** Whether draws of this layer can get the stream at all. */
        boolean ready(RenderDrawKind layer);

        /** The generation chunks need for their stream to be drawn, or 0 when nothing can be drawn. */
        int generation();

        /** A terrain layer drew without passing the hooked chunk renderers. Called once per stage. */
        void drawnElsewhere(RenderDrawKind layer);
    }

    private final AttributeGl gl;
    private final Status status;
    private final AttributeState saved = new AttributeState();

    // The stream scopes the binding opened, innermost last.
    private RenderStage[] stages = new RenderStage[4];
    private RenderDrawKind[] layers = new RenderDrawKind[4];
    private DrawSuspension[] suspensions = new DrawSuspension[4];
    private boolean[] seen = new boolean[4];
    private int depth;
    // Whether the innermost stream scope is stepped aside right now.
    private boolean stepped;

    // The container draw in progress.
    private boolean drawing;
    // Whether it feeds the innermost stream scope.
    private boolean feeding;
    private int generation;
    private boolean arraysCaptured;
    private boolean currentCaptured;
    private boolean arraysOn;
    // Whether the current normal and generic values may differ from the incoming ones since a draw or list set them.
    private boolean valuesDirty;

    TerrainDraws(AttributeGl gl, Status status) {
        this.gl = Objects.requireNonNull(gl, "gl");
        this.status = Objects.requireNonNull(status, "status");
    }

    @Override
    public boolean ready(RenderDrawKind layer) {
        return status.ready(layer);
    }

    @Override
    public void opened(RenderStage stage, RenderDrawKind layer, DrawSuspension suspension) {
        if (stepped) {
            throw new IllegalStateException("A stream scope for " + stage + "/" + layer + " opened inside "
                    + stages[depth - 1] + "/" + layers[depth - 1] + ", which is stepped aside");
        }
        if (depth == stages.length) {
            int grown = depth * 2;
            stages = Arrays.copyOf(stages, grown);
            layers = Arrays.copyOf(layers, grown);
            suspensions = Arrays.copyOf(suspensions, grown);
            seen = Arrays.copyOf(seen, grown);
        }
        stages[depth] = stage;
        layers[depth] = layer;
        suspensions[depth] = suspension;
        seen[depth] = false;
        depth++;
        // Nothing draws with the program until a chunk draw here proves it gets the stream.
        stepped = true;
        suspension.suspendForDraw(stage, layer);
    }

    @Override
    public void closed(RenderStage stage, RenderDrawKind layer) {
        if (depth == 0 || stages[depth - 1] != stage || layers[depth - 1] != layer) {
            throw new IllegalStateException("Stream scope " + stage + "/" + layer + " closed while "
                    + (depth == 0 ? "none is open" : stages[depth - 1] + "/" + layers[depth - 1] + " is innermost"));
        }
        int top = --depth;
        DrawSuspension suspension = suspensions[top];
        suspensions[top] = null;
        boolean drewHere = seen[top];
        if (stepped) {
            stepped = false;
            // The scope ends the normal way, which needs it to own its program again first.
            suspension.resumeAfterDraw(stage, layer);
        }
        if (!drewHere) {
            status.drawnElsewhere(layer);
        }
    }

    @Override
    public void reset() {
        Arrays.fill(suspensions, 0, depth, null);
        depth = 0;
        stepped = false;
        // A container draw in progress still puts back the arrays when it ends, it just stops feeding.
        feeding = false;
    }

    /** One of the hooked chunk renderers starts drawing a layer. */
    void containerStarted(@Nullable RenderDrawKind layer) {
        drawing = true;
        arraysCaptured = false;
        currentCaptured = false;
        arraysOn = false;
        valuesDirty = false;
        generation = status.generation();
        int top = depth - 1;
        feeding = top >= 0 && layer != null && layers[top] == layer && generation != 0;
        if (top >= 0 && layers[top] == layer) {
            seen[top] = true;
        }
    }

    /**
     * Right before vanilla draws one chunk layer's vertex buffer, with that buffer bound and vanilla's pointers set.
     *
     * @param vanillaBuffer the GL buffer vanilla bound for the draw
     */
    void chunkBuffer(@Nullable BufferStream chunkStream, int vanillaBuffer, int vanillaVertices) {
        if (!feeding) {
            return;
        }
        if (chunkStream != null && chunkStream.valid && chunkStream.buffer != 0
                && chunkStream.generation == generation && chunkStream.vertices == vanillaVertices) {
            if (!arraysCaptured) {
                saved.captureArrays(gl);
                saved.captureCurrent(gl);
                arraysCaptured = true;
                currentCaptured = true;
            }
            gl.bindArrayBuffer(chunkStream.buffer);
            StreamArrays.pointAtBuffer(gl, StreamLayout.BUFFER);
            gl.bindArrayBuffer(vanillaBuffer);
            if (!arraysOn) {
                StreamArrays.clearDivisors(gl, saved);
                StreamArrays.enable(gl, true);
                arraysOn = true;
            }
            stepBack();
            // A draw from enabled arrays leaves their current values indeterminate.
            valuesDirty = true;
        } else {
            // Left on, the arrays would read past the end of another chunk's smaller stream. The draw gets the arrays
            // it would have seen without the stream, which are only off if they were off.
            if (arraysOn) {
                // The hook doesn't always know vanilla's buffer, and the one bound stays bound either way.
                saved.restoreForDraw(gl, vanillaBuffer > 0 ? vanillaBuffer : gl.arrayBuffer());
                arraysOn = false;
            }
            giveBackValues();
            stepAside();
        }
    }

    /**
     * Right before vanilla calls one chunk layer's display list. A list compiled with the stream sets the current
     * normal and generic values as it plays, so those are captured first and put back when the layer is done, also
     * when no scope needs the stream.
     *
     * @param listGeneration the generation the list was compiled with, 0 when it has no stream
     */
    void chunkList(int listGeneration) {
        boolean carriesValues = listGeneration != 0 && drawing;
        if (carriesValues && !currentCaptured) {
            saved.captureCurrent(gl);
            currentCaptured = true;
        }
        boolean fed = feeding && listGeneration == generation;
        if (!fed) {
            giveBackValues();
        }
        if (carriesValues) {
            // The list sets the values of its last vertex as it plays.
            valuesDirty = true;
        }
        if (!feeding) {
            return;
        }
        if (fed) {
            stepBack();
        } else {
            stepAside();
        }
    }

    /** The container is done with the layer. */
    void containerFinished() {
        try {
            if (feeding && !stepped && depth > 0) {
                stepped = true;
                suspensions[depth - 1].suspendForDraw(stages[depth - 1], layers[depth - 1]);
            }
        } finally {
            restore();
        }
    }

    /**
     * After a failure in the middle of a layer. The rest of the layer draws the vanilla way, with the program stepped
     * aside and the arrays put back.
     */
    void fail() {
        boolean wasFeeding = feeding;
        feeding = false;
        try {
            if (wasFeeding && !stepped && depth > 0) {
                stepped = true;
                suspensions[depth - 1].suspendForDraw(stages[depth - 1], layers[depth - 1]);
            }
        } finally {
            restore();
        }
    }

    boolean drawing() {
        return drawing;
    }

    /** Whether the container draw in progress feeds a stream scope, so its chunk draws need to come here. */
    boolean feeding() {
        return feeding;
    }

    // A draw that doesn't use the stream gets the values it would have seen without it. The arrays it reads were
    // already turned off, and the rest of what the stream changed can wait until the layer is done.
    private void giveBackValues() {
        if (valuesDirty) {
            saved.restoreCurrent(gl);
            valuesDirty = false;
        }
    }

    private void restore() {
        boolean arrays = arraysCaptured;
        boolean current = currentCaptured;
        drawing = false;
        feeding = false;
        arraysCaptured = false;
        currentCaptured = false;
        arraysOn = false;
        valuesDirty = false;
        if (arrays) {
            saved.restoreArrays(gl);
        }
        if (current) {
            saved.restoreCurrent(gl);
        }
    }

    private void stepBack() {
        if (stepped) {
            stepped = false;
            int top = depth - 1;
            suspensions[top].resumeAfterDraw(stages[top], layers[top]);
        }
    }

    private void stepAside() {
        if (!stepped) {
            stepped = true;
            int top = depth - 1;
            suspensions[top].suspendForDraw(stages[top], layers[top]);
        }
    }
}
