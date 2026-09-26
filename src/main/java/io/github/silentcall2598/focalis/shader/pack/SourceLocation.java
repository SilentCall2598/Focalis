// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.shader.pack;

import javax.annotation.Nullable;
import java.util.Objects;

/** A line in a pack file, or a line Focalis added itself when {@link #file()} is null. */
public final class SourceLocation {

    static final SourceLocation ADDED_BY_FOCALIS = new SourceLocation(null, 0);

    @Nullable
    private final ShaderPath file;
    private final int line;

    SourceLocation(@Nullable ShaderPath file, int line) {
        this.file = file;
        this.line = line;
    }

    @Nullable
    public ShaderPath file() {
        return file;
    }

    /** One-based line number, or 0 for lines Focalis added. */
    public int line() {
        return line;
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof SourceLocation)) {
            return false;
        }
        SourceLocation location = (SourceLocation) other;
        return line == location.line && Objects.equals(file, location.file);
    }

    @Override
    public int hashCode() {
        return Objects.hash(file, line);
    }

    @Override
    public String toString() {
        return file == null ? "<added by Focalis>" : file + ":" + line;
    }
}
