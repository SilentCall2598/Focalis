// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.mixin;

import io.github.silentcall2598.focalis.render.state.FragmentCache;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Lets Focalis read GlStateManager's cached alpha function, the same way as its fog.
@Mixin(targets = "net.minecraft.client.renderer.GlStateManager$AlphaState")
public abstract class GlStateManagerAlphaStateMixin implements FragmentCache.Alpha {

    @Shadow
    public int func;
    @Shadow
    public float ref;

    @Inject(method = "<init>()V", at = @At("RETURN"), require = 0)
    private void focalis$register(CallbackInfo info) {
        FragmentCache.register(this);
    }

    @Override
    public int focalis$function() {
        return func;
    }

    @Override
    public float focalis$reference() {
        return ref;
    }
}
