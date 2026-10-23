// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.mixin;

import io.github.silentcall2598.focalis.render.lifecycle.RenderDrawKind;
import io.github.silentcall2598.focalis.render.lifecycle.RenderHooks;
import net.minecraft.client.renderer.RenderItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Reports the glint over an item, which renderItem draws for every item with an effect, in the world, the hand and
// screens alike. Only the world pass's entity stages act on it.
@Mixin(RenderItem.class)
public abstract class RenderItemGlintMixin {

    private static final String EFFECT = "renderEffect(Lnet/minecraft/client/renderer/block/model/IBakedModel;)V";

    @Inject(method = EFFECT, at = @At("HEAD"), require = 0, expect = 1)
    private void focalis$glintStarted(CallbackInfo ci) {
        RenderHooks.specialDrawStarted(RenderDrawKind.ITEM_GLINT);
    }

    @Inject(method = EFFECT, at = @At("RETURN"), require = 0, expect = 1)
    private void focalis$glintEnded(CallbackInfo ci) {
        RenderHooks.specialDrawEnded(RenderDrawKind.ITEM_GLINT);
    }
}
