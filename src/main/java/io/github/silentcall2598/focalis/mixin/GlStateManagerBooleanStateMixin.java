// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.mixin;

import io.github.silentcall2598.focalis.render.state.FixedFunctionFog;
import io.github.silentcall2598.focalis.render.state.FragmentCache;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Lets Focalis read whether GlStateManager has fog, the alpha test or blending on. Only GlStateManager's caches for
// those capabilities make one for GL_FOG, GL_ALPHA_TEST and GL_BLEND.
@Mixin(targets = "net.minecraft.client.renderer.GlStateManager$BooleanState")
public abstract class GlStateManagerBooleanStateMixin implements FixedFunctionFog.Capability {

    @Shadow
    private boolean currentState;

    @Inject(method = "<init>(I)V", at = @At("RETURN"), require = 0)
    private void focalis$register(int capability, CallbackInfo info) {
        if (capability == GL11.GL_FOG) {
            FixedFunctionFog.registerFogCapability(this);
        } else if (capability == GL11.GL_ALPHA_TEST) {
            FragmentCache.registerAlphaTest(this);
        } else if (capability == GL11.GL_BLEND) {
            FragmentCache.registerBlend(this);
        }
    }

    @Override
    public boolean focalis$enabled() {
        return currentState;
    }
}
