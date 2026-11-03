// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.mixin;

import io.github.silentcall2598.focalis.shader.terrain.LightingCapture;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.vertex.VertexFormatElement;
import net.minecraftforge.client.model.pipeline.VertexBufferConsumer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = VertexBufferConsumer.class, remap = false)
public abstract class VertexBufferConsumerMixin {

    @Shadow
    private BufferBuilder renderer;
    @Shadow
    private int v;

    @Inject(method = "put", at = @At("HEAD"), require = 0, expect = 1)
    private void focalis$put(int element, float[] data, CallbackInfo ci) {
        if (LightingCapture.enabled() && renderer.getVertexFormat().getElement(element).getUsage()
                == VertexFormatElement.EnumUsage.COLOR) {
            LightingCapture.forgeColor(renderer, v, data);
        }
    }
}
