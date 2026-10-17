// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.shader.terrain;

import net.minecraft.block.state.IBlockState;
import org.lwjgl.opengl.GL11;

import javax.annotation.Nullable;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * The stream side of one BufferBuilder that builds chunk layers. It follows a layer build, keeps the material ID of
 * every quad through the translucent sort, and writes the stream once vanilla finished the layer. The builder's thread
 * uses it, and the client thread reads it for the upload after vanilla hands it over. Not thread safe otherwise.
 *
 * <p>A layer only gets a stream when everything that feeds it was seen. A hook that missed shows up as quads with no
 * block render or no actual state, and that layer gets no stream.
 */
public final class StreamCompanion {

    // Translucent data saved before the stream started, or under another plan, until the reload builds it again.
    static final String NO_SAVED_IDS = "the saved translucent data has no IDs";
    static final String OTHER_PLAN = "the saved translucent data has IDs of another plan";
    static final String NO_BLOCKS = "no block render was seen while a layer with quads was built";
    static final String NO_ACTUAL_STATES = "vanilla's actual block states weren't seen while a layer was built";

    private static final int QUAD_BYTES = 4 * StreamWriter.SOURCE_STRIDE;

    private final QuadIds ids = new QuadIds();
    private final SortCheck sort = new SortCheck();
    private final StreamWriter writer = new StreamWriter();

    // The plan of the build in progress, or null when this build has no stream.
    @Nullable
    private TerrainStreamPlan plan;
    private int generation;
    private StreamLayout layout = StreamLayout.BUFFER;
    @Nullable
    private String problem;
    private boolean observing;
    @Nullable
    private IBlockState observed;
    // Block renders this build saw, and how many of them had vanilla's actual state observed.
    private int blocks;
    private int actualStates;
    // Whether the IDs came from saved sorted data, which sorting again does without any block render.
    private boolean resorted;

    @Nullable
    private ByteBuffer stream;
    // Vertices in the finished stream, or -1 when there is none.
    private int vertices = -1;

    StreamCompanion() {
    }

    /** A layer build starts. Without a plan it gets no stream. */
    void begin(@Nullable TerrainStreamPlan buildPlan, StreamLayout streamLayout) {
        plan = buildPlan;
        generation = buildPlan == null ? 0 : buildPlan.generation();
        layout = streamLayout;
        problem = null;
        observing = false;
        observed = null;
        blocks = 0;
        actualStates = 0;
        resorted = false;
        vertices = -1;
        ids.reset();
        sort.abandon();
        if (buildPlan == null) {
            release();
        }
    }

    // Nothing is built with a stream for now, so the builder doesn't keep what the biggest layer it ever built took.
    private void release() {
        stream = null;
        ids.release();
        sort.release();
    }

    /** The bytes this builder holds on to for its streams, which grow to the biggest layer it built. */
    int capacity() {
        ByteBuffer held = stream;
        return (held == null ? 0 : held.capacity()) + sort.capacity() + ids.capacity();
    }

    /** Whether this build is still on its way to a stream. */
    boolean active() {
        return plan != null && problem == null;
    }

    /** Why the last build has no stream, or null. */
    @Nullable
    String problem() {
        return problem;
    }

    void blockStarted(int bufferVertices) {
        if (!active()) {
            return;
        }
        blocks++;
        ids.blockStarted(bufferVertices);
        observing = true;
        observed = null;
    }

    /** The first state vanilla's dispatcher works out for the block being rendered, before any nested render's. */
    void observeActualState(IBlockState actual) {
        if (observing && observed == null) {
            observed = actual;
        }
    }

    /**
     * The block render ended. Its quads get the ID of the actual state vanilla rendered it with, or of the stored
     * state when vanilla doesn't work one out, like when getActualState throws.
     */
    void blockEnded(int bufferVertices, IBlockState stored) {
        if (!observing) {
            return;
        }
        IBlockState state = observed != null ? observed : stored;
        if (observed != null) {
            actualStates++;
        }
        observing = false;
        observed = null;
        TerrainStreamPlan current = plan;
        if (current == null || problem != null) {
            return;
        }
        ids.blockEnded(bufferVertices, StreamLayout.entityId(current.block(state)));
        if (ids.broken()) {
            fail("a block render added part of a quad");
        }
    }

    /** Vanilla is about to sort the translucent layer. */
    void sortStarting(ByteBuffer data, int bufferVertices, boolean blockFormat) {
        if (!active()) {
            return;
        }
        if (!blockFormat) {
            fail("the layer isn't in vanilla's block vertex format");
            return;
        }
        if (!ids.finish(bufferVertices)) {
            fail("the layer doesn't end on a quad boundary");
            return;
        }
        sort.prepare(data, bufferVertices / 4, QUAD_BYTES);
    }

    /** The index array of vanilla's sort, right after it was sorted. */
    void sortObserved(Object[] sorted) {
        if (active()) {
            sort.observed(sorted);
        }
    }

    void sortFinished(ByteBuffer data) {
        if (!active()) {
            sort.abandon();
            return;
        }
        String why = sort.verify(data);
        if (why != null) {
            fail(why);
            return;
        }
        ids.permute(sort.order(), sort.quads());
    }

    /** The IDs to attach to the sorted data vanilla saves, or null when this build has no stream. */
    @Nullable
    SortedIds sortedIds() {
        return active() ? new SortedIds(ids.copy(), generation) : null;
    }

    /** Vanilla put back saved sorted data to sort it again. Its IDs come along if it has them. */
    void restoreSorted(@Nullable SortedIds saved, int bufferVertices) {
        if (!active()) {
            return;
        }
        if (saved == null) {
            fail(NO_SAVED_IDS);
            return;
        }
        if (saved.generation() != generation) {
            fail(OTHER_PLAN);
            return;
        }
        if (saved.ids().length * 4 != bufferVertices) {
            fail("the saved translucent data doesn't have one ID per quad");
            return;
        }
        ids.load(saved.ids());
        resorted = true;
    }

    /**
     * Vanilla finished the layer. Writes the stream for exactly the data it's about to upload. A layer without
     * vertices is never drawn and gets none.
     */
    void finish(ByteBuffer data, int bufferVertices, boolean blockFormat, int drawMode) {
        if (!active() || bufferVertices == 0) {
            return;
        }
        if (!blockFormat) {
            fail("the layer isn't in vanilla's block vertex format");
            return;
        }
        if (drawMode != GL11.GL_QUADS) {
            fail("the layer isn't drawn as quads");
            return;
        }
        if (!resorted) {
            if (blocks == 0) {
                fail(NO_BLOCKS);
                return;
            }
            if (actualStates == 0) {
                fail(NO_ACTUAL_STATES);
                return;
            }
        }
        if (!ids.finish(bufferVertices)) {
            fail("the layer doesn't end on a quad boundary");
            return;
        }
        int quads = bufferVertices / 4;
        int bytes = bufferVertices * layout.stride();
        ByteBuffer target = stream;
        if (target == null || target.capacity() < bytes) {
            // Grows with headroom, like vanilla's builders, so a slightly bigger chunk doesn't allocate again.
            int capacity = Math.max(bytes, target == null ? 0 : target.capacity() + (target.capacity() >> 1));
            target = ByteBuffer.allocateDirect(Math.max(capacity, 4096)).order(ByteOrder.nativeOrder());
            stream = target;
        }
        writer.write(data, quads, ids.array(), layout, target);
        vertices = bufferVertices;
    }

    /** Whether a finished stream in this layout with exactly this many vertices is ready. */
    boolean ready(StreamLayout uploadLayout, int uploadVertices) {
        return vertices > 0 && problem == null && layout == uploadLayout && vertices == uploadVertices;
    }

    /** The finished stream, starting at 0. Its position and limit mean nothing. */
    ByteBuffer stream() {
        ByteBuffer finished = stream;
        if (finished == null || vertices < 0) {
            throw new IllegalStateException("No stream was finished");
        }
        return finished;
    }

    int vertices() {
        return vertices;
    }

    int generation() {
        return generation;
    }

    StreamLayout layout() {
        return layout;
    }

    private void fail(String why) {
        if (problem == null) {
            problem = why;
        }
        vertices = -1;
        sort.abandon();
    }
}
