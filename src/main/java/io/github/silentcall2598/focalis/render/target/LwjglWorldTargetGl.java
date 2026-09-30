// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.render.target;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

/**
 * {@link WorldTargetGl} on LWJGL 2. Framebuffers are bound through GL30 directly, never through OpenGlHelper, so
 * the redirect of Minecraft's framebuffer never applies to Focalis's own binds.
 */
final class LwjglWorldTargetGl implements WorldTargetGl {

    static final LwjglWorldTargetGl INSTANCE = new LwjglWorldTargetGl();

    private LwjglWorldTargetGl() {
    }

    @Override
    public int readFramebuffer() {
        return GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
    }

    @Override
    public int drawFramebuffer() {
        return GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
    }

    @Override
    public void bindFramebuffer(int framebuffer) {
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, framebuffer);
    }

    @Override
    public void bindReadFramebuffer(int framebuffer) {
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, framebuffer);
    }

    @Override
    public void bindDrawFramebuffer(int framebuffer) {
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, framebuffer);
    }

    @Override
    public int drawDepthRenderbuffer() {
        int type = GL30.glGetFramebufferAttachmentParameteri(GL30.GL_DRAW_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT,
                GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE);
        if (type != GL30.GL_RENDERBUFFER) {
            return 0;
        }
        return GL30.glGetFramebufferAttachmentParameteri(GL30.GL_DRAW_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT,
                GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME);
    }

    // Depth can only be blitted with nearest filtering. The sizes match, so nothing is scaled anyway.
    @Override
    public void copyColorAndDepth(int width, int height) {
        GL30.glBlitFramebuffer(0, 0, width, height, 0, 0, width, height,
                GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT, GL11.GL_NEAREST);
    }
}
