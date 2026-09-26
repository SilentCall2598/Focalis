// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.shader.pack;

import javax.annotation.Nullable;

/** Something wrong with part of a pack that didn't stop the rest of it from loading. */
public final class PackIssue {

    @Nullable
    private final SourceLocation location;
    private final String message;

    PackIssue(@Nullable SourceLocation location, String message) {
        this.location = location;
        this.message = message;
    }

    @Nullable
    public SourceLocation location() {
        return location;
    }

    public String message() {
        return message;
    }

    @Override
    public String toString() {
        return location == null ? message : location + ": " + message;
    }
}
