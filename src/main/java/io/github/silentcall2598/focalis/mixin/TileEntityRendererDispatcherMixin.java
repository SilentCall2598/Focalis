// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.mixin;

import io.github.silentcall2598.focalis.render.lifecycle.RenderCheckpoint;
import io.github.silentcall2598.focalis.render.lifecycle.RenderHooks;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.tileentity.TileEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Reports each block entity renderer starting and returning. Every other render method here ends up in this one,
// which hands the block entity to its renderer.
@Mixin(TileEntityRendererDispatcher.class)
public abstract class TileEntityRendererDispatcherMixin {

    @Inject(method = "render(Lnet/minecraft/tileentity/TileEntity;DDDFIF)V", at = @At("HEAD"), require = 0)
    private void focalis$blockEntityStarted(TileEntity tileEntity, double x, double y, double z, float partialTicks,
            int destroyStage, float alpha, CallbackInfo ci) {
        RenderHooks.rendererStarted();
    }

    @Inject(method = "render(Lnet/minecraft/tileentity/TileEntity;DDDFIF)V", at = @At("RETURN"), require = 0)
    private void focalis$blockEntityReturned(TileEntity tileEntity, double x, double y, double z, float partialTicks,
            int destroyStage, float alpha, CallbackInfo ci) {
        RenderHooks.rendererReturned(RenderCheckpoint.BLOCK_ENTITY_RENDERED);
    }
}
