// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.shader.terrain;

import io.github.silentcall2598.focalis.shader.pack.LegacyLighting;
import net.minecraft.block.state.IBlockState;

import javax.annotation.Nullable;
import java.util.Objects;
import java.util.function.ToIntFunction;

public final class TerrainStreamPlan {

    private final int generation;
    private final ToIntFunction<IBlockState> blocks;
    @Nullable
    private final LegacyLighting lighting;

    TerrainStreamPlan(int generation, ToIntFunction<IBlockState> blocks) {
        this(generation, blocks, LegacyLighting.UNSET);
    }

    TerrainStreamPlan(int generation, ToIntFunction<IBlockState> blocks, LegacyLighting lighting) {
        if (generation == 0) {
            throw new IllegalArgumentException("Generation 0 stands for no stream");
        }
        this.generation = generation;
        this.blocks = Objects.requireNonNull(blocks, "blocks");
        this.lighting = lighting.changesTerrainColors() ? lighting : null;
    }

    public int generation() {
        return generation;
    }

    public boolean colors() {
        return lighting != null;
    }

    @Nullable
    LegacyLighting lighting() {
        return lighting;
    }

    boolean sameLighting(LegacyLighting other) {
        return Objects.equals(lighting, other.changesTerrainColors() ? other : null);
    }

    StreamLayout bufferLayout() {
        return lighting != null ? StreamLayout.BUFFER_COLOR : StreamLayout.BUFFER;
    }

    int block(IBlockState state) {
        return blocks.applyAsInt(state);
    }
}
