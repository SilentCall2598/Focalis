// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.mixin;

import io.github.silentcall2598.focalis.shader.terrain.StreamDisplayLists;
import net.minecraft.client.renderer.chunk.ListedRenderChunk;
import net.minecraft.util.BlockRenderLayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import javax.annotation.Nullable;

// Remembers which of a chunk's display lists were compiled with the stream, and under which plan generation.
@Mixin(ListedRenderChunk.class)
public abstract class ListedRenderChunkStreamMixin implements StreamDisplayLists {

    // Made on the first list with a stream, so chunks never streamed carry nothing.
    @Unique
    @Nullable
    private int[] focalis$generations;

    @Override
    public int focalis$streamGeneration(int layer) {
        int[] generations = focalis$generations;
        return generations == null || layer >= generations.length ? 0 : generations[layer];
    }

    @Override
    public void focalis$streamGeneration(int layer, int generation) {
        int[] generations = focalis$generations;
        if (generations == null) {
            if (generation == 0) {
                return;
            }
            generations = new int[BlockRenderLayer.values().length];
            focalis$generations = generations;
        }
        generations[layer] = generation;
    }
}
