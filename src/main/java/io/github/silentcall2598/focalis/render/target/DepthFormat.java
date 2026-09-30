// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.render.target;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL30;

/** The depth texture of a {@link RenderTarget}. Both can be sampled for depth. */
public enum DepthFormat {
    DEPTH_24(GL14.GL_DEPTH_COMPONENT24, GL11.GL_DEPTH_COMPONENT, GL11.GL_UNSIGNED_INT, GL30.GL_DEPTH_ATTACHMENT),
    DEPTH_24_STENCIL_8(GL30.GL_DEPTH24_STENCIL8, GL30.GL_DEPTH_STENCIL, GL30.GL_UNSIGNED_INT_24_8,
            GL30.GL_DEPTH_STENCIL_ATTACHMENT);

    final int internalFormat;
    final int format;
    final int type;
    final int attachment;

    DepthFormat(int internalFormat, int format, int type, int attachment) {
        this.internalFormat = internalFormat;
        this.format = format;
        this.type = type;
        this.attachment = attachment;
    }

    public static DepthFormat withStencil(boolean stencil) {
        return stencil ? DEPTH_24_STENCIL_8 : DEPTH_24;
    }
}
