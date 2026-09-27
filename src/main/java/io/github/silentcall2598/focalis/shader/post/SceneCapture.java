// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.shader.post;

import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.shader.Framebuffer;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL30;

import java.nio.IntBuffer;

/**
 * A Focalis-owned copy of Minecraft's color and depth, in textures the post program can sample. The capture owns its
 * framebuffer and both textures, and {@link #delete()} frees all of them.
 */
final class SceneCapture {

    private final int width;
    private final int height;
    private final SceneDepthFormat depthFormat;
    private final int sourceFramebuffer;
    private final int sourceDepthBuffer;
    private int colorTexture;
    private int depthTexture;
    private int framebuffer;

    private SceneCapture(Framebuffer source) {
        width = source.framebufferWidth;
        height = source.framebufferHeight;
        depthFormat = SceneDepthFormat.of(source);
        sourceFramebuffer = source.framebufferObject;
        sourceDepthBuffer = source.depthBuffer;
    }

    /**
     * Sized and formatted to match the source. Texture and framebuffer bindings made here are recorded in the state,
     * so the caller's restore undoes them. Nothing is left allocated if this throws.
     */
    static SceneCapture create(Framebuffer source, PassGlState state, int colorUnit, int depthUnit)
            throws PostPassException {
        checkDepthBuffer(source);
        SceneCapture capture = new SceneCapture(source);
        boolean complete = false;
        try {
            capture.colorTexture = GlStateManager.generateTexture();
            state.bindTexture(colorUnit, capture.colorTexture);
            setNearestAndClamped();
            GlStateManager.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, capture.width, capture.height, 0,
                    GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (IntBuffer) null);

            capture.depthTexture = GlStateManager.generateTexture();
            state.bindTexture(depthUnit, capture.depthTexture);
            setNearestAndClamped();
            SceneDepthFormat depth = capture.depthFormat;
            GlStateManager.glTexImage2D(GL11.GL_TEXTURE_2D, 0, depth.internalFormat, capture.width, capture.height,
                    0, depth.format, depth.type, (IntBuffer) null);

            capture.framebuffer = GL30.glGenFramebuffers();
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, capture.framebuffer);
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D,
                    capture.colorTexture, 0);
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, depth.attachment, GL11.GL_TEXTURE_2D,
                    capture.depthTexture, 0);
            int status = GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER);
            if (status != GL30.GL_FRAMEBUFFER_COMPLETE) {
                throw new PostPassException("The scene capture framebuffer is incomplete (status 0x"
                        + Integer.toHexString(status) + ") for " + depth + " depth at " + capture.width + "x"
                        + capture.height);
            }
            complete = true;
            return capture;
        } finally {
            if (!complete) {
                capture.delete();
            }
        }
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

    private static void setNearestAndClamped() {
        GlStateManager.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GlStateManager.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        GlStateManager.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GlStateManager.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
    }

    /** False once Minecraft's framebuffer was resized or recreated, which means this capture has to be replaced. */
    boolean matches(Framebuffer source) {
        return source.framebufferObject == sourceFramebuffer && source.depthBuffer == sourceDepthBuffer
                && source.framebufferWidth == width && source.framebufferHeight == height
                && SceneDepthFormat.of(source) == depthFormat;
    }

    /** Copies color and depth. Leaves the source bound for reading and the capture bound for drawing. */
    void copyFrom(Framebuffer source) {
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, source.framebufferObject);
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, framebuffer);
        // Depth can only be blitted with nearest filtering. The sizes match, so nothing is scaled anyway.
        GL30.glBlitFramebuffer(0, 0, width, height, 0, 0, width, height,
                GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT, GL11.GL_NEAREST);
    }

    int width() {
        return width;
    }

    int height() {
        return height;
    }

    SceneDepthFormat depthFormat() {
        return depthFormat;
    }

    int colorTexture() {
        return colorTexture;
    }

    int depthTexture() {
        return depthTexture;
    }

    /** Frees everything the capture owns. Calling it again does nothing. */
    void delete() {
        if (framebuffer != 0) {
            GL30.glDeleteFramebuffers(framebuffer);
            framebuffer = 0;
        }
        // GlStateManager forgets deleted textures, so its binding cache can't point at them afterwards.
        if (colorTexture != 0) {
            GlStateManager.deleteTexture(colorTexture);
            colorTexture = 0;
        }
        if (depthTexture != 0) {
            GlStateManager.deleteTexture(depthTexture);
            depthTexture = 0;
        }
    }
}
