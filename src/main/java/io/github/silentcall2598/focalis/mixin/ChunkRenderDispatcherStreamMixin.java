// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import io.github.silentcall2598.focalis.shader.terrain.TerrainStreams;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.chunk.ChunkRenderDispatcher;
import net.minecraft.client.renderer.chunk.RenderChunk;
import net.minecraft.client.renderer.vertex.VertexBuffer;
import net.minecraft.util.BlockRenderLayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

// Uploads a layer's stream together with vanilla's data, on the client thread where vanilla uploads it. A queued upload
// from a worker comes back through uploadChunk on the client thread, so it passes here too.
@Mixin(ChunkRenderDispatcher.class)
public abstract class ChunkRenderDispatcherStreamMixin {

    @WrapOperation(method = "uploadChunk(Lnet/minecraft/util/BlockRenderLayer;Lnet/minecraft/client/renderer/"
            + "BufferBuilder;Lnet/minecraft/client/renderer/chunk/RenderChunk;Lnet/minecraft/client/renderer/chunk/"
            + "CompiledChunk;D)Lcom/google/common/util/concurrent/ListenableFuture;", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/chunk/ChunkRenderDispatcher;uploadVertexBuffer("
                    + "Lnet/minecraft/client/renderer/BufferBuilder;"
                    + "Lnet/minecraft/client/renderer/vertex/VertexBuffer;)V"),
            require = 0, expect = 1)
    private void focalis$bufferUpload(ChunkRenderDispatcher dispatcher, BufferBuilder buffer,
            VertexBuffer vertexBuffer, Operation<Void> original) {
        original.call(dispatcher, buffer, vertexBuffer);
        TerrainStreams.bufferUploaded(buffer, vertexBuffer);
    }

    // The arrays are only on while vanilla compiles the list, which copies what they point at into it.
    @WrapOperation(method = "uploadChunk(Lnet/minecraft/util/BlockRenderLayer;Lnet/minecraft/client/renderer/"
            + "BufferBuilder;Lnet/minecraft/client/renderer/chunk/RenderChunk;Lnet/minecraft/client/renderer/chunk/"
            + "CompiledChunk;D)Lcom/google/common/util/concurrent/ListenableFuture;", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/chunk/ChunkRenderDispatcher;uploadDisplayList("
                    + "Lnet/minecraft/client/renderer/BufferBuilder;I"
                    + "Lnet/minecraft/client/renderer/chunk/RenderChunk;)V"),
            require = 0, expect = 1)
    private void focalis$listUpload(ChunkRenderDispatcher dispatcher, BufferBuilder buffer, int list,
            RenderChunk chunk, Operation<Void> original, @Local(argsOnly = true) BlockRenderLayer layer) {
        boolean streamed = TerrainStreams.listUploadStarting(buffer);
        try {
            original.call(dispatcher, buffer, list, chunk);
        } finally {
            TerrainStreams.listUploadFinished(streamed, buffer, chunk, layer);
        }
    }
}
