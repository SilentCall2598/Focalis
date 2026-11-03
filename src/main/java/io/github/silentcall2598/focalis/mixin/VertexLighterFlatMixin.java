// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import io.github.silentcall2598.focalis.shader.terrain.LighterLink;
import io.github.silentcall2598.focalis.shader.terrain.LightingCapture;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraftforge.client.model.pipeline.VertexLighterFlat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import javax.annotation.Nullable;

@Mixin(value = VertexLighterFlat.class, remap = false)
public abstract class VertexLighterFlatMixin implements LighterLink {

    @Unique
    @Nullable
    private BufferBuilder focalis$builder;

    @Nullable
    @Override
    public BufferBuilder focalis$builder() {
        return focalis$builder;
    }

    @Override
    public void focalis$builder(@Nullable BufferBuilder builder) {
        focalis$builder = builder;
    }

    @Inject(method = "updateColor", at = @At("HEAD"), require = 0, expect = 1)
    private void focalis$quadColor(float[] normal, float[] color, float x, float y, float z, float tint,
            int multiplier, CallbackInfo ci) {
        BufferBuilder builder = focalis$builder;
        if (builder != null) {
            LightingCapture.forgeBase(builder, color, tint, multiplier);
        }
    }

    @ModifyExpressionValue(method = "processQuad", at = @At(value = "INVOKE", target = "Lnet/minecraftforge/client/"
            + "model/pipeline/LightUtil;diffuseLight(FFF)F"), require = 0, expect = 1)
    private float focalis$diffuse(float diffuse) {
        BufferBuilder builder = focalis$builder;
        if (builder != null) {
            LightingCapture.forgeDiffuse(builder, diffuse);
        }
        return diffuse;
    }
}
