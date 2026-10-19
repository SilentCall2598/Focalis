// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import io.github.silentcall2598.focalis.shader.terrain.TerrainStreams;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.BlockRendererDispatcher;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

// The actual state vanilla renders a block with, which the stream's material ID comes from. If getActualState throws,
// vanilla keeps the stored state, and so does the stream since nothing gets observed.
@Mixin(BlockRendererDispatcher.class)
public abstract class BlockRendererDispatcherStreamMixin {

    @WrapOperation(method = "renderBlock(Lnet/minecraft/block/state/IBlockState;Lnet/minecraft/util/math/BlockPos;"
            + "Lnet/minecraft/world/IBlockAccess;Lnet/minecraft/client/renderer/BufferBuilder;)Z",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/block/state/IBlockState;getActualState("
                    + "Lnet/minecraft/world/IBlockAccess;Lnet/minecraft/util/math/BlockPos;)"
                    + "Lnet/minecraft/block/state/IBlockState;"), require = 0, expect = 1)
    private IBlockState focalis$actualState(IBlockState state, IBlockAccess world, BlockPos pos,
            Operation<IBlockState> original, @Local(argsOnly = true) BufferBuilder buffer) {
        IBlockState actual = original.call(state, world, pos);
        TerrainStreams.actualState(buffer, actual);
        return actual;
    }
}
