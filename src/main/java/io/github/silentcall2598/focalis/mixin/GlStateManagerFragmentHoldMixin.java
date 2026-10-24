// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.mixin;

import io.github.silentcall2598.focalis.render.state.FragmentHold;
import net.minecraft.client.renderer.GlStateManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Lets a program's alpha test and blend directives stay in effect while vanilla code that sets that state itself
// draws with the program, see FragmentHold. A held call returns before it touches the cache or GL. The overloads
// taking GlStateManager's factor enums call these ones, so they are held too. Outside a hold each call only checks a
// field.
@Mixin(GlStateManager.class)
public abstract class GlStateManagerFragmentHoldMixin {

    @Inject(method = "enableAlpha()V", at = @At("HEAD"), cancellable = true, require = 0, expect = 1)
    private static void focalis$enableAlpha(CallbackInfo info) {
        if (FragmentHold.alphaTest(true)) {
            info.cancel();
        }
    }

    @Inject(method = "disableAlpha()V", at = @At("HEAD"), cancellable = true, require = 0, expect = 1)
    private static void focalis$disableAlpha(CallbackInfo info) {
        if (FragmentHold.alphaTest(false)) {
            info.cancel();
        }
    }

    @Inject(method = "alphaFunc(IF)V", at = @At("HEAD"), cancellable = true, require = 0, expect = 1)
    private static void focalis$alphaFunc(int function, float reference, CallbackInfo info) {
        if (FragmentHold.alphaFunction(function, reference)) {
            info.cancel();
        }
    }

    @Inject(method = "enableBlend()V", at = @At("HEAD"), cancellable = true, require = 0, expect = 1)
    private static void focalis$enableBlend(CallbackInfo info) {
        if (FragmentHold.blend(true)) {
            info.cancel();
        }
    }

    @Inject(method = "disableBlend()V", at = @At("HEAD"), cancellable = true, require = 0, expect = 1)
    private static void focalis$disableBlend(CallbackInfo info) {
        if (FragmentHold.blend(false)) {
            info.cancel();
        }
    }

    @Inject(method = "blendFunc(II)V", at = @At("HEAD"), cancellable = true, require = 0, expect = 1)
    private static void focalis$blendFunc(int source, int destination, CallbackInfo info) {
        if (FragmentHold.blendFactors(source, destination, source, destination)) {
            info.cancel();
        }
    }

    @Inject(method = "tryBlendFuncSeparate(IIII)V", at = @At("HEAD"), cancellable = true, require = 0, expect = 1)
    private static void focalis$blendFuncSeparate(int sourceColor, int destinationColor, int sourceAlpha,
            int destinationAlpha, CallbackInfo info) {
        if (FragmentHold.blendFactors(sourceColor, destinationColor, sourceAlpha, destinationAlpha)) {
            info.cancel();
        }
    }
}
