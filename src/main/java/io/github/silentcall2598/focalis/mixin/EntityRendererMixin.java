// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.mixin;

import io.github.silentcall2598.focalis.render.lifecycle.RenderHooks;
import net.minecraft.client.renderer.EntityRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

// Only reports where Minecraft is. RenderHooks and the render lifecycle decide what happens there.
@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin {

    // ModifyVariable instead of Inject so no CallbackInfo is allocated every world pass. The value goes back unchanged.
    @ModifyVariable(method = "renderWorldPass(IFJ)V", at = @At("HEAD"), argsOnly = true)
    private float focalis$worldPassStart(float partialTicks) {
        RenderHooks.worldPassStart(partialTicks);
        return partialTicks;
    }
}
