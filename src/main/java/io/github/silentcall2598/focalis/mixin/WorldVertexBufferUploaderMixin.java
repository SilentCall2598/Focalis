// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import io.github.silentcall2598.focalis.shader.terrain.TerrainStreams;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.WorldVertexBufferUploader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

// The feed has to come after Forge's preDraw, which sets the color pointer to vanilla's buffer, and its restore before
// postDraw turns the arrays off.
@Mixin(WorldVertexBufferUploader.class)
public abstract class WorldVertexBufferUploaderMixin {

    // A WrapOperation boxes the call's arguments into a new array on every Tessellator draw, 32 bytes a draw on Java
    // 8u51 and 8u504 in the 1G.1a allocation harness. So a fed draw makes the call itself, inside the finally that
    // restores the feed, and skips any hook another mod has inside this one for those draws only.
    @WrapWithCondition(method = "draw(Lnet/minecraft/client/renderer/BufferBuilder;)V", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/GlStateManager;glDrawArrays(III)V"), require = 0, expect = 1)
    private boolean focalis$draw(int mode, int first, int count, BufferBuilder buffer) {
        if (!TerrainStreams.objectDrawing(buffer)) {
            return true;
        }
        try {
            GlStateManager.glDrawArrays(mode, first, count);
        } finally {
            TerrainStreams.objectDrawn();
        }
        return false;
    }
}
