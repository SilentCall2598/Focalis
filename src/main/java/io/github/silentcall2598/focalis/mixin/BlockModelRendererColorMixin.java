// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import io.github.silentcall2598.focalis.shader.terrain.LightingCapture;
import net.minecraft.client.renderer.BlockModelRenderer;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.block.model.BakedQuad;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BlockModelRenderer.class)
public abstract class BlockModelRendererColorMixin {

    private static final String ADD = "Lnet/minecraft/client/renderer/BufferBuilder;addVertexData([I)V";
    private static final String DIFFUSE = "Lnet/minecraftforge/client/model/pipeline/LightUtil;diffuseLight("
            + "Lnet/minecraft/util/EnumFacing;)F";
    private static final String TINT = "Lnet/minecraft/client/renderer/color/BlockColors;colorMultiplier("
            + "Lnet/minecraft/block/state/IBlockState;Lnet/minecraft/world/IBlockAccess;"
            + "Lnet/minecraft/util/math/BlockPos;I)I";

    @Inject(method = "renderQuadsSmooth", at = @At(value = "INVOKE", target = ADD), require = 0, expect = 1)
    private void focalis$smoothQuad(CallbackInfo ci, @Local(argsOnly = true) BufferBuilder buffer,
            @Local BakedQuad quad) {
        LightingCapture.modelQuad(buffer, quad, true);
    }

    @Inject(method = "renderQuadsFlat", at = @At(value = "INVOKE", target = ADD), require = 0, expect = 1)
    private void focalis$flatQuad(CallbackInfo ci, @Local(argsOnly = true) BufferBuilder buffer,
            @Local BakedQuad quad) {
        LightingCapture.modelQuad(buffer, quad, false);
    }

    @ModifyExpressionValue(method = "renderQuadsSmooth", at = @At(value = "INVOKE", target = DIFFUSE, remap = false),
            require = 0, expect = 1)
    private float focalis$smoothDiffuse(float diffuse, @Local(argsOnly = true) BufferBuilder buffer) {
        return LightingCapture.modelDiffuse(buffer, diffuse);
    }

    @ModifyExpressionValue(method = "renderQuadsFlat", at = @At(value = "INVOKE", target = DIFFUSE, remap = false),
            require = 0, expect = 2)
    private float focalis$flatDiffuse(float diffuse, @Local(argsOnly = true) BufferBuilder buffer) {
        return LightingCapture.modelDiffuse(buffer, diffuse);
    }

    @ModifyExpressionValue(method = {"renderQuadsSmooth", "renderQuadsFlat"}, at = @At(value = "INVOKE", target = TINT),
            require = 0, expect = 2)
    private int focalis$tint(int multiplier, @Local(argsOnly = true) BufferBuilder buffer) {
        return LightingCapture.modelTint(buffer, multiplier);
    }
}
