// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.shader.post;

/**
 * The post pass can't run, because of the pack's program or because this setup can't support it. Rendering should
 * go back to vanilla. Focalis bugs are never reported this way, they stay unchecked exceptions.
 */
public final class PostPassException extends Exception {

    private static final long serialVersionUID = 1L;

    PostPassException(String message) {
        super(message);
    }
}
