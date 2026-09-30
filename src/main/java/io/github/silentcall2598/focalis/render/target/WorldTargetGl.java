// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.render.target;

/** The OpenGL calls the world target makes around the world pass. All of them need a current context. */
interface WorldTargetGl {

    int readFramebuffer();

    int drawFramebuffer();

    /** Binds one framebuffer for both reading and drawing. */
    void bindFramebuffer(int framebuffer);

    void bindReadFramebuffer(int framebuffer);

    void bindDrawFramebuffer(int framebuffer);

    /** The renderbuffer attached as depth to the draw framebuffer, or 0 if its depth is anything else. */
    int drawDepthRenderbuffer();

    /** Copies color and depth unscaled from the read framebuffer to the draw framebuffer. */
    void copyColorAndDepth(int width, int height);
}
