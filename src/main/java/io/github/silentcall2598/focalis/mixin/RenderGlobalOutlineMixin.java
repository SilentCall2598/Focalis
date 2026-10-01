// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.mixin;

import io.github.silentcall2598.focalis.render.lifecycle.RenderHooks;
import io.github.silentcall2598.focalis.render.lifecycle.RenderStage;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.culling.ICamera;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Slice;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Marks vanilla's entity outline block inside renderEntities, which EntityRendererStageMixin reports as a whole. The
// block opens with the entityOutlines profiler section and ends by binding Minecraft's framebuffer again, the only
// getFramebuffer call in the method. Both points are inside the block, so each START has its END.
@Mixin(RenderGlobal.class)
public abstract class RenderGlobalOutlineMixin {

    @Inject(method = "renderEntities(Lnet/minecraft/entity/Entity;Lnet/minecraft/client/renderer/culling/ICamera;F)V",
            at = @At(value = "INVOKE_STRING",
                    target = "Lnet/minecraft/profiler/Profiler;endStartSection(Ljava/lang/String;)V",
                    args = "ldc=entityOutlines"),
            require = 0, expect = 1)
    private void focalis$outlinesStart(Entity entity, ICamera camera, float partialTicks, CallbackInfo ci) {
        RenderHooks.stageStart(RenderStage.ENTITY_OUTLINES, partialTicks);
    }

    @Inject(method = "renderEntities(Lnet/minecraft/entity/Entity;Lnet/minecraft/client/renderer/culling/ICamera;F)V",
            slice = @Slice(
                    from = @At(value = "INVOKE",
                            target = "Lnet/minecraft/client/Minecraft;getFramebuffer()"
                                    + "Lnet/minecraft/client/shader/Framebuffer;"),
                    to = @At(value = "INVOKE_STRING",
                            target = "Lnet/minecraft/profiler/Profiler;endStartSection(Ljava/lang/String;)V",
                            args = "ldc=blockentities")),
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/shader/Framebuffer;bindFramebuffer(Z)V",
                    shift = At.Shift.AFTER),
            require = 0, expect = 1)
    private void focalis$outlinesEnd(Entity entity, ICamera camera, float partialTicks, CallbackInfo ci) {
        RenderHooks.stageEnd(RenderStage.ENTITY_OUTLINES, partialTicks);
    }
}
