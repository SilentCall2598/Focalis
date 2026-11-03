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

final class TerrainDraws implements TerrainStreamGate {

    interface Status {

        boolean ready(RenderDrawKind layer);

        int generation();

        default boolean colors() {
            return false;
        }

        void drawnElsewhere(RenderDrawKind layer);
    }

    private final AttributeGl gl;
    private final Status status;
    private final AttributeState saved = new AttributeState();

    private RenderStage[] stages = new RenderStage[4];
    private RenderDrawKind[] layers = new RenderDrawKind[4];
    private DrawSuspension[] suspensions = new DrawSuspension[4];
    private boolean[] seen = new boolean[4];
    private int depth;
    private boolean stepped;

    private boolean drawing;
    private boolean feeding;
    private int generation;
    private StreamLayout layout = StreamLayout.BUFFER;
    private int lastVanillaBuffer;
    private boolean arraysCaptured;
    private boolean currentCaptured;
    private boolean arraysOn;
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
        feeding = false;
    }

    void containerStarted(@Nullable RenderDrawKind layer) {
        drawing = true;
        arraysCaptured = false;
        currentCaptured = false;
        arraysOn = false;
        valuesDirty = false;
        generation = status.generation();
        lastVanillaBuffer = 0;
        layout = status.colors() ? StreamLayout.BUFFER_COLOR : StreamLayout.BUFFER;
        int top = depth - 1;
        feeding = top >= 0 && layer != null && layers[top] == layer && generation != 0;
        if (top >= 0 && layers[top] == layer) {
            seen[top] = true;
        }
    }

    void chunkBuffer(@Nullable BufferStream chunkStream, int vanillaBuffer, int vanillaVertices) {
        if (!feeding) {
            return;
        }
        if (vanillaBuffer > 0) {
            lastVanillaBuffer = vanillaBuffer;
        }
        if (chunkStream != null && chunkStream.valid && chunkStream.buffer != 0
                && chunkStream.generation == generation && chunkStream.vertices == vanillaVertices) {
            if (!arraysCaptured) {
                saved.captureArrays(gl);
                if (layout.hasColors()) {
                    saved.captureColor(gl);
                }
                saved.captureCurrent(gl);
                arraysCaptured = true;
                currentCaptured = true;
            }
            gl.bindArrayBuffer(chunkStream.buffer);
            StreamArrays.pointAtBuffer(gl, layout);
            gl.bindArrayBuffer(vanillaBuffer);
            if (!arraysOn) {
                StreamArrays.clearDivisors(gl, saved);
                StreamArrays.enable(gl, true);
                if (layout.hasColors()) {
                    gl.colorArray(true);
                }
                arraysOn = true;
            }
            stepBack();
            // A draw from enabled arrays leaves their current values indeterminate.
            valuesDirty = true;
        } else {
            // Left on, the arrays would read past the end of another chunk's smaller stream.
            if (arraysOn) {
                saved.restoreForDraw(gl, vanillaBuffer > 0 ? vanillaBuffer : gl.arrayBuffer());
                arraysOn = false;
            }
            giveBackValues();
            stepAside();
        }
    }

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

    boolean feeding() {
        return feeding;
    }

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
            // Vanilla sets the color pointer for every chunk, so a layer ends with it in the last chunk's buffer, not in the
            // buffer of the first streamed chunk, where the arrays were captured.
            saved.endColorOn(lastVanillaBuffer);
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
