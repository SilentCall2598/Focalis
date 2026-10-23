// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.mixin;

import io.github.silentcall2598.focalis.render.lifecycle.RenderHooks;
import net.minecraft.client.renderer.entity.RenderLivingBase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// doRender catches every failure of the model and its layers, logs it and draws on with the shadow, fire and name. A
// glint or eye layer that threw never returned, so its special draw ends here, at the catch's only log call.
@Mixin(RenderLivingBase.class)
public abstract class RenderLivingBaseFailureMixin {

    @Inject(method = "doRender(Lnet/minecraft/entity/EntityLivingBase;DDDFF)V", at = @At(value = "INVOKE",
            target = "Lorg/apache/logging/log4j/Logger;error(Ljava/lang/String;Ljava/lang/Throwable;)V",
            remap = false), require = 0, expect = 1)
    private void focalis$failed(CallbackInfo ci) {
        RenderHooks.specialDrawsAbandoned();
    }
}
