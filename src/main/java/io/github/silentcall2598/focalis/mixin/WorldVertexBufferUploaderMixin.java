// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.mixin;

import io.github.silentcall2598.focalis.shader.terrain.TerrainStreams;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.WorldVertexBufferUploader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// The feed has to come after Forge's preDraw, which sets the color pointer to vanilla's buffer, and its restore before
// postDraw turns the arrays off.
@Mixin(WorldVertexBufferUploader.class)
public abstract class WorldVertexBufferUploaderMixin {

    private static final String DRAW_ARRAYS = "Lnet/minecraft/client/renderer/GlStateManager;glDrawArrays(III)V";

    @Inject(method = "draw(Lnet/minecraft/client/renderer/BufferBuilder;)V", at = @At(value = "INVOKE",
            target = DRAW_ARRAYS), require = 0, expect = 1)
    private void focalis$drawing(BufferBuilder buffer, CallbackInfo ci) {
        TerrainStreams.objectDrawing(buffer);
    }

    @Inject(method = "draw(Lnet/minecraft/client/renderer/BufferBuilder;)V", at = @At(value = "INVOKE",
            target = DRAW_ARRAYS, shift = At.Shift.AFTER), require = 0, expect = 1)
    private void focalis$drawn(BufferBuilder buffer, CallbackInfo ci) {
        TerrainStreams.objectDrawn();
    }
}
