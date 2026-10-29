// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.mixin;

import io.github.silentcall2598.focalis.shader.terrain.LightingCapture;
import net.minecraft.client.renderer.BufferBuilder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BufferBuilder.class)
public abstract class BufferBuilderColorMixin {

    @Inject(method = "putColorMultiplier(FFFI)V", at = @At("HEAD"), require = 0, expect = 1)
    private void focalis$scaled(float red, float green, float blue, int vertexIndex, CallbackInfo ci) {
        LightingCapture.modelVertex((BufferBuilder) (Object) this, red, green, blue, vertexIndex);
    }

    @Inject(method = "color(FFFF)Lnet/minecraft/client/renderer/BufferBuilder;", at = @At("HEAD"), require = 0,
            expect = 1)
    private void focalis$colored(float red, float green, float blue, float alpha,
            CallbackInfoReturnable<BufferBuilder> cir) {
        LightingCapture.fluidVertex((BufferBuilder) (Object) this, red, green, blue, alpha);
    }
}
