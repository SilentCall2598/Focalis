// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.render.target;

interface RenderTargetGl {

    int createTexture();

    void deleteTexture(int texture);

    int boundTexture();

    void bindTexture(int texture);

    void texParameter(int name, int value);

    void allocateTexture(int internalFormat, int width, int height, int format, int type);

    int createFramebuffer();

    void deleteFramebuffer(int framebuffer);

    int drawFramebuffer();

    void bindDrawFramebuffer(int framebuffer);

    void attachTexture(int attachment, int texture);

    int framebufferStatus();
}
