// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.shader.program;

import io.github.silentcall2598.focalis.shader.pack.SourceLocation;

import javax.annotation.Nullable;

public final class DriverLogMessage {

    private final String text;
    private final String message;
    @Nullable
    private final SourceLocation location;

    DriverLogMessage(String text, String message, @Nullable SourceLocation location) {
        this.text = text;
        this.message = message;
        this.location = location;
    }

    public String text() {
        return text;
    }

    public String message() {
        return message;
    }

    @Nullable
    public SourceLocation location() {
        return location;
    }
}
