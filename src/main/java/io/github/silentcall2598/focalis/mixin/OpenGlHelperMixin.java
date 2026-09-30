// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.mixin;

import io.github.silentcall2598.focalis.render.target.MainFramebufferRedirect;
import net.minecraft.client.renderer.OpenGlHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

// Swaps Minecraft's framebuffer for the Focalis world target while a world pass draws into it, and changes nothing
// otherwise. require = 0 because the world target notices on its own when this never runs and stops.
@Mixin(OpenGlHelper.class)
public abstract class OpenGlHelperMixin {

    @ModifyVariable(method = "glBindFramebuffer(II)V", at = @At("HEAD"), argsOnly = true, ordinal = 1,
            require = 0)
    private static int focalis$redirectMainFramebuffer(int framebuffer) {
        return MainFramebufferRedirect.substitute(framebuffer);
    }
}
