// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import io.github.silentcall2598.focalis.shader.terrain.LighterLink;
import io.github.silentcall2598.focalis.shader.terrain.LightingCapture;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraftforge.client.model.pipeline.VertexLighterSmoothAo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = VertexLighterSmoothAo.class, remap = false)
public abstract class VertexLighterSmoothAoMixin {

    @ModifyExpressionValue(method = "updateColor", at = @At(value = "INVOKE", target = "Lnet/minecraftforge/client/"
            + "model/pipeline/VertexLighterSmoothAo;getAo(FFF)F"), require = 0, expect = 1)
    private float focalis$occlusion(float ao) {
        BufferBuilder builder = LighterLink.builder(this);
        if (builder != null) {
            LightingCapture.forgeAo(builder, ao);
        }
        return ao;
    }
}
