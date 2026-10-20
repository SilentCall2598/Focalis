// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.github.silentcall2598.focalis.render.lifecycle.RenderHooks;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Slice;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Reports each entity renderer starting and returning. Every entity draw goes through renderEntity, also when a mod
// replaces the entity loop in RenderGlobal.renderEntities, and multipass draws go through renderMultipass. The return
// is also reported when the renderer throws, before whoever catches it draws on.
@Mixin(RenderManager.class)
public abstract class RenderManagerMixin {

    private static final String RENDER_ENTITY = "renderEntity(Lnet/minecraft/entity/Entity;DDDFFZ)V";
    // The first call inside the try that spans the rest of renderEntity.
    private static final String RENDERER_LOOKUP = "Lnet/minecraft/client/renderer/entity/RenderManager;"
            + "getEntityRenderObject(Lnet/minecraft/entity/Entity;)Lnet/minecraft/client/renderer/entity/Render;";

    // renderEntity isn't wrapped as a whole, since LagGoggles renames every RenderManager method with its descriptor
    // and a wrapper keeps the original body in a second one. So the object spans renderEntity's outer try instead,
    // whose catch every failure in it reaches before it is rethrown.
    @Inject(method = RENDER_ENTITY, at = @At(value = "INVOKE", target = RENDERER_LOOKUP, ordinal = 0), require = 0,
            expect = 1)
    private void focalis$entityStarted(Entity entity, double x, double y, double z, float yaw, float partialTicks,
            boolean debug, CallbackInfo ci) {
        RenderHooks.entityBodyStarted(entity);
    }

    @Inject(method = RENDER_ENTITY, at = @At("RETURN"),
            slice = @Slice(from = @At(value = "INVOKE", target = RENDERER_LOOKUP, ordinal = 0)), require = 0,
            expect = 1)
    private void focalis$entityReturned(CallbackInfo ci) {
        RenderHooks.entityBodyEnded();
    }

    // The outer catch starts by making its crash report, which runs mods' crash callables, so the end comes first.
    // Its description is the first "Rendering entity in world" after the hitbox's own catch.
    @Inject(method = RENDER_ENTITY, at = @At(value = "CONSTANT", args = "stringValue=Rendering entity in world",
            ordinal = 0), slice = @Slice(from = @At(value = "CONSTANT",
            args = "stringValue=Rendering entity hitbox in world")), require = 0, expect = 1)
    private void focalis$entityFailed(CallbackInfo ci) {
        RenderHooks.entityBodyEnded();
    }

    @WrapMethod(method = "renderMultipass(Lnet/minecraft/entity/Entity;F)V", require = 0, expect = 1)
    private void focalis$multipass(Entity entity, float partialTicks, Operation<Void> original) {
        boolean started = RenderHooks.entityStarted(entity);
        try {
            original.call(entity, partialTicks);
        } finally {
            if (started) {
                RenderHooks.entityReturned();
            }
        }
    }
}
