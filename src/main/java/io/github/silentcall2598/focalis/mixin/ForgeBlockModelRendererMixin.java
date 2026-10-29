// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.mixin;

import io.github.silentcall2598.focalis.shader.terrain.LighterLink;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraftforge.client.model.pipeline.ForgeBlockModelRenderer;
import net.minecraftforge.client.model.pipeline.VertexLighterFlat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = ForgeBlockModelRenderer.class, remap = false)
public abstract class ForgeBlockModelRendererMixin {

    private static final String RENDER = "render(Lnet/minecraftforge/client/model/pipeline/VertexLighterFlat;"
            + "Lnet/minecraft/world/IBlockAccess;Lnet/minecraft/client/renderer/block/model/IBakedModel;"
            + "Lnet/minecraft/block/state/IBlockState;Lnet/minecraft/util/math/BlockPos;"
            + "Lnet/minecraft/client/renderer/BufferBuilder;ZJ)Z";

    @Inject(method = RENDER, at = @At("HEAD"), require = 0, expect = 1)
    private static void focalis$linked(VertexLighterFlat lighter, IBlockAccess world, IBakedModel model,
            IBlockState state, BlockPos pos, BufferBuilder buffer, boolean checkSides, long rand,
            CallbackInfoReturnable<Boolean> cir) {
        ((LighterLink) lighter).focalis$builder(buffer);
    }

    @Inject(method = RENDER, at = @At("RETURN"), require = 0, expect = 1)
    private static void focalis$unlinked(VertexLighterFlat lighter, IBlockAccess world, IBakedModel model,
            IBlockState state, BlockPos pos, BufferBuilder buffer, boolean checkSides, long rand,
            CallbackInfoReturnable<Boolean> cir) {
        ((LighterLink) lighter).focalis$builder(null);
    }
}
