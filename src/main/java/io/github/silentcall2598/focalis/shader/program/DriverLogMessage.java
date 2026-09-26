// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.shader.program;

import io.github.silentcall2598.focalis.shader.pack.SourceLocation;

import javax.annotation.Nullable;

/** One line of a driver log, with the pack file and line it points at when the log format is understood. */
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

    /** The line exactly as the driver wrote it. */
    public String text() {
        return text;
    }

    /** The driver's message without its location prefix, or the whole line if the format isn't understood. */
    public String message() {
        return message;
    }

    /** Where the message points in the pack, or null if that couldn't be worked out. */
    @Nullable
    public SourceLocation location() {
        return location;
    }
}
