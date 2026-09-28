// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.shader.routing;

import io.github.silentcall2598.focalis.shader.pack.ProgramSource;

import javax.annotation.Nullable;
import java.util.List;

/** Which pack program one role got in one program folder. Immutable. */
public final class ProgramResolution {

    private final ShaderProgramRole role;
    private final ResolutionState state;
    @Nullable
    private final ProgramSource program;
    private final List<String> candidates;
    private final int fallbackDepth;

    ProgramResolution(ShaderProgramRole role, ResolutionState state, @Nullable ProgramSource program,
            List<String> candidates, int fallbackDepth) {
        this.role = role;
        this.state = state;
        this.program = program;
        this.candidates = candidates;
        this.fallbackDepth = fallbackDepth;
    }

    public ShaderProgramRole role() {
        return role;
    }

    public ResolutionState state() {
        return state;
    }

    /** The program the pack provides for this role, exactly as loaded, or null unless the state is RESOLVED. */
    @Nullable
    public ProgramSource program() {
        return program;
    }

    @Nullable
    public String selectedName() {
        return program == null ? null : program.name();
    }

    /** 0 when the most specific program exists, higher for each fallback taken, -1 when nothing was selected. */
    public int fallbackDepth() {
        return fallbackDepth;
    }

    /** The program names looked for, most specific first. Empty when the role has no chain. */
    public List<String> candidates() {
        return candidates;
    }

    @Override
    public String toString() {
        return role + " " + state + (program == null ? "" : " " + program.name() + " at depth " + fallbackDepth);
    }
}
