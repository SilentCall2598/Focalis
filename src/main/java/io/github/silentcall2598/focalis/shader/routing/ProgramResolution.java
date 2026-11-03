// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.shader.routing;

import io.github.silentcall2598.focalis.shader.pack.ProgramSource;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;

public final class ProgramResolution {

    private final ShaderProgramRole role;
    private final ResolutionState state;
    @Nullable
    private final ProgramSource program;
    private final List<String> candidates;
    private final int fallbackDepth;
    private final List<String> disabled;

    ProgramResolution(ShaderProgramRole role, ResolutionState state, @Nullable ProgramSource program,
            List<String> candidates, int fallbackDepth) {
        this(role, state, program, candidates, fallbackDepth, Collections.<String>emptyList());
    }

    ProgramResolution(ShaderProgramRole role, ResolutionState state, @Nullable ProgramSource program,
            List<String> candidates, int fallbackDepth, List<String> disabled) {
        this.role = role;
        this.state = state;
        this.program = program;
        this.candidates = candidates;
        this.fallbackDepth = fallbackDepth;
        this.disabled = disabled;
    }

    public ShaderProgramRole role() {
        return role;
    }

    public ResolutionState state() {
        return state;
    }

    @Nullable
    public ProgramSource program() {
        return program;
    }

    @Nullable
    public String selectedName() {
        return program == null ? null : program.name();
    }

    public int fallbackDepth() {
        return fallbackDepth;
    }

    public List<String> candidates() {
        return candidates;
    }

    public List<String> disabled() {
        return disabled;
    }

    @Override
    public String toString() {
        return role + " " + state + (program == null ? "" : " " + program.name() + " at depth " + fallbackDepth)
                + (disabled.isEmpty() ? "" : " past disabled " + disabled);
    }
}
