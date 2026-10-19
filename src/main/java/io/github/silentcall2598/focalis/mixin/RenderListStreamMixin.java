// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.silentcall2598.focalis.shader.terrain.TerrainStreams;
import net.minecraft.client.renderer.RenderList;
import net.minecraft.client.renderer.chunk.CompiledChunk;
import net.minecraft.client.renderer.chunk.ListedRenderChunk;
import net.minecraft.util.BlockRenderLayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

// Vanilla's display list chunk renderer. Lists compiled with the stream carry it themselves, so each chunk only decides
// whether the program draws it, and the layer puts back the current values the lists leave behind.
@Mixin(RenderList.class)
public abstract class RenderListStreamMixin {

    @WrapMethod(method = "renderChunkLayer(Lnet/minecraft/util/BlockRenderLayer;)V", require = 0, expect = 1)
    private void focalis$layer(BlockRenderLayer layer, Operation<Void> original) {
        TerrainStreams.containerStarting(layer);
        try {
            original.call(layer);
        } finally {
            TerrainStreams.containerFinished();
        }
    }

    // The list lookup sits right in the callList argument, so nothing draws between the two.
    @WrapOperation(method = "renderChunkLayer(Lnet/minecraft/util/BlockRenderLayer;)V", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/chunk/ListedRenderChunk;getDisplayList("
                    + "Lnet/minecraft/util/BlockRenderLayer;Lnet/minecraft/client/renderer/chunk/CompiledChunk;)I"),
            require = 0, expect = 1)
    private int focalis$chunk(ListedRenderChunk chunk, BlockRenderLayer layer, CompiledChunk compiled,
            Operation<Integer> original) {
        int list = original.call(chunk, layer, compiled);
        TerrainStreams.chunkList(chunk, layer);
        return list;
    }
}
