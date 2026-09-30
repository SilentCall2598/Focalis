// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.render.target;

/** The driver can't provide a render target, like when it reports the framebuffer as incomplete. */
public final class RenderTargetException extends Exception {

    private static final long serialVersionUID = 1L;

    RenderTargetException(String message) {
        super(message);
    }
}
