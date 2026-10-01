// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.shader.program;

import io.github.silentcall2598.focalis.shader.pack.ProgramDirectory;

/**
 * The world programs of one program folder, prepared and built once for the whole session. The session that made it
 * owns the built programs and deletes them.
 */
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

    /** Whether at least one program was built, so something can be bound. */
    public boolean usable() {
        for (BuiltWorldPrograms.Build build : programs.builds()) {
            if (build.succeeded()) {
                return true;
            }
        }
        return false;
    }
}
