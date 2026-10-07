// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.mixin;

import io.github.silentcall2598.focalis.render.state.FixedFunctionFog;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Lets Focalis read GlStateManager's fog cache. The class is private to GlStateManager, so it hands itself over once
// when it's made instead of being looked up later.
@Mixin(targets = "net.minecraft.client.renderer.GlStateManager$FogState")
public abstract class GlStateManagerFogStateMixin implements FixedFunctionFog.Cache {

    @Shadow
    public int mode;
    @Shadow
    public float density;
    @Shadow
    public float start;
    @Shadow
    public float end;

    @Inject(method = "<init>()V", at = @At("RETURN"), require = 0)
    private void focalis$register(CallbackInfo info) {
        FixedFunctionFog.register(this);
    }

    @Override
    public int focalis$mode() {
        return mode;
    }

    @Override
    public float focalis$density() {
        return density;
    }

    @Override
    public float focalis$start() {
        return start;
    }

    @Override
    public float focalis$end() {
        return end;
    }
}
