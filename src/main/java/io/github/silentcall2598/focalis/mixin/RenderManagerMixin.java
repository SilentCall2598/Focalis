// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.mixin;

import io.github.silentcall2598.focalis.render.lifecycle.RenderCheckpoint;
import io.github.silentcall2598.focalis.render.lifecycle.RenderHooks;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Reports each entity renderer starting and returning. Every entity draw goes through renderEntity, also when a mod
// replaces the entity loop in RenderGlobal.renderEntities, and multipass draws go through renderMultipass.
@Mixin(RenderManager.class)
public abstract class RenderManagerMixin {

    @Inject(method = "renderEntity(Lnet/minecraft/entity/Entity;DDDFFZ)V", at = @At("HEAD"), require = 0)
    private void focalis$entityStarted(Entity entity, double x, double y, double z, float yaw, float partialTicks,
            boolean debug, CallbackInfo ci) {
        RenderHooks.rendererStarted();
    }

    @Inject(method = "renderEntity(Lnet/minecraft/entity/Entity;DDDFFZ)V", at = @At("RETURN"), require = 0)
    private void focalis$entityReturned(Entity entity, double x, double y, double z, float yaw, float partialTicks,
            boolean debug, CallbackInfo ci) {
        RenderHooks.rendererReturned(RenderCheckpoint.ENTITY_RENDERED);
    }

    @Inject(method = "renderMultipass(Lnet/minecraft/entity/Entity;F)V", at = @At("HEAD"), require = 0)
    private void focalis$multipassStarted(Entity entity, float partialTicks, CallbackInfo ci) {
        RenderHooks.rendererStarted();
    }

    @Inject(method = "renderMultipass(Lnet/minecraft/entity/Entity;F)V", at = @At("RETURN"), require = 0)
    private void focalis$multipassReturned(Entity entity, float partialTicks, CallbackInfo ci) {
        RenderHooks.rendererReturned(RenderCheckpoint.ENTITY_RENDERED);
    }
}
