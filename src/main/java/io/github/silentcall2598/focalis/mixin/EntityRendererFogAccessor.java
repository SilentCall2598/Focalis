// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.mixin;

import net.minecraft.client.renderer.EntityRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

// The fog color of the current world pass. Vanilla only keeps it in these fields and hands it to GL in setupFog.
@Mixin(EntityRenderer.class)
public interface EntityRendererFogAccessor {

    @Accessor("fogColorRed")
    float focalis$fogColorRed();

    @Accessor("fogColorGreen")
    float focalis$fogColorGreen();

    @Accessor("fogColorBlue")
    float focalis$fogColorBlue();
}
