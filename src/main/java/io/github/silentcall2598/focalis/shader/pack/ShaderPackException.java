// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.shader.pack;

/** A shaderpack that can't be loaded, which is a problem with the pack itself and not with Focalis. */
public class ShaderPackException extends Exception {

    private static final long serialVersionUID = 1L;

    public ShaderPackException(String message) {
        super(message);
    }

    public ShaderPackException(String message, Throwable cause) {
        super(message, cause);
    }
}
