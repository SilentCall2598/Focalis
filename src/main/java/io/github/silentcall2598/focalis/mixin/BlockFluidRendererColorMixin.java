// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import io.github.silentcall2598.focalis.shader.terrain.LightingCapture;
import net.minecraft.client.renderer.BlockFluidRenderer;
import net.minecraft.client.renderer.BufferBuilder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(BlockFluidRenderer.class)
public abstract class BlockFluidRendererColorMixin {

    @ModifyExpressionValue(method = "renderFluid", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/"
            + "color/BlockColors;colorMultiplier(Lnet/minecraft/block/state/IBlockState;"
            + "Lnet/minecraft/world/IBlockAccess;Lnet/minecraft/util/math/BlockPos;I)I"), require = 0, expect = 1)
    private int focalis$tint(int multiplier, @Local(argsOnly = true) BufferBuilder buffer) {
        return LightingCapture.fluidTint(buffer, multiplier);
    }
}
