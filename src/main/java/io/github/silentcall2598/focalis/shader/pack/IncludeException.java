// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.shader.pack;

import javax.annotation.Nullable;

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

    public String reason() {
        return reason;
    }

    @Nullable
    public SourceLocation location() {
        return location;
    }
}
