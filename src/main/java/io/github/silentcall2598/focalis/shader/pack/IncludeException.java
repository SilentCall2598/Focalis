// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.shader.pack;

import javax.annotation.Nullable;

/** An {@code #include} that can't be resolved, with the location of the directive that caused it. */
public final class IncludeException extends ShaderPackException {

    private static final long serialVersionUID = 1L;

    private final String reason;
    @Nullable
    private final transient SourceLocation location;

    IncludeException(String reason, @Nullable SourceLocation location) {
        super(location == null ? reason : reason + " (" + location + ")");
        this.reason = reason;
        this.location = location;
    }

    /** The message without the location. */
    public String reason() {
        return reason;
    }

    /** The {@code #include} line that failed, or null when the program's own file is the problem. */
    @Nullable
    public SourceLocation location() {
        return location;
    }
}
