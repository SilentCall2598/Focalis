// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.shader.program;

/**
 * A pack's program couldn't be built. Unexpected problems inside Focalis are never reported this way, they stay
 * unchecked exceptions.
 */
public final class ProgramBuildException extends Exception {

    private static final long serialVersionUID = 1L;

    private final transient ProgramFailure failure;

    ProgramBuildException(ProgramFailure failure) {
        super(failure.summary());
        this.failure = failure;
    }

    public ProgramFailure failure() {
        return failure;
    }
}
