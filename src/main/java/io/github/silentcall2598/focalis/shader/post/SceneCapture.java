// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.shader.post;

import io.github.silentcall2598.focalis.render.target.DepthFormat;
import io.github.silentcall2598.focalis.render.target.RenderTarget;
import io.github.silentcall2598.focalis.render.target.RenderTargetException;
import net.minecraft.client.shader.Framebuffer;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

/**
 * A Focalis-owned copy of Minecraft's color and depth, in a render target the post program can sample. The capture
 * owns the target, and {@link #delete()} frees it.
 */
final class SceneCapture {

    private final RenderTarget target;
    private final int sourceFramebuffer;
    private final int sourceDepthBuffer;

    private SceneCapture(RenderTarget target, Framebuffer source) {
        this.target = target;
        sourceFramebuffer = source.framebufferObject;
        sourceDepthBuffer = source.depthBuffer;
    }

    /**
     * Sized and formatted to match the source. Leaves the source bound for reading, which the caller's restore
     * undoes. Nothing is left allocated if this throws.
     */
    static SceneCapture create(Framebuffer source) throws PostPassException {
        checkDepthBuffer(source);
        RenderTarget target;
        try {
            target = RenderTarget.create(source.framebufferWidth, source.framebufferHeight, depthFormat(source));
        } catch (RenderTargetException e) {
            throw new PostPassException("Can't create the scene capture. " + e.getMessage());
        }
        return new SceneCapture(target, source);
    }

    // A depth blit only works between matching formats, so this has to be the renderbuffer format 1.12.2's
    // Framebuffer allocates. That's 24 bit depth, with 8 bits of stencil once Forge's enableStencil was called.
    private static DepthFormat depthFormat(Framebuffer source) {
        return DepthFormat.withStencil(source.isStencilEnabled());
    }

    // The depth format is only known because it's the renderbuffer 1.12.2's Framebuffer creates. If something
    // swapped it out, a depth blit could fail without any sign of it, so refuse instead.
    private static void checkDepthBuffer(Framebuffer source) throws PostPassException {
        if (!source.useDepth) {
            throw new PostPassException("Minecraft's framebuffer has no depth buffer to capture");
        }
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, source.framebufferObject);
        int type = GL30.glGetFramebufferAttachmentParameteri(GL30.GL_READ_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT,
                GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE);
        int name = type == GL30.GL_RENDERBUFFER ? GL30.glGetFramebufferAttachmentParameteri(
                GL30.GL_READ_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT, GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME) : 0;
        if (type != GL30.GL_RENDERBUFFER || name != source.depthBuffer) {
            throw new PostPassException("Minecraft's depth buffer was replaced by something other than the"
                    + " renderbuffer Minecraft created, so its format is unknown and can't be copied safely");
        }
    }

    /** False once Minecraft's framebuffer was resized or recreated, which means this capture has to be replaced. */
    boolean matches(Framebuffer source) {
        return source.framebufferObject == sourceFramebuffer && source.depthBuffer == sourceDepthBuffer
                && target.matches(source.framebufferWidth, source.framebufferHeight, depthFormat(source));
    }

    /** Copies color and depth. Leaves the source bound for reading and the capture bound for drawing. */
    void copyFrom(Framebuffer source) {
        int width = target.width();
        int height = target.height();
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, source.framebufferObject);
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, target.framebuffer());
        // Depth can only be blitted with nearest filtering. The sizes match, so nothing is scaled anyway.
        GL30.glBlitFramebuffer(0, 0, width, height, 0, 0, width, height,
                GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT, GL11.GL_NEAREST);
    }

    int width() {
        return target.width();
    }

    int height() {
        return target.height();
    }

    DepthFormat depthFormat() {
        return target.depthFormat();
    }

    int framebuffer() {
        return target.framebuffer();
    }

    int colorTexture() {
        return target.colorTexture();
    }

    int depthTexture() {
        return target.depthTexture();
    }

    /** Frees everything the capture owns. Calling it again does nothing. */
    void delete() {
        target.delete();
    }
}
