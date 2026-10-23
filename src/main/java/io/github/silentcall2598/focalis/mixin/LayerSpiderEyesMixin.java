// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.mixin;

import io.github.silentcall2598.focalis.render.lifecycle.RenderDrawKind;
import io.github.silentcall2598.focalis.render.lifecycle.RenderHooks;
import net.minecraft.client.renderer.entity.layers.LayerSpiderEyes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Reports the spider's eye layer. The typed method, not the bridge the layer list calls, so a direct call counts too.
@Mixin(LayerSpiderEyes.class)
public abstract class LayerSpiderEyesMixin {

    private static final String LAYER = "doRenderLayer(Lnet/minecraft/entity/monster/EntitySpider;FFFFFFF)V";

    @Inject(method = LAYER, at = @At("HEAD"), require = 0, expect = 1)
    private void focalis$eyesStarted(CallbackInfo ci) {
        RenderHooks.specialDrawStarted(RenderDrawKind.EYES);
    }

    @Inject(method = LAYER, at = @At("RETURN"), require = 0, expect = 1)
    private void focalis$eyesEnded(CallbackInfo ci) {
        RenderHooks.specialDrawEnded(RenderDrawKind.EYES);
    }
}
