// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.mixin;

import io.github.silentcall2598.focalis.render.state.FragmentCache;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Lets Focalis read GlStateManager's cached blend factors, the same way as its fog.
@Mixin(targets = "net.minecraft.client.renderer.GlStateManager$BlendState")
public abstract class GlStateManagerBlendStateMixin implements FragmentCache.Blend {

    @Shadow
    public int srcFactor;
    @Shadow
    public int dstFactor;
    @Shadow
    public int srcFactorAlpha;
    @Shadow
    public int dstFactorAlpha;

    @Inject(method = "<init>()V", at = @At("RETURN"), require = 0)
    private void focalis$register(CallbackInfo info) {
        FragmentCache.register(this);
    }

    @Override
    public int focalis$factor(int index) {
        switch (index) {
            case 0:
                return srcFactor;
            case 1:
                return dstFactor;
            case 2:
                return srcFactorAlpha;
            default:
                return dstFactorAlpha;
        }
    }
}
