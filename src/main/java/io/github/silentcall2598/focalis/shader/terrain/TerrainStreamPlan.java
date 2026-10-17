// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.shader.terrain;

import net.minecraft.block.state.IBlockState;

import java.util.Objects;
import java.util.function.ToIntFunction;

/**
 * What chunk builds put in the stream while it's on: the material ID of every block state, from the pack
 * configuration in use. Every plan gets a new generation, and a stream built under another generation is never drawn.
 * Immutable, so worker threads can read it once it's published.
 */
public final class TerrainStreamPlan {

    private final int generation;
    private final ToIntFunction<IBlockState> blocks;

    /**
     * @param blocks the material ID of a block state, or -1 when it has none. Called from chunk build threads.
     */
    TerrainStreamPlan(int generation, ToIntFunction<IBlockState> blocks) {
        if (generation == 0) {
            throw new IllegalArgumentException("Generation 0 stands for no stream");
        }
        this.generation = generation;
        this.blocks = Objects.requireNonNull(blocks, "blocks");
    }

    /** Never 0, which stands for no stream. */
    public int generation() {
        return generation;
    }

    int block(IBlockState state) {
        return blocks.applyAsInt(state);
    }
}
