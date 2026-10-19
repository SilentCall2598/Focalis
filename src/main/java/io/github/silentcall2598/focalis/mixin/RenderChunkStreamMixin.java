// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.silentcall2598.focalis.shader.terrain.TerrainStreams;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.BlockRendererDispatcher;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.chunk.CompiledChunk;
import net.minecraft.client.renderer.chunk.RenderChunk;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Follows a chunk layer build for the attribute stream: where it starts, which blocks add which quads, vanilla's
// translucent sort and the data it saves for sorting again, and the finished layer. Each wrapper calls the original
// once with the same arguments and returns what it returned.
@Mixin(RenderChunk.class)
public abstract class RenderChunkStreamMixin {

    // Both full builds and translucent sorts start every layer here.
    @Inject(method = "preRenderBlocks(Lnet/minecraft/client/renderer/BufferBuilder;"
            + "Lnet/minecraft/util/math/BlockPos;)V", at = @At("HEAD"), require = 0, expect = 1)
    private void focalis$layerStarted(BufferBuilder buffer, BlockPos pos, CallbackInfo ci) {
        TerrainStreams.layerStarted(buffer);
    }

    // Only the chunk's own block loop. Renders a block's model starts inside it belong to that block.
    @WrapOperation(method = "rebuildChunk(FFFLnet/minecraft/client/renderer/chunk/ChunkCompileTaskGenerator;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/BlockRendererDispatcher;renderBlock("
                    + "Lnet/minecraft/block/state/IBlockState;Lnet/minecraft/util/math/BlockPos;"
                    + "Lnet/minecraft/world/IBlockAccess;Lnet/minecraft/client/renderer/BufferBuilder;)Z"),
            require = 0, expect = 1)
    private boolean focalis$block(BlockRendererDispatcher dispatcher, IBlockState state, BlockPos pos,
            IBlockAccess world, BufferBuilder buffer, Operation<Boolean> original) {
        TerrainStreams.blockStarted(buffer);
        boolean drawn = original.call(dispatcher, state, pos, world, buffer);
        TerrainStreams.blockEnded(buffer, state);
        return drawn;
    }

    @WrapOperation(method = "postRenderBlocks(Lnet/minecraft/util/BlockRenderLayer;FFFLnet/minecraft/client/renderer/"
            + "BufferBuilder;Lnet/minecraft/client/renderer/chunk/CompiledChunk;)V", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/BufferBuilder;sortVertexData(FFF)V"), require = 0, expect = 1)
    private void focalis$sort(BufferBuilder buffer, float x, float y, float z, Operation<Void> original) {
        TerrainStreams.sortStarting(buffer);
        original.call(buffer, x, y, z);
        TerrainStreams.sortFinished(buffer);
    }

    @WrapOperation(method = "postRenderBlocks(Lnet/minecraft/util/BlockRenderLayer;FFFLnet/minecraft/client/renderer/"
            + "BufferBuilder;Lnet/minecraft/client/renderer/chunk/CompiledChunk;)V", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/BufferBuilder;getVertexState()"
                    + "Lnet/minecraft/client/renderer/BufferBuilder$State;"), require = 0, expect = 1)
    private BufferBuilder.State focalis$stateSaved(BufferBuilder buffer, Operation<BufferBuilder.State> original) {
        BufferBuilder.State state = original.call(buffer);
        TerrainStreams.stateSaved(buffer, state);
        return state;
    }

    // After finishDrawing, so the stream sees exactly the data that gets uploaded.
    @Inject(method = "postRenderBlocks(Lnet/minecraft/util/BlockRenderLayer;FFFLnet/minecraft/client/renderer/"
            + "BufferBuilder;Lnet/minecraft/client/renderer/chunk/CompiledChunk;)V", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/BufferBuilder;finishDrawing()V", shift = At.Shift.AFTER),
            require = 0, expect = 1)
    private void focalis$layerFinished(BlockRenderLayer layer, float x, float y, float z, BufferBuilder buffer,
            CompiledChunk compiled, CallbackInfo ci) {
        TerrainStreams.layerFinished(buffer);
    }

    @WrapOperation(method = "resortTransparency(FFFLnet/minecraft/client/renderer/chunk/ChunkCompileTaskGenerator;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/BufferBuilder;setVertexState("
                    + "Lnet/minecraft/client/renderer/BufferBuilder$State;)V"), require = 0, expect = 1)
    private void focalis$stateRestored(BufferBuilder buffer, BufferBuilder.State state, Operation<Void> original) {
        original.call(buffer, state);
        TerrainStreams.stateRestored(buffer, state);
    }
}
