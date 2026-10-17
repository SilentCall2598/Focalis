// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.silentcall2598.focalis.shader.terrain.TerrainStreams;
import net.minecraft.client.renderer.VboRenderList;
import net.minecraft.client.renderer.vertex.VertexBuffer;
import net.minecraft.util.BlockRenderLayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

// Vanilla's vertex buffer chunk renderer. Each chunk draw gets its stream or draws the vanilla way, and the whole layer
// puts back the array state the stream changed.
@Mixin(VboRenderList.class)
public abstract class VboRenderListStreamMixin {

    @WrapMethod(method = "renderChunkLayer(Lnet/minecraft/util/BlockRenderLayer;)V", require = 0, expect = 1)
    private void focalis$layer(BlockRenderLayer layer, Operation<Void> original) {
        TerrainStreams.containerStarting(layer);
        try {
            original.call(layer);
        } finally {
            TerrainStreams.containerFinished();
        }
    }

    // Right after vanilla bound the chunk's buffer and set its own pointers.
    @WrapOperation(method = "renderChunkLayer(Lnet/minecraft/util/BlockRenderLayer;)V", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/vertex/VertexBuffer;drawArrays(I)V"), require = 0, expect = 1)
    private void focalis$chunk(VertexBuffer buffer, int mode, Operation<Void> original) {
        TerrainStreams.chunkBuffer(buffer);
        original.call(buffer, mode);
    }
}
