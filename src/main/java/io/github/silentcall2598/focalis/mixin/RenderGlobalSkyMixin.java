// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.silentcall2598.focalis.render.lifecycle.RenderDrawKind;
import io.github.silentcall2598.focalis.render.lifecycle.RenderHooks;
import io.github.silentcall2598.focalis.render.lifecycle.RenderStage;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.Tessellator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

// Marks the sun and moon inside the surface sky, which EntityRendererStageMixin reports as a whole. In renderSky the
// sunrise fan is draw 0 and the black geometry below the horizon is draw 3, and those stay part of the basic sky.
@Mixin(RenderGlobal.class)
public abstract class RenderGlobalSkyMixin {

    // The trailing partialTicks is renderSky's own argument, so it matches the outer SKY boundary.
    @WrapOperation(method = "renderSky(FI)V", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/Tessellator;draw()V", ordinal = 1), require = 0, expect = 1)
    private void focalis$sun(Tessellator tessellator, Operation<Void> original, float partialTicks) {
        RenderHooks.stageStart(RenderStage.SKY, RenderDrawKind.SKY_TEXTURED, partialTicks);
        try {
            original.call(tessellator);
        } finally {
            RenderHooks.stageEnd(RenderStage.SKY, RenderDrawKind.SKY_TEXTURED, partialTicks);
        }
    }

    @WrapOperation(method = "renderSky(FI)V", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/Tessellator;draw()V", ordinal = 2), require = 0, expect = 1)
    private void focalis$moon(Tessellator tessellator, Operation<Void> original, float partialTicks) {
        RenderHooks.stageStart(RenderStage.SKY, RenderDrawKind.SKY_TEXTURED, partialTicks);
        try {
            original.call(tessellator);
        } finally {
            RenderHooks.stageEnd(RenderStage.SKY, RenderDrawKind.SKY_TEXTURED, partialTicks);
        }
    }
}
