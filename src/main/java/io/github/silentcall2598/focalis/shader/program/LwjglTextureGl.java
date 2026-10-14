// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.shader.program;

import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.opengl.ContextCapabilities;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL21;
import org.lwjgl.opengl.GLContext;

import java.nio.ByteBuffer;

/**
 * {@link TextureGl} on LWJGL 2. Short bindings and unit switches are raw GL and always put back, so GlStateManager's
 * texture cache never sees them. Bindings that stay while other code draws update its cache too.
 */
final class LwjglTextureGl implements TextureGl {

    static final LwjglTextureGl INSTANCE = new LwjglTextureGl();
    // GlStateManager caches the bindings of units 0 to 7 in 1.12.2, and indexing past that would throw.
    private static final int CACHED_UNITS = 8;

    private LwjglTextureGl() {
    }

    @Override
    public int maxTextureUnits() {
        return GL11.glGetInteger(GL20.GL_MAX_COMBINED_TEXTURE_IMAGE_UNITS);
    }

    @Override
    public int maxTextureSize() {
        return GL11.glGetInteger(GL11.GL_MAX_TEXTURE_SIZE);
    }

    @Override
    public int createTexture() {
        return GlStateManager.generateTexture();
    }

    // GlStateManager forgets deleted textures, so its binding cache can't point at them afterwards.
    @Override
    public void deleteTexture(int texture) {
        GlStateManager.deleteTexture(texture);
    }

    @Override
    public int activeUnit() {
        return GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE) - GL13.GL_TEXTURE0;
    }

    @Override
    public void activeUnit(int unit) {
        GL13.glActiveTexture(GL13.GL_TEXTURE0 + unit);
    }

    @Override
    public int boundTexture() {
        return GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
    }

    @Override
    public void bindTexture(int texture) {
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
    }

    // Raw GL first, since GlStateManager skips calls that match a cache that may already be stale.
    @Override
    public void bindOnUnit(int unit, int texture) {
        GL13.glActiveTexture(GL13.GL_TEXTURE0 + unit);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
        if (unit < CACHED_UNITS) {
            GlStateManager.setActiveTexture(GL13.GL_TEXTURE0 + unit);
            GlStateManager.bindTexture(texture);
        }
    }

    @Override
    public void restoreUnit(int unit) {
        GL13.glActiveTexture(GL13.GL_TEXTURE0 + unit);
        if (unit < CACHED_UNITS) {
            GlStateManager.setActiveTexture(GL13.GL_TEXTURE0 + unit);
        }
    }

    // Only at setup, so the unpack state is read and put back instead of assumed. A mod could have left a pixel
    // buffer bound, which would make the upload read from it.
    @Override
    public void upload(int width, int height, ByteBuffer pixels, boolean linear, boolean clamp) {
        ContextCapabilities caps = GLContext.getCapabilities();
        boolean pixelBuffers = caps.OpenGL21 || caps.GL_ARB_pixel_buffer_object;
        int alignment = GL11.glGetInteger(GL11.GL_UNPACK_ALIGNMENT);
        int rowLength = GL11.glGetInteger(GL11.GL_UNPACK_ROW_LENGTH);
        int skipRows = GL11.glGetInteger(GL11.GL_UNPACK_SKIP_ROWS);
        int skipPixels = GL11.glGetInteger(GL11.GL_UNPACK_SKIP_PIXELS);
        int unpackBuffer = pixelBuffers ? GL11.glGetInteger(GL21.GL_PIXEL_UNPACK_BUFFER_BINDING) : 0;
        try {
            GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 4);
            GL11.glPixelStorei(GL11.GL_UNPACK_ROW_LENGTH, 0);
            GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_ROWS, 0);
            GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_PIXELS, 0);
            if (unpackBuffer != 0) {
                GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, 0);
            }
            int filter = linear ? GL11.GL_LINEAR : GL11.GL_NEAREST;
            int wrap = clamp ? GL12.GL_CLAMP_TO_EDGE : GL11.GL_REPEAT;
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, filter);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, filter);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, wrap);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, wrap);
            // No mipmaps, so level 0 is the whole texture.
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_MAX_LEVEL, 0);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, width, height, 0, GL11.GL_RGBA,
                    GL11.GL_UNSIGNED_BYTE, pixels);
        } finally {
            GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, alignment);
            GL11.glPixelStorei(GL11.GL_UNPACK_ROW_LENGTH, rowLength);
            GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_ROWS, skipRows);
            GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_PIXELS, skipPixels);
            if (unpackBuffer != 0) {
                GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, unpackBuffer);
            }
        }
    }
}
