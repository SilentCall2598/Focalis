// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.render.target;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL30;

import javax.annotation.Nullable;
import java.util.Objects;

/**
 * A Focalis-owned framebuffer with one RGBA8 color texture and one depth texture, both sampled with nearest
 * filtering. Whoever creates it owns it and has to call {@link #delete()} on the client thread while the context that
 * created it is current. Nothing frees it automatically. Its size never changes, so a new size means a new target.
 * The contents are undefined until something draws into it.
 */
public final class RenderTarget {

    private final RenderTargetGl gl;
    private final int width;
    private final int height;
    private final DepthFormat depthFormat;
    private int framebuffer;
    private int colorTexture;
    private int depthTexture;
    private boolean deleted;

    private RenderTarget(RenderTargetGl gl, int width, int height, DepthFormat depthFormat) {
        this.gl = gl;
        this.width = width;
        this.height = height;
        this.depthFormat = depthFormat;
    }

    /**
     * Must run on the client thread with the OpenGL context current. The texture and draw framebuffer bindings it
     * uses are put back before it returns, and nothing stays allocated if it throws.
     *
     * @throws RenderTargetException if the driver reports the framebuffer as incomplete
     */
    public static RenderTarget create(int width, int height, DepthFormat depthFormat) throws RenderTargetException {
        return create(LwjglRenderTargetGl.INSTANCE, width, height, depthFormat);
    }

    static RenderTarget create(RenderTargetGl gl, int width, int height, DepthFormat depthFormat)
            throws RenderTargetException {
        Objects.requireNonNull(depthFormat, "depthFormat");
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("A render target can't be " + width + "x" + height);
        }
        RenderTarget target = new RenderTarget(gl, width, height, depthFormat);
        int boundTexture = gl.boundTexture();
        int drawFramebuffer = gl.drawFramebuffer();
        Throwable failure = null;
        try {
            target.build();
        } catch (RenderTargetException | RuntimeException | LinkageError e) {
            failure = e;
        }
        // Put back first, so none of the target's objects is bound when a failed target gets deleted.
        try {
            gl.bindTexture(boundTexture);
            gl.bindDrawFramebuffer(drawFramebuffer);
        } catch (RuntimeException | LinkageError e) {
            failure = collect(failure, e);
        }
        if (failure == null) {
            return target;
        }
        try {
            target.delete();
        } catch (RuntimeException | LinkageError e) {
            failure = collect(failure, e);
        }
        if (failure instanceof RenderTargetException) {
            throw (RenderTargetException) failure;
        }
        throw unchecked(failure);
    }

    private void build() throws RenderTargetException {
        colorTexture = checked(gl.createTexture(), "texture");
        allocate(colorTexture, GL11.GL_RGBA8, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE);
        depthTexture = checked(gl.createTexture(), "texture");
        allocate(depthTexture, depthFormat.internalFormat, depthFormat.format, depthFormat.type);

        framebuffer = checked(gl.createFramebuffer(), "framebuffer");
        gl.bindDrawFramebuffer(framebuffer);
        gl.attachTexture(GL30.GL_COLOR_ATTACHMENT0, colorTexture);
        gl.attachTexture(depthFormat.attachment, depthTexture);
        int status = gl.framebufferStatus();
        if (status != GL30.GL_FRAMEBUFFER_COMPLETE) {
            throw new RenderTargetException("The render target framebuffer is incomplete (status 0x"
                    + Integer.toHexString(status) + ") for " + depthFormat + " depth at " + width + "x" + height);
        }
    }

    private static int checked(int name, String kind) {
        if (name == 0) {
            throw new IllegalStateException("The driver couldn't create a " + kind + " for a render target");
        }
        return name;
    }

    private void allocate(int texture, int internalFormat, int format, int type) {
        gl.bindTexture(texture);
        gl.texParameter(GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        gl.texParameter(GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        gl.texParameter(GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        gl.texParameter(GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        gl.allocateTexture(internalFormat, width, height, format, type);
    }

    /** Whether this target already has exactly this size and depth format. */
    public boolean matches(int width, int height, DepthFormat depthFormat) {
        return this.width == width && this.height == height && this.depthFormat == depthFormat;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public DepthFormat depthFormat() {
        return depthFormat;
    }

    /** The OpenGL framebuffer name. Throws once the target has been deleted. */
    public int framebuffer() {
        checkNotDeleted();
        return framebuffer;
    }

    /** The OpenGL name of the RGBA8 color texture. Throws once the target has been deleted. */
    public int colorTexture() {
        checkNotDeleted();
        return colorTexture;
    }

    /** The OpenGL name of the depth texture. Throws once the target has been deleted. */
    public int depthTexture() {
        checkNotDeleted();
        return depthTexture;
    }

    public boolean isDeleted() {
        return deleted;
    }

    private void checkNotDeleted() {
        if (deleted) {
            throw new IllegalStateException("The render target was already deleted");
        }
    }

    /**
     * Frees the framebuffer and both textures. Must run on the client thread with the context that created them
     * current. If one delete throws, the others are still attempted and the first failure is rethrown. Calling it
     * again does nothing.
     */
    public void delete() {
        if (deleted) {
            return;
        }
        deleted = true;
        Throwable failure = null;
        if (framebuffer != 0) {
            try {
                gl.deleteFramebuffer(framebuffer);
            } catch (RuntimeException | LinkageError e) {
                failure = e;
            }
            framebuffer = 0;
        }
        for (int texture : new int[] {colorTexture, depthTexture}) {
            if (texture != 0) {
                try {
                    gl.deleteTexture(texture);
                } catch (RuntimeException | LinkageError e) {
                    failure = collect(failure, e);
                }
            }
        }
        colorTexture = 0;
        depthTexture = 0;
        if (failure != null) {
            throw unchecked(failure);
        }
    }

    // The first failure wins and later ones are kept as suppressed.
    private static Throwable collect(@Nullable Throwable first, Throwable next) {
        if (first == null) {
            return next;
        }
        if (first != next) {
            first.addSuppressed(next);
        }
        return first;
    }

    // Only ever called with a RuntimeException or a LinkageError.
    private static RuntimeException unchecked(Throwable failure) {
        if (failure instanceof LinkageError) {
            throw (LinkageError) failure;
        }
        return (RuntimeException) failure;
    }
}
