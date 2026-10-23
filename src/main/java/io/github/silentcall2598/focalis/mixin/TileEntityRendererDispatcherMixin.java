// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.github.silentcall2598.focalis.render.lifecycle.RenderHooks;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.tileentity.TileEntity;
import org.spongepowered.asm.mixin.Mixin;

// Reports each block entity renderer starting and returning. Every other render method here ends up in this one,
// which hands the block entity to its renderer. Fast renderers only fill Forge's batch there, which drawBatch draws.
// The return is also reported when the renderer throws, before whoever catches it draws on. A destroy stage from 0 to
// 9 means the renderer draws the block entity's breaking overlay.
@Mixin(TileEntityRendererDispatcher.class)
public abstract class TileEntityRendererDispatcherMixin {

    @WrapMethod(method = "render(Lnet/minecraft/tileentity/TileEntity;DDDFIF)V", require = 0, expect = 1)
    private void focalis$blockEntity(TileEntity tileEntity, double x, double y, double z, float partialTicks,
            int destroyStage, float alpha, Operation<Void> original) {
        boolean started = RenderHooks.blockEntityStarted(tileEntity, destroyStage);
        try {
            original.call(tileEntity, x, y, z, partialTicks, destroyStage, alpha);
        } finally {
            if (started) {
                RenderHooks.blockEntityReturned();
            }
        }
    }

    @WrapMethod(method = "drawBatch(I)V", require = 0, expect = 1, remap = false)
    private void focalis$batch(int pass, Operation<Void> original) {
        boolean started = RenderHooks.blockEntityBatchStarted();
        try {
            original.call(pass);
        } finally {
            if (started) {
                RenderHooks.blockEntityBatchEnded();
            }
        }
    }
}
