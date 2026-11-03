// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.shader.program;

import io.github.silentcall2598.focalis.shader.pack.ProgramDirectory;

/** The session that made it owns the built programs and deletes them. */
public final class DirectoryPrograms {

    private final ProgramDirectory directory;
    private final PreparedWorldPrograms prepared;
    private final BuiltWorldPrograms programs;

    DirectoryPrograms(ProgramDirectory directory, PreparedWorldPrograms prepared, BuiltWorldPrograms programs) {
        this.directory = directory;
        this.prepared = prepared;
        this.programs = programs;
    }

    public ProgramDirectory directory() {
        return directory;
    }

    public PreparedWorldPrograms prepared() {
        return prepared;
    }

    public BuiltWorldPrograms programs() {
        return programs;
    }

    public boolean usable() {
        for (BuiltWorldPrograms.Build build : programs.builds()) {
            if (build.succeeded()) {
                return true;
            }
        }
        return false;
    }
}
