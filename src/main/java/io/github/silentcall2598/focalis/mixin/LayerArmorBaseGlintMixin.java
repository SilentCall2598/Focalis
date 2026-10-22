// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.mixin;

import io.github.silentcall2598.focalis.render.lifecycle.RenderDrawKind;
import io.github.silentcall2598.focalis.render.lifecycle.RenderHooks;
import net.minecraft.client.renderer.entity.layers.LayerArmorBase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Reports the armor glint. Vanilla's armor and elytra layers, and many mod armor layers, draw it through this shared
// method inside the renderer of the entity wearing the armor. Quark and HammerLib recolor it inside the method.
@Mixin(LayerArmorBase.class)
public abstract class LayerArmorBaseGlintMixin {

    private static final String GLINT = "renderEnchantedGlint(Lnet/minecraft/client/renderer/entity/RenderLivingBase;"
            + "Lnet/minecraft/entity/EntityLivingBase;Lnet/minecraft/client/model/ModelBase;FFFFFFF)V";

    @Inject(method = GLINT, at = @At("HEAD"), require = 0, expect = 1)
    private static void focalis$glintStarted(CallbackInfo ci) {
        RenderHooks.specialDrawStarted(RenderDrawKind.ARMOR_GLINT);
    }

    @Inject(method = GLINT, at = @At("RETURN"), require = 0, expect = 1)
    private static void focalis$glintEnded(CallbackInfo ci) {
        RenderHooks.specialDrawEnded(RenderDrawKind.ARMOR_GLINT);
    }
}
