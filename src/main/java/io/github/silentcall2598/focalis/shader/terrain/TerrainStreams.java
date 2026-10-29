// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.shader.terrain;

import io.github.silentcall2598.focalis.render.lifecycle.RenderDrawKind;
import io.github.silentcall2598.focalis.render.lifecycle.RenderLifecycle;
import io.github.silentcall2598.focalis.render.lifecycle.RenderPhase;
import io.github.silentcall2598.focalis.render.lifecycle.RenderStage;
import io.github.silentcall2598.focalis.shader.material.MaterialIds;
import io.github.silentcall2598.focalis.shader.pack.LegacyLighting;
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

public final class TerrainStreams {

    private static final String OWNER = "focalis:terrain_streams";
    private static final int UNCHECKED = 0;
    private static final int PASSED = 1;
    private static final int TURNED_DOWN = 2;

    @Nullable
    private static volatile TerrainStreamPlan plan;
    @Nullable
    private static MaterialIds planIds;
    private static int lastGeneration;
    private static boolean reloadRequested;
    private static boolean releaseRequested;
    @Nullable
    private static TerrainDraws draws;

    private static volatile int bufferPath = UNCHECKED;
    private static volatile int colorPath = UNCHECKED;
    private static volatile int listPath = UNCHECKED;
    private static boolean renderersReplaced;
    private static StreamBuffers buffers = new StreamBuffers(LwjglAttributeGl.INSTANCE);

    private static final AtomicReference<Throwable> failure = new AtomicReference<>();
    private static final Set<String> noted = ConcurrentHashMap.newKeySet();
    private static final Queue<String> notes = new ConcurrentLinkedQueue<>();

    private static final LongAdder layersStreamed = new LongAdder();
    private static final LongAdder layersWithout = new LongAdder();
    private static final LongAdder quadsWritten = new LongAdder();
    private static final LongAdder quadsCaptured = new LongAdder();
    private static final LongAdder quadsUncaptured = new LongAdder();
    private static final LongAdder quadsMismatched = new LongAdder();
    private static final LongAdder buildNanos = new LongAdder();
    private static long uploads;
    private static long uploadedBytes;
    private static long uploadNanos;
    private static long uploadsRefused;

    private TerrainStreams() {
    }

    public static void install(RenderLifecycle lifecycle) {
        lifecycle.register(RenderStage.FRAME, OWNER, (stage, phase, drawKind, partialTicks) -> {
            if (phase == RenderPhase.START) {
                serviceReload();
            }
        });
    }

    public static TerrainStreamGate gate() {
        return draws();
    }

    public static boolean publish(MaterialIds ids) {
        return publish(ids, LegacyLighting.UNSET);
    }

    public static boolean publish(MaterialIds ids, LegacyLighting lighting) {
        if (renderersReplaced || failure.get() != null || pathState(lighting.changesTerrainColors()) == TURNED_DOWN) {
            return false;
        }
        TerrainStreamPlan current = plan;
        if (current != null && planIds == ids && current.sameLighting(lighting)) {
            return false;
        }
        int generation = ++lastGeneration == 0 ? ++lastGeneration : lastGeneration;
        planIds = ids;
        plan = new TerrainStreamPlan(generation, ids::block, lighting);
        LightingCapture.enabled(plan.colors());
        reloadRequested = true;
        return true;
    }

    private static int pathState(boolean colors) {
        return OpenGlHelper.useVbo() ? (colors ? colorPath : bufferPath) : listPath;
    }

    public static void withdraw(boolean reload) {
        boolean was = plan != null;
        plan = null;
        planIds = null;
        LightingCapture.enabled(false);
        if (reload && was) {
            reloadRequested = true;
        }
        releaseRequested = true;
    }

    public static boolean active() {
        return plan != null;
    }

    @Nullable
    public static Throwable failure() {
        return failure.get();
    }

    public static void prepare(Logger logger) {
        String note;
        while ((note = notes.poll()) != null) {
            logger.info(note);
        }
        TerrainStreamPlan current = plan;
        if (current == null) {
            return;
        }
        boolean buffers = OpenGlHelper.useVbo();
        boolean colors = buffers && current.colors();
        if (pathState(current.colors()) != UNCHECKED) {
            return;
        }
        long start = System.nanoTime();
        StreamSelfTest.Result result = StreamSelfTest.run(buffers ? current.bufferLayout() : StreamLayout.LIST);
        boolean ok = result.outcome == StreamSelfTest.Outcome.PASSED;
        if (colors) {
            colorPath = ok ? PASSED : TURNED_DOWN;
        } else if (buffers) {
            bufferPath = ok ? PASSED : TURNED_DOWN;
        } else {
            listPath = ok ? PASSED : TURNED_DOWN;
        }
        String what = colors ? "vertex buffers with colors" : buffers ? "vertex buffers" : "display lists";
        String took = String.format(Locale.ROOT, "%.1f", (System.nanoTime() - start) / 1_000_000.0);
        if (ok) {
            logger.info("The chunk attribute stream works with {}, checked in {} ms: {}", what, took, result.detail);
        } else {
            logger.warn("The chunk attribute stream can't be used with {}, so terrain programs that read gl_Normal,"
                    + " mc_Entity, mc_midTexCoord or at_tangent{} draw the normal way: {}", what,
                    colors ? " or depend on the pack's lighting colors" : "", result);
            withdraw(true);
        }
    }

    public static String statistics() {
        return "layers with the stream " + layersStreamed.sum() + ", without " + layersWithout.sum() + ", quads "
                + quadsWritten.sum() + ", quads with captured colors " + quadsCaptured.sum() + ", with vanilla colors "
                + (quadsUncaptured.sum() + quadsMismatched.sum()) + " (" + quadsUncaptured.sum() + " uncaptured, "
                + quadsMismatched.sum() + " mismatched), build ms " + String.format(Locale.ROOT, "%.1f",
                buildNanos.sum() / 1_000_000.0) + ", uploads " + uploads + " with " + uploadedBytes + " bytes in "
                + String.format(Locale.ROOT, "%.1f", uploadNanos / 1_000_000.0) + " ms, refused " + uploadsRefused
                + ", live stream buffers " + buffers.live();
    }

    public static int liveBuffers() {
        return buffers.live();
    }

    // Chunk-build hooks may run on worker threads. Vanilla hands completed
    // companion data to the render thread for upload; GL work must stay there.
    public static void layerStarted(BufferBuilder buffer) {
        try {
            if (!(buffer instanceof StreamBufferBuilder)) {
                return;
            }
            StreamBufferBuilder holder = (StreamBufferBuilder) buffer;
            StreamCompanion companion = holder.focalis$streamCompanion();
            TerrainStreamPlan current = usablePlan();
            boolean vbo = OpenGlHelper.useVbo();
            if (current != null && current.colors() && !vbo) {
                // A display list bakes in the color array, so replaying one without the stream would show the pack's
                // colors. Lists get no colors and so no stream, and the programs step aside, see DrawStatus.
                note("The pack's lighting settings (oldLighting or separateAo) change the colors of chunk terrain, which"
                        + " Focalis only supplies with vertex buffers, so terrain draws without the pack's programs"
                        + " while display lists are in use.");
                current = null;
            }
            if (companion == null) {
                if (current == null) {
                    return;
                }
                companion = new StreamCompanion();
                holder.focalis$streamCompanion(companion);
            }
            companion.begin(current, !vbo ? StreamLayout.LIST : current == null ? StreamLayout.BUFFER
                    : current.bufferLayout());
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

    public static void layerFinished(BufferBuilder buffer) {
        try {
            StreamCompanion companion = companion(buffer);
            if (companion == null) {
                return;
            }
            if (!companion.active()) {
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
                noteColors(companion.finishedColors());
            } else if (companion.problem() != null) {
                layersWithout.increment();
                noteProblem(companion);
            }
        } catch (RuntimeException | LinkageError e) {
            failed(e);
        } finally {
            LightingCapture.bind(null);
        }
    }

    private static void noteColors(@Nullable VertexColors colors) {
        if (colors == null) {
            return;
        }
        quadsCaptured.add(colors.capturedQuads());
        int kept = colors.uncapturedQuads() + colors.mismatchedQuads();
        if (kept > 0) {
            quadsUncaptured.add(colors.uncapturedQuads());
            quadsMismatched.add(colors.mismatchedQuads());
            note("Some chunk quads keep the color vanilla built instead of the pack's lighting colors, because the"
                    + " renderer that made them isn't hooked, or because 3D anaglyph or a modded renderer changed their"
                    + " color. They keep their face shading and ambient occlusion in rgb, and the counts are in the"
                    + " stream statistics. This is only logged once.");
        }
    }

    static void captureFailed(Throwable e) {
        failed(e);
    }

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
        StreamLayout layout = current == null ? StreamLayout.BUFFER : current.bufferLayout();
        // A vertex buffer vanilla already deleted never gets a new stream buffer, so a late upload can't leak one.
        boolean usable = companion != null && current != null && target.focalis$glBuffer() >= 0
                && companion.generation() == current.generation()
                && companion.ready(layout, target.focalis$vertexCount());
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
        String problem = buffers.upload(stream, companion.stream(), companion.vertices(), companion.generation(),
                layout);
        if (problem != null) {
            uploadsRefused++;
            note("Some chunk layers draw without the attribute stream because " + problem + ". This is only logged"
                    + " once for each reason.");
            return;
        }
        uploads++;
        uploadedBytes += companion.vertices() * layout.stride();
        uploadNanos += System.nanoTime() - start;
    }

    public static void vertexDataReplaced(VertexBuffer vertexBuffer) {
        if (vertexBuffer instanceof StreamVertexBuffer) {
            BufferStream stream = ((StreamVertexBuffer) vertexBuffer).focalis$stream();
            if (stream != null) {
                stream.valid = false;
            }
        }
    }

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

    private static final AttributeState listUpload = new AttributeState();
    private static int listUploadBuffer;

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

    public static void containerStarting(BlockRenderLayer layer) {
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

    public static void renderersReloading() {
        reloadRequested = false;
    }

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
        return pathState(current.colors()) == TURNED_DOWN ? null : current;
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

    private static void noteProblem(StreamCompanion companion) {
        String problem = companion.problem();
        if (problem != null && !problem.equals(StreamCompanion.NO_SAVED_IDS)
                && !problem.equals(StreamCompanion.NO_SAVED_COLORS) && !problem.equals(StreamCompanion.OTHER_PLAN)) {
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
            if (current.colors() && !OpenGlHelper.useVbo()) {
                return 0;
            }
            return pathState(current.colors()) == PASSED ? current.generation() : 0;
        }

        @Override
        public boolean colors() {
            TerrainStreamPlan current = plan;
            return current != null && current.colors();
        }

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
