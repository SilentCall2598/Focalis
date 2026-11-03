// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.mixin;

import io.github.silentcall2598.focalis.shader.terrain.LightingCapture;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.client.renderer.BlockModelRenderer$AmbientOcclusionFace")
public abstract class AmbientOcclusionFaceMixin {

    @Shadow
    @Final
    private float[] vertexColorMultiplier;

    @Inject(method = "updateVertexBrightness", at = @At("TAIL"), require = 0, expect = 1)
    private void focalis$occlusion(CallbackInfo ci) {
        LightingCapture.modelAo(vertexColorMultiplier);
    }
}
