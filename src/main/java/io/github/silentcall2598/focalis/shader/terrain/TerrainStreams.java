// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.shader.terrain;

import io.github.silentcall2598.focalis.render.lifecycle.RenderDrawKind;
import io.github.silentcall2598.focalis.render.lifecycle.RenderLifecycle;
import io.github.silentcall2598.focalis.render.lifecycle.RenderPhase;
import io.github.silentcall2598.focalis.render.lifecycle.RenderStage;
import io.github.silentcall2598.focalis.shader.material.MaterialIds;
import io.github.silentcall2598.focalis.shader.program.TerrainStreamGate;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.chunk.RenderChunk;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.renderer.vertex.VertexBuffer;
import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraft.util.BlockRenderLayer;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nullable;
import java.nio.ByteBuffer;
import java.util.Locale;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.LongAdder;

/**
 * The chunk attribute stream as a whole. The chunk mixins hand every layer build, upload and draw to it, and the world
 * programs turn it on with a plan when a terrain program needs it. Without a plan every hook returns right away. A
 * hook never lets a Focalis failure reach vanilla's code. The failure turns the stream off and waits here until the
 * world programs pick it up and fail. Builds run on worker threads too, everything else on the client thread.
 */
public final class TerrainStreams {

    private static final String OWNER = "focalis:terrain_streams";
    private static final int UNCHECKED = 0;
    private static final int PASSED = 1;
    private static final int TURNED_DOWN = 2;

    @Nullable
    private static volatile TerrainStreamPlan plan;
    // Client thread only.
    @Nullable
    private static MaterialIds planIds;
    private static int lastGeneration;
    private static boolean reloadRequested;
    // Set when the plan went away, so the streams of chunks that were built with it are given back at the next frame,
    // unless a plan is there again by then.
    private static boolean releaseRequested;
    @Nullable
    private static TerrainDraws draws;

    // What the check found for each path. Chunk builds read these too.
    private static volatile int bufferPath = UNCHECKED;
    private static volatile int listPath = UNCHECKED;
    // Set once a terrain layer was drawn without the hooked chunk renderers. Final for the session. Client thread only.
    private static boolean renderersReplaced;
    // The GL buffers of the streams of vertex buffer chunk layers. Client thread only.
    private static StreamBuffers buffers = new StreamBuffers(LwjglAttributeGl.INSTANCE);

    private static final AtomicReference<Throwable> failure = new AtomicReference<>();
    // Each kind of note is only queued once a session, so the log can't fill up from worker threads.
    private static final Set<String> noted = ConcurrentHashMap.newKeySet();
    private static final Queue<String> notes = new ConcurrentLinkedQueue<>();

    private static final LongAdder layersStreamed = new LongAdder();
    private static final LongAdder layersWithout = new LongAdder();
    private static final LongAdder quadsWritten = new LongAdder();
    private static final LongAdder buildNanos = new LongAdder();
    private static long uploads;
    private static long uploadedBytes;
    private static long uploadNanos;
    private static long uploadsRefused;

    private TerrainStreams() {
    }

    /** Registers the renderer reload the stream asks for, before features register their listeners. */
    public static void install(RenderLifecycle lifecycle) {
        lifecycle.register(RenderStage.FRAME, OWNER, (stage, phase, drawKind, partialTicks) -> {
            if (phase == RenderPhase.START) {
                serviceReload();
            }
        });
    }

    /** Where the world programs ask whether a terrain stage can feed its program the stream. */
    public static TerrainStreamGate gate() {
        return draws();
    }

    /**
     * Turns the stream on with the material IDs in use. New IDs start a new generation, and the renderers reload at the
     * next frame so every chunk is built again with it.
     *
     * @return whether this started a new plan. Nothing starts after a failure, a turned down path or replaced
     *     renderers.
     */
    public static boolean publish(MaterialIds ids) {
        if (renderersReplaced || failure.get() != null
                || (OpenGlHelper.useVbo() ? bufferPath : listPath) == TURNED_DOWN) {
            return false;
        }
        if (plan != null && planIds == ids) {
            return false;
        }
        int generation = ++lastGeneration == 0 ? ++lastGeneration : lastGeneration;
        planIds = ids;
        plan = new TerrainStreamPlan(generation, ids::block);
        reloadRequested = true;
        return true;
    }

    /**
     * Turns the stream off. The buffers of streams already built can't be drawn anymore, so they are deleted at the
     * next frame without touching the chunks. The display lists compiled with the stream, and the builders' own
     * buffers, stay until their chunks and builders build again.
     *
     * @param reload whether to reload the renderers at the next frame, which builds every chunk again without it
     */
    public static void withdraw(boolean reload) {
        boolean was = plan != null;
        plan = null;
        planIds = null;
        if (reload && was) {
            reloadRequested = true;
        }
        releaseRequested = true;
    }

    public static boolean active() {
        return plan != null;
    }

    /** The first Focalis failure in the stream, or null. */
    @Nullable
    public static Throwable failure() {
        return failure.get();
    }

    /** Runs the check for the path the renderers use, once per path, and logs what the stream noted. Client thread. */
    public static void prepare(Logger logger) {
        String note;
        while ((note = notes.poll()) != null) {
            logger.info(note);
        }
        if (plan == null) {
            return;
        }
        boolean buffers = OpenGlHelper.useVbo();
        if ((buffers ? bufferPath : listPath) != UNCHECKED) {
            return;
        }
        long start = System.nanoTime();
        StreamSelfTest.Result result = StreamSelfTest.run(buffers ? StreamLayout.BUFFER : StreamLayout.LIST);
        boolean ok = result.outcome == StreamSelfTest.Outcome.PASSED;
        if (buffers) {
            bufferPath = ok ? PASSED : TURNED_DOWN;
        } else {
            listPath = ok ? PASSED : TURNED_DOWN;
        }
        String what = buffers ? "vertex buffers" : "display lists";
        String took = String.format(Locale.ROOT, "%.1f", (System.nanoTime() - start) / 1_000_000.0);
        if (ok) {
            logger.info("The chunk attribute stream works with {}, checked in {} ms: {}", what, took, result.detail);
        } else {
            logger.warn("The chunk attribute stream can't be used with {}, so terrain programs that read gl_Normal,"
                    + " mc_Entity, mc_midTexCoord or at_tangent draw the normal way: {}", what, result);
            // Chunks stop carrying streams nothing can draw, and the ones built so far go at the next reload.
            withdraw(true);
        }
    }

    /** What the stream has done this session, for diagnostics. */
    public static String statistics() {
        return "layers with the stream " + layersStreamed.sum() + ", without " + layersWithout.sum() + ", quads "
                + quadsWritten.sum() + ", build ms " + String.format(Locale.ROOT, "%.1f",
                buildNanos.sum() / 1_000_000.0) + ", uploads " + uploads + " with " + uploadedBytes + " bytes in "
                + String.format(Locale.ROOT, "%.1f", uploadNanos / 1_000_000.0) + " ms, refused " + uploadsRefused
                + ", live stream buffers " + buffers.live();
    }

    /** How many stream buffers exist right now, for checking that none outlive their chunks. */
    public static int liveBuffers() {
        return buffers.live();
    }

    // Chunk builds, any thread.

    /** Vanilla starts a layer build with this builder, for a full rebuild or to sort translucent data again. */
    public static void layerStarted(BufferBuilder buffer) {
        try {
            if (!(buffer instanceof StreamBufferBuilder)) {
                return;
            }
            StreamBufferBuilder holder = (StreamBufferBuilder) buffer;
            StreamCompanion companion = holder.focalis$streamCompanion();
            TerrainStreamPlan current = usablePlan();
            if (companion == null) {
                if (current == null) {
                    return;
                }
                companion = new StreamCompanion();
                holder.focalis$streamCompanion(companion);
            }
            companion.begin(current, OpenGlHelper.useVbo() ? StreamLayout.BUFFER : StreamLayout.LIST);
        } catch (RuntimeException | LinkageError e) {
            failed(e);
        }
    }

    public static void blockStarted(BufferBuilder buffer) {
        try {
            StreamCompanion companion = activeCompanion(buffer);
            if (companion != null) {
                companion.blockStarted(buffer.getVertexCount());
            }
        } catch (RuntimeException | LinkageError e) {
            failed(e);
        }
    }

    /** The actual state vanilla's dispatcher worked out for a block rendering into this builder. */
    public static void actualState(BufferBuilder buffer, IBlockState actual) {
        try {
            StreamCompanion companion = activeCompanion(buffer);
            if (companion != null) {
                companion.observeActualState(actual);
            }
        } catch (RuntimeException | LinkageError e) {
            failed(e);
        }
    }

    public static void blockEnded(BufferBuilder buffer, IBlockState stored) {
        try {
            StreamCompanion companion = activeCompanion(buffer);
            if (companion != null) {
                companion.blockEnded(buffer.getVertexCount(), stored);
                noteProblem(companion);
            }
        } catch (RuntimeException | LinkageError e) {
            failed(e);
        }
    }

    public static void sortStarting(BufferBuilder buffer) {
        try {
            StreamCompanion companion = activeCompanion(buffer);
            if (companion != null) {
                companion.sortStarting(buffer.getByteBuffer(), buffer.getVertexCount(),
                        isBlockFormat(buffer.getVertexFormat()));
                noteProblem(companion);
            }
        } catch (RuntimeException | LinkageError e) {
            failed(e);
        }
    }

    /** The index array of vanilla's translucent sort in this builder, right after it was sorted. */
    public static void sortObserved(BufferBuilder buffer, Object[] sorted) {
        try {
            StreamCompanion companion = activeCompanion(buffer);
            if (companion != null) {
                companion.sortObserved(sorted);
            }
        } catch (RuntimeException | LinkageError e) {
            failed(e);
        }
    }

    public static void sortFinished(BufferBuilder buffer) {
        try {
            StreamCompanion companion = activeCompanion(buffer);
            if (companion != null) {
                companion.sortFinished(buffer.getByteBuffer());
                noteProblem(companion);
            }
        } catch (RuntimeException | LinkageError e) {
            failed(e);
        }
    }

    /** Vanilla saved the sorted translucent data, which keeps the IDs in the same order from now on. */
    public static void stateSaved(BufferBuilder buffer, @Nullable BufferBuilder.State state) {
        try {
            StreamCompanion companion = activeCompanion(buffer);
            if (companion == null || !(state instanceof StreamVertexState)) {
                return;
            }
            SortedIds ids = companion.sortedIds();
            if (ids != null) {
                ((StreamVertexState) state).focalis$sortedIds(ids);
            }
        } catch (RuntimeException | LinkageError e) {
            failed(e);
        }
    }

    /** Vanilla put saved translucent data back into the builder to sort it again. */
    public static void stateRestored(BufferBuilder buffer, @Nullable BufferBuilder.State state) {
        try {
            StreamCompanion companion = activeCompanion(buffer);
            if (companion == null) {
                return;
            }
            SortedIds saved = state instanceof StreamVertexState ? ((StreamVertexState) state).focalis$sortedIds()
                    : null;
            companion.restoreSorted(saved, buffer.getVertexCount());
            noteProblem(companion);
        } catch (RuntimeException | LinkageError e) {
            failed(e);
        }
    }

    /** Vanilla finished the layer, and its data won't change before the upload. */
    public static void layerFinished(BufferBuilder buffer) {
        try {
            StreamCompanion companion = companion(buffer);
            if (companion == null) {
                return;
            }
            if (!companion.active()) {
                // Something earlier in the build already ruled this layer out.
                if (companion.problem() != null) {
                    layersWithout.increment();
                    noteProblem(companion);
                }
                return;
            }
            long start = System.nanoTime();
            companion.finish(buffer.getByteBuffer(), buffer.getVertexCount(),
                    isBlockFormat(buffer.getVertexFormat()), buffer.getDrawMode());
            buildNanos.add(System.nanoTime() - start);
            if (companion.vertices() > 0) {
                layersStreamed.increment();
                quadsWritten.add(companion.vertices() / 4);
            } else if (companion.problem() != null) {
                layersWithout.increment();
                noteProblem(companion);
            }
        } catch (RuntimeException | LinkageError e) {
            failed(e);
        }
    }

    // Uploads, client thread.

    /** Vanilla just uploaded a layer to its vertex buffer, which replaced the buffer's stream. */
    public static void bufferUploaded(BufferBuilder buffer, VertexBuffer vertexBuffer) {
        try {
            if (!(vertexBuffer instanceof StreamVertexBuffer)) {
                return;
            }
            streamUploaded(companion(buffer), usablePlan(), (StreamVertexBuffer) vertexBuffer);
        } catch (RuntimeException | LinkageError e) {
            failed(e);
        }
    }

    static void streamUploaded(@Nullable StreamCompanion companion, @Nullable TerrainStreamPlan current,
            StreamVertexBuffer target) {
        BufferStream stream = target.focalis$stream();
        // A vertex buffer vanilla already deleted never gets a new stream buffer, so a late upload can't leak one.
        boolean usable = companion != null && current != null && target.focalis$glBuffer() >= 0
                && companion.generation() == current.generation()
                && companion.ready(StreamLayout.BUFFER, target.focalis$vertexCount());
        if (!usable) {
            if (stream != null) {
                buffers.release(stream);
            }
            return;
        }
        if (stream == null) {
            stream = new BufferStream();
            target.focalis$stream(stream);
        }
        long start = System.nanoTime();
        String problem = buffers.upload(stream, companion.stream(), companion.vertices(), companion.generation());
        if (problem != null) {
            // Only this layer goes without, drawing the normal way, and its next upload tries again.
            uploadsRefused++;
            note("Some chunk layers draw without the attribute stream because " + problem + ". This is only logged"
                    + " once for each reason.");
            return;
        }
        uploads++;
        uploadedBytes += companion.vertices() * StreamLayout.BUFFER.stride();
        uploadNanos += System.nanoTime() - start;
    }

    /** Vanilla is putting new data into this vertex buffer, so whatever stream it had no longer belongs to it. */
    public static void vertexDataReplaced(VertexBuffer vertexBuffer) {
        if (vertexBuffer instanceof StreamVertexBuffer) {
            BufferStream stream = ((StreamVertexBuffer) vertexBuffer).focalis$stream();
            if (stream != null) {
                stream.valid = false;
            }
        }
    }

    /** Vanilla deleted the vertex buffer, so its stream buffer goes with it. */
    public static void vertexBufferDeleted(VertexBuffer vertexBuffer) {
        try {
            if (!(vertexBuffer instanceof StreamVertexBuffer)) {
                return;
            }
            StreamVertexBuffer target = (StreamVertexBuffer) vertexBuffer;
            BufferStream stream = target.focalis$stream();
            if (stream != null) {
                buffers.release(stream);
                target.focalis$stream(null);
            }
        } catch (RuntimeException | LinkageError e) {
            failed(e);
        }
    }

    // One list upload at a time, client thread.
    private static final AttributeState listUpload = new AttributeState();
    private static int listUploadBuffer;

    /**
     * Vanilla is about to compile a layer's display list. With a stream ready the arrays point at it, so the list keeps
     * it too.
     *
     * @return whether the stream goes into the list, to hand back to {@link #listUploadFinished}
     */
    public static boolean listUploadStarting(BufferBuilder buffer) {
        boolean captured = false;
        AttributeGl gl = LwjglAttributeGl.INSTANCE;
        try {
            StreamCompanion companion = companion(buffer);
            TerrainStreamPlan current = usablePlan();
            if (companion == null || current == null || companion.generation() != current.generation()
                    || !companion.ready(StreamLayout.LIST, buffer.getVertexCount())) {
                return false;
            }
            listUpload.captureArrays(gl);
            captured = true;
            listUploadBuffer = gl.arrayBuffer();
            if (listUploadBuffer != 0) {
                gl.bindArrayBuffer(0);
            }
            StreamArrays.pointAtClient(gl, StreamLayout.LIST, companion.stream());
            StreamArrays.clearDivisors(gl, listUpload);
            StreamArrays.enable(gl, true);
            return true;
        } catch (RuntimeException | LinkageError e) {
            failed(e);
            if (captured) {
                // The list compiles without the stream, but not with half of the arrays changed.
                try {
                    listUpload.restoreArrays(gl);
                    if (gl.arrayBuffer() != listUploadBuffer) {
                        gl.bindArrayBuffer(listUploadBuffer);
                    }
                } catch (RuntimeException | LinkageError suppressed) {
                    e.addSuppressed(suppressed);
                }
            }
            return false;
        }
    }

    /** The list is compiled. Records whether it has the stream, and puts the arrays back. */
    public static void listUploadFinished(boolean streamed, BufferBuilder buffer, RenderChunk chunk,
            BlockRenderLayer layer) {
        try {
            int generation = 0;
            if (streamed) {
                AttributeGl gl = LwjglAttributeGl.INSTANCE;
                listUpload.restoreArrays(gl);
                if (gl.arrayBuffer() != listUploadBuffer) {
                    gl.bindArrayBuffer(listUploadBuffer);
                }
                StreamCompanion companion = companion(buffer);
                generation = companion == null ? 0 : companion.generation();
                uploads++;
            }
            if (chunk instanceof StreamDisplayLists) {
                ((StreamDisplayLists) chunk).focalis$streamGeneration(layer.ordinal(), generation);
            }
        } catch (RuntimeException | LinkageError e) {
            failed(e);
        }
    }

    // Draws, client thread.

    /** One of vanilla's chunk renderers starts drawing a layer. */
    public static void containerStarting(BlockRenderLayer layer) {
        // Nobody asked for the gate, so there is nothing to feed.
        TerrainDraws current = draws;
        if (current == null) {
            return;
        }
        try {
            if (current.drawing()) {
                throw new IllegalStateException("A chunk layer draw started inside another one");
            }
            current.containerStarted(kind(layer));
        } catch (RuntimeException | LinkageError e) {
            drawFailed(e);
        }
    }

    /** Right before vanilla draws one chunk's vertex buffer, with it bound and vanilla's pointers set. */
    public static void chunkBuffer(VertexBuffer vertexBuffer) {
        TerrainDraws current = draws;
        if (current == null || !current.feeding()) {
            return;
        }
        try {
            if (vertexBuffer instanceof StreamVertexBuffer) {
                StreamVertexBuffer target = (StreamVertexBuffer) vertexBuffer;
                current.chunkBuffer(target.focalis$stream(), target.focalis$glBuffer(), target.focalis$vertexCount());
            } else {
                current.chunkBuffer(null, 0, 0);
            }
        } catch (RuntimeException | LinkageError e) {
            drawFailed(e);
        }
    }

    /** Right before vanilla calls one chunk's display list for a layer. */
    public static void chunkList(RenderChunk chunk, BlockRenderLayer layer) {
        TerrainDraws current = draws;
        if (current == null || !current.drawing()) {
            return;
        }
        try {
            int generation = chunk instanceof StreamDisplayLists
                    ? ((StreamDisplayLists) chunk).focalis$streamGeneration(layer.ordinal()) : 0;
            current.chunkList(generation);
        } catch (RuntimeException | LinkageError e) {
            drawFailed(e);
        }
    }

    /** The chunk renderer is done with the layer. */
    public static void containerFinished() {
        TerrainDraws current = draws;
        if (current == null) {
            return;
        }
        try {
            current.containerFinished();
        } catch (RuntimeException | LinkageError e) {
            drawFailed(e);
        }
    }

    // Lifecycle, client thread.

    /** The renderers reload with a world, which rebuilds every chunk and satisfies any reload the stream asked for. */
    public static void renderersReloading() {
        reloadRequested = false;
    }

    // Only at the start of a frame, outside any rendering, the way vanilla's own option changes reload.
    private static void serviceReload() {
        if (releaseRequested) {
            releaseRequested = false;
            if (plan == null) {
                releaseBuffers();
            }
        }
        if (!reloadRequested) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.world == null || mc.renderGlobal == null) {
            return;
        }
        reloadRequested = false;
        mc.renderGlobal.loadRenderers();
    }

    // Streams that were built can't be drawn once the plan is gone. The chunks themselves are left as they are.
    private static void releaseBuffers() {
        try {
            buffers.releaseAll();
        } catch (RuntimeException | LinkageError e) {
            failed(e);
        }
    }

    @Nullable
    private static TerrainStreamPlan usablePlan() {
        TerrainStreamPlan current = plan;
        if (current == null || failure.get() != null) {
            return null;
        }
        // A path the check turned down gets no streams. One that wasn't checked yet still does.
        return (OpenGlHelper.useVbo() ? bufferPath : listPath) == TURNED_DOWN ? null : current;
    }

    @Nullable
    private static StreamCompanion companion(BufferBuilder buffer) {
        return buffer instanceof StreamBufferBuilder ? ((StreamBufferBuilder) buffer).focalis$streamCompanion() : null;
    }

    @Nullable
    private static StreamCompanion activeCompanion(BufferBuilder buffer) {
        StreamCompanion companion = companion(buffer);
        return companion != null && companion.active() ? companion : null;
    }

    private static boolean isBlockFormat(@Nullable VertexFormat format) {
        return format == DefaultVertexFormats.BLOCK || DefaultVertexFormats.BLOCK.equals(format);
    }

    // Translucent data from before a plan only needs the reload, which is already on its way.
    private static void noteProblem(StreamCompanion companion) {
        String problem = companion.problem();
        if (problem != null && !problem.equals(StreamCompanion.NO_SAVED_IDS)
                && !problem.equals(StreamCompanion.OTHER_PLAN)) {
            note("Some chunk layers draw without the attribute stream because " + problem + ". This is only logged"
                    + " once for each reason.");
        }
    }

    private static void note(String message) {
        if (noted.add(message)) {
            notes.add(message);
        }
    }

    private static void failed(Throwable e) {
        if (failure.compareAndSet(null, e)) {
            note("The chunk attribute stream stopped after a Focalis error: " + e);
        }
    }

    private static void drawFailed(Throwable e) {
        failed(e);
        TerrainDraws current = draws;
        if (current != null) {
            try {
                current.fail();
            } catch (RuntimeException | LinkageError suppressed) {
                Throwable first = failure.get();
                if (first != null && first != suppressed) {
                    first.addSuppressed(suppressed);
                }
            }
        }
    }

    private static TerrainDraws draws() {
        TerrainDraws current = draws;
        if (current == null) {
            current = new TerrainDraws(LwjglAttributeGl.INSTANCE, new DrawStatus());
            draws = current;
        }
        return current;
    }

    @Nullable
    private static RenderDrawKind kind(BlockRenderLayer layer) {
        if (layer == BlockRenderLayer.SOLID) {
            return RenderDrawKind.TERRAIN_SOLID;
        }
        if (layer == BlockRenderLayer.CUTOUT_MIPPED) {
            return RenderDrawKind.TERRAIN_CUTOUT_MIPPED;
        }
        if (layer == BlockRenderLayer.CUTOUT) {
            return RenderDrawKind.TERRAIN_CUTOUT;
        }
        return layer == BlockRenderLayer.TRANSLUCENT ? RenderDrawKind.TERRAIN_TRANSLUCENT : null;
    }

    private static final class DrawStatus implements TerrainDraws.Status {

        @Override
        public boolean ready(RenderDrawKind layer) {
            if (layer != RenderDrawKind.TERRAIN_SOLID && layer != RenderDrawKind.TERRAIN_CUTOUT_MIPPED
                    && layer != RenderDrawKind.TERRAIN_CUTOUT && layer != RenderDrawKind.TERRAIN_TRANSLUCENT) {
                return false;
            }
            return generation() != 0;
        }

        @Override
        public int generation() {
            TerrainStreamPlan current = plan;
            if (current == null || failure.get() != null) {
                return 0;
            }
            return (OpenGlHelper.useVbo() ? bufferPath : listPath) == PASSED ? current.generation() : 0;
        }

        // Another mod draws terrain itself, or the hooks in vanilla's renderers didn't apply. Either way nothing
        // would use the streams, so they stop being built and the ones that exist go at the next reload.
        @Override
        public void drawnElsewhere(RenderDrawKind layer) {
            renderersReplaced = true;
            note("Terrain layer " + layer + " was drawn without the hooks Focalis puts in vanilla's chunk renderers,"
                    + " so it drew the normal way and the chunk attribute stream stays off for this session. Another"
                    + " mod probably draws terrain itself.");
            withdraw(true);
        }
    }
}
