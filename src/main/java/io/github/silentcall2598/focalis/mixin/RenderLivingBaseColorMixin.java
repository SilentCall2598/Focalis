// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.mixin;

import io.github.silentcall2598.focalis.render.lifecycle.RenderHooks;
import net.minecraft.client.renderer.entity.RenderLivingBase;
import net.minecraft.entity.EntityLivingBase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.nio.FloatBuffer;

// Reports the color vanilla blends a living entity with, red while hurt or the creeper's flash. setBrightness puts it in
// brightnessBuffer as the constant color of its texture blend and returns true when that blend is on.
@Mixin(RenderLivingBase.class)
public abstract class RenderLivingBaseColorMixin {

    @Shadow
    protected FloatBuffer brightnessBuffer;

    @Inject(method = "setBrightness(Lnet/minecraft/entity/EntityLivingBase;FZ)Z", at = @At("RETURN"), require = 0)
    private void focalis$colorSet(EntityLivingBase entity, float partialTicks, boolean combineTextures,
            CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ()) {
            FloatBuffer color = brightnessBuffer;
            RenderHooks.entityColor(color.get(0), color.get(1), color.get(2), color.get(3));
        }
    }

    @Inject(method = "unsetBrightness()V", at = @At("HEAD"), require = 0)
    private void focalis$colorCleared(CallbackInfo ci) {
        RenderHooks.entityColor(0f, 0f, 0f, 0f);
    }
}
