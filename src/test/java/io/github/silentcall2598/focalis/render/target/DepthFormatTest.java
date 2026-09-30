// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.render.target;

import org.junit.jupiter.api.Test;
import org.lwjgl.opengl.EXTPackedDepthStencil;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DepthFormatTest {

    // The scene capture blits Minecraft's depth into these, which only works while they stay exactly what 1.12.2's
    // Framebuffer allocates.
    @Test
    void matchesTheRenderbufferFormatsMinecraftAllocates() {
        // Framebuffer.createFramebuffer passes the literal 33190 when stencil is off.
        assertEquals(33190, DepthFormat.withStencil(false).internalFormat);
        assertEquals(EXTPackedDepthStencil.GL_DEPTH24_STENCIL8_EXT, DepthFormat.withStencil(true).internalFormat);
    }
}
