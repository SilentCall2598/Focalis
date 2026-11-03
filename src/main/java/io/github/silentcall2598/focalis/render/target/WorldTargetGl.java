// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.render.target;

interface WorldTargetGl {

    int readFramebuffer();

    int drawFramebuffer();

    void bindFramebuffer(int framebuffer);

    void bindReadFramebuffer(int framebuffer);

    void bindDrawFramebuffer(int framebuffer);

    /** The renderbuffer attached as depth to the draw framebuffer, or 0 if its depth is anything else. */
    int drawDepthRenderbuffer();

    void copyColorAndDepth(int width, int height);
}
