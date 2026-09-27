// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.shader.post;

import net.minecraft.client.shader.Framebuffer;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL30;

/**
 * The depth formats Minecraft 1.12.2's framebuffer can have. A depth blit only works between matching formats, so
 * the scene depth texture always uses the one Minecraft picked.
 */
enum SceneDepthFormat {
    /** What {@code Framebuffer.createFramebuffer} allocates normally. */
    DEPTH_24(GL14.GL_DEPTH_COMPONENT24, GL11.GL_DEPTH_COMPONENT, GL11.GL_UNSIGNED_INT, GL30.GL_DEPTH_ATTACHMENT),
    /** What it allocates once Forge's {@code Framebuffer.enableStencil} was called. */
    DEPTH_24_STENCIL_8(GL30.GL_DEPTH24_STENCIL8, GL30.GL_DEPTH_STENCIL, GL30.GL_UNSIGNED_INT_24_8,
            GL30.GL_DEPTH_STENCIL_ATTACHMENT);

    final int internalFormat;
    final int format;
    final int type;
    final int attachment;

    SceneDepthFormat(int internalFormat, int format, int type, int attachment) {
        this.internalFormat = internalFormat;
        this.format = format;
        this.type = type;
        this.attachment = attachment;
    }

    static SceneDepthFormat of(Framebuffer framebuffer) {
        return forStencil(framebuffer.isStencilEnabled());
    }

    static SceneDepthFormat forStencil(boolean stencilEnabled) {
        return stencilEnabled ? DEPTH_24_STENCIL_8 : DEPTH_24;
    }
}
