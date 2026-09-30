// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.render.target;

/**
 * The OpenGL calls render targets make, nothing more. Every method needs a current context on the calling thread.
 * Tests swap in a recording version to check that nothing leaks and no binding is left changed.
 */
interface RenderTargetGl {

    /** Returns 0 if the driver couldn't create one. */
    int createTexture();

    void deleteTexture(int texture);

    /** The 2D texture bound on the active unit. */
    int boundTexture();

    /** Binds a 2D texture on the active unit. */
    void bindTexture(int texture);

    /** Sets a parameter of the bound 2D texture. */
    void texParameter(int name, int value);

    /** Allocates level 0 of the bound 2D texture without uploading anything. */
    void allocateTexture(int internalFormat, int width, int height, int format, int type);

    /** Returns 0 if the driver couldn't create one. */
    int createFramebuffer();

    void deleteFramebuffer(int framebuffer);

    int drawFramebuffer();

    void bindDrawFramebuffer(int framebuffer);

    /** Attaches a 2D texture to the framebuffer bound for drawing. */
    void attachTexture(int attachment, int texture);

    /** The completeness status of the framebuffer bound for drawing. */
    int framebufferStatus();
}
