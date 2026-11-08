// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.mixin;

import io.github.silentcall2598.focalis.shader.terrain.LightingCapture;
import io.github.silentcall2598.focalis.shader.terrain.TerrainStreams;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.vertex.VertexFormat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BufferBuilder.class)
public abstract class BufferBuilderColorMixin {

    @Inject(method = "begin(ILnet/minecraft/client/renderer/vertex/VertexFormat;)V", at = @At("RETURN"), require = 0,
            expect = 1)
    private void focalis$begun(int mode, VertexFormat format, CallbackInfo ci) {
        TerrainStreams.objectBegun((BufferBuilder) (Object) this, mode, format);
    }

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
