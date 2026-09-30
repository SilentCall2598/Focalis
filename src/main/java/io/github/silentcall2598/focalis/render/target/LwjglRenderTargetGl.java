// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.render.target;

import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

import java.nio.IntBuffer;

/** {@link RenderTargetGl} on LWJGL 2, which throws if no context is current on the calling thread. */
final class LwjglRenderTargetGl implements RenderTargetGl {

    static final LwjglRenderTargetGl INSTANCE = new LwjglRenderTargetGl();

    private LwjglRenderTargetGl() {
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
    public int boundTexture() {
        return GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
    }

    // Raw GL on purpose. The binding is always put back, so GlStateManager's cache never sees it and stays right.
    @Override
    public void bindTexture(int texture) {
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
    }

    @Override
    public void texParameter(int name, int value) {
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, name, value);
    }

    @Override
    public void allocateTexture(int internalFormat, int width, int height, int format, int type) {
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, internalFormat, width, height, 0, format, type, (IntBuffer) null);
    }

    @Override
    public int createFramebuffer() {
        return GL30.glGenFramebuffers();
    }

    @Override
    public void deleteFramebuffer(int framebuffer) {
        GL30.glDeleteFramebuffers(framebuffer);
    }

    @Override
    public int drawFramebuffer() {
        return GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
    }

    @Override
    public void bindDrawFramebuffer(int framebuffer) {
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, framebuffer);
    }

    @Override
    public void attachTexture(int attachment, int texture) {
        GL30.glFramebufferTexture2D(GL30.GL_DRAW_FRAMEBUFFER, attachment, GL11.GL_TEXTURE_2D, texture, 0);
    }

    @Override
    public int framebufferStatus() {
        return GL30.glCheckFramebufferStatus(GL30.GL_DRAW_FRAMEBUFFER);
    }
}
