// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.mixin;

import io.github.silentcall2598.focalis.shader.terrain.TerrainStreams;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.RenderGlobal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Any reload with a world rebuilds every chunk, so a reload the stream asked for has happened by then.
@Mixin(RenderGlobal.class)
public abstract class RenderGlobalStreamMixin {

    @Shadow
    private WorldClient world;

    @Inject(method = "loadRenderers()V", at = @At("HEAD"), require = 0, expect = 1)
    private void focalis$reloading(CallbackInfo ci) {
        if (world != null) {
            TerrainStreams.renderersReloading();
        }
    }
}
