// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.mixin;

import io.github.silentcall2598.focalis.render.lifecycle.RenderDrawKind;
import io.github.silentcall2598.focalis.render.lifecycle.RenderHooks;
import net.minecraft.client.renderer.tileentity.TileEntityBeaconRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Reports each beam segment. The full overload draws every one, the beacon's through the short overload, the end
// gateway's directly, and those of mods that reuse vanilla's beam.
@Mixin(TileEntityBeaconRenderer.class)
public abstract class TileEntityBeaconRendererMixin {

    private static final String SEGMENT = "renderBeamSegment(DDDDDDII[FDD)V";

    @Inject(method = SEGMENT, at = @At("HEAD"), require = 0, expect = 1)
    private static void focalis$beamStarted(CallbackInfo ci) {
        RenderHooks.specialDrawStarted(RenderDrawKind.BEACON_BEAM);
    }

    @Inject(method = SEGMENT, at = @At("RETURN"), require = 0, expect = 1)
    private static void focalis$beamEnded(CallbackInfo ci) {
        RenderHooks.specialDrawEnded(RenderDrawKind.BEACON_BEAM);
    }
}
