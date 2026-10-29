// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.shader.terrain;

import io.github.silentcall2598.focalis.shader.pack.LegacyLighting;
import net.minecraft.block.state.IBlockState;
import org.lwjgl.opengl.GL11;

import javax.annotation.Nullable;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public final class StreamCompanion {

    static final String NO_SAVED_IDS = "the saved translucent data has no IDs";
    static final String OTHER_PLAN = "the saved translucent data has IDs of another plan";
    static final String NO_SAVED_COLORS = "the saved translucent data has no vertex colors";
    static final String NO_BLOCKS = "no block render was seen while a layer with quads was built";
    static final String NO_ACTUAL_STATES = "vanilla's actual block states weren't seen while a layer was built";

    private static final int QUAD_BYTES = 4 * StreamWriter.SOURCE_STRIDE;

    private final QuadIds ids = new QuadIds();
    private final SortCheck sort = new SortCheck();
    private final StreamWriter writer = new StreamWriter();

    @Nullable
    private TerrainStreamPlan plan;
    private int generation;
    private StreamLayout layout = StreamLayout.BUFFER;
    @Nullable
    private VertexColors colors;
    private boolean colorMode;
    @Nullable
    private String problem;
    private boolean observing;
    @Nullable
    private IBlockState observed;
    private int blocks;
    private int actualStates;
    private boolean resorted;

    @Nullable
    private ByteBuffer stream;
    private int vertices = -1;

    StreamCompanion() {
    }

    void begin(@Nullable TerrainStreamPlan buildPlan, StreamLayout streamLayout) {
        if (buildPlan != null && buildPlan.colors() != streamLayout.hasColors()) {
            throw new IllegalArgumentException("A plan " + (buildPlan.colors() ? "with" : "without")
                    + " colors can't build into the " + streamLayout + " layout");
        }
        plan = buildPlan;
        generation = buildPlan == null ? 0 : buildPlan.generation();
        layout = streamLayout;
        colorMode = buildPlan != null && buildPlan.colors();
        if (colorMode) {
            LegacyLighting lighting = buildPlan.lighting();
            if (colors == null) {
                colors = new VertexColors();
            }
            colors.begin(lighting.faceShading(), lighting.separateAo());
            LightingCapture.bind(colors);
        } else {
            colors = null;
            LightingCapture.bind(null);
        }
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

    private void release() {
        stream = null;
        ids.release();
        sort.release();
    }

    @Nullable
    VertexColors colors() {
        VertexColors held = colors;
        return held != null && colorMode && active() && held.capturing() ? held : null;
    }

    @Nullable
    VertexColors finishedColors() {
        return colorMode ? colors : null;
    }

    int capacity() {
        ByteBuffer held = stream;
        VertexColors heldColors = colors;
        return (held == null ? 0 : held.capacity()) + sort.capacity() + ids.capacity()
                + (heldColors == null ? 0 : heldColors.capacity());
    }

    boolean active() {
        return plan != null && problem == null;
    }

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
        if (colorMode) {
            colors.blockStarted();
            // A chunk build has a builder per layer, and begin() bound the one that started last. Vanilla's AO hook has no
            // builder to look the layer up by, so the thread's binding has to follow the block's own layer.
            LightingCapture.bind(colors);
        }
        observing = true;
        observed = null;
    }

    void observeActualState(IBlockState actual) {
        if (observing && observed == null) {
            observed = actual;
        }
    }

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
        if (colorMode) {
            // The captures are matched to the vertices as they stand now, before the sort moves any quad.
            colors.resolve(data, bufferVertices);
        }
        sort.prepare(data, bufferVertices / 4, QUAD_BYTES);
    }

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
        if (colorMode) {
            colors.permute(sort.order(), sort.quads());
        }
    }

    @Nullable
    SortedIds sortedIds() {
        if (!active() || colorMode && colors.capturing()) {
            return null;
        }
        return new SortedIds(ids.copy(), generation, colorMode ? colors.copy() : null);
    }

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
        if (colorMode) {
            int[] savedColors = saved.colors();
            if (savedColors == null) {
                fail(NO_SAVED_COLORS);
                return;
            }
            if (savedColors.length != bufferVertices) {
                fail("the saved translucent data doesn't have one color per vertex");
                return;
            }
            colors.load(savedColors);
        }
        ids.load(saved.ids());
        resorted = true;
    }

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
            int capacity = Math.max(bytes, target == null ? 0 : target.capacity() + (target.capacity() >> 1));
            target = ByteBuffer.allocateDirect(Math.max(capacity, 4096)).order(ByteOrder.nativeOrder());
            stream = target;
        }
        int[] pack = null;
        if (colorMode) {
            colors.resolve(data, bufferVertices);
            pack = colors.array();
        }
        writer.write(data, quads, ids.array(), pack, layout, target);
        vertices = bufferVertices;
    }

    boolean ready(StreamLayout uploadLayout, int uploadVertices) {
        return vertices > 0 && problem == null && layout == uploadLayout && vertices == uploadVertices;
    }

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
