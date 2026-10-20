// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.github.silentcall2598.focalis.render.lifecycle.RenderHooks;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;

// Reports each entity renderer starting and returning. Every entity draw goes through renderEntity, also when a mod
// replaces the entity loop in RenderGlobal.renderEntities, and multipass draws go through renderMultipass. The return
// is also reported when the renderer throws, before whoever catches it draws on.
@Mixin(RenderManager.class)
public abstract class RenderManagerMixin {

    @WrapMethod(method = "renderEntity(Lnet/minecraft/entity/Entity;DDDFFZ)V", require = 0, expect = 1)
    private void focalis$entity(Entity entity, double x, double y, double z, float yaw, float partialTicks,
            boolean debug, Operation<Void> original) {
        boolean started = RenderHooks.entityStarted(entity);
        try {
            original.call(entity, x, y, z, yaw, partialTicks, debug);
        } finally {
            if (started) {
                RenderHooks.entityReturned();
            }
        }
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
