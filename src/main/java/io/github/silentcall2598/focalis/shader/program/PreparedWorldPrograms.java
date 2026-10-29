// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.shader.program;

import io.github.silentcall2598.focalis.shader.pack.CustomUniforms;
import io.github.silentcall2598.focalis.shader.pack.IncludeException;
import io.github.silentcall2598.focalis.shader.pack.PackConfiguration;
import io.github.silentcall2598.focalis.shader.pack.ProgramDirectory;
import io.github.silentcall2598.focalis.shader.pack.ProgramSource;
import io.github.silentcall2598.focalis.shader.pack.ProgramStates;
import io.github.silentcall2598.focalis.shader.pack.ShaderPack;
import io.github.silentcall2598.focalis.shader.routing.ProgramResolution;
import io.github.silentcall2598.focalis.shader.routing.ResolutionState;
import io.github.silentcall2598.focalis.shader.routing.ShaderProgramResolver;
import io.github.silentcall2598.focalis.shader.routing.ShaderProgramRole;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class PreparedWorldPrograms {

    private final Map<ShaderProgramRole, Entry> entries;
    private final List<PreparedProgram> uniquePrograms;
    private final CustomUniforms customUniforms;
    private final ProgramStates programStates;

    private PreparedWorldPrograms(Map<ShaderProgramRole, Entry> entries, List<PreparedProgram> uniquePrograms,
            CustomUniforms customUniforms, ProgramStates programStates) {
        this.entries = entries;
        this.uniquePrograms = uniquePrograms;
        this.customUniforms = customUniforms;
        this.programStates = programStates;
    }

    public static PreparedWorldPrograms prepare(ShaderPack pack, ProgramDirectory directory,
            PackConfiguration configuration) {
        Objects.requireNonNull(pack, "pack");
        Objects.requireNonNull(directory, "directory");
        Objects.requireNonNull(configuration, "configuration");
        if (!ownsDirectory(pack, directory)) {
            String folder = directory.name().isEmpty() ? "shaders" : "shaders/" + directory.name();
            throw new IllegalArgumentException("Program folder '" + folder + "' doesn't belong to shaderpack '"
                    + pack.name() + "'");
        }
        if (configuration.pack() != pack) {
            throw new IllegalArgumentException("The configuration belongs to shaderpack '"
                    + configuration.pack().name() + "', not '" + pack.name() + "'");
        }
        Map<ProgramSource, Outcome> outcomes = new IdentityHashMap<>();
        Map<ShaderProgramRole, Entry> entries = new EnumMap<>(ShaderProgramRole.class);
        List<PreparedProgram> unique = new ArrayList<>();
        for (ShaderProgramRole role : ShaderProgramRole.values()) {
            ProgramResolution resolution = ShaderProgramResolver.resolve(directory, role, configuration::isEnabled);
            ProgramSource source = resolution.program();
            if (resolution.state() != ResolutionState.RESOLVED || source == null) {
                entries.put(role, new Entry(role, resolution, null, null));
                continue;
            }
            Outcome outcome = outcomes.get(source);
            if (outcome == null) {
                outcome = Outcome.prepare(pack, source, configuration);
                outcomes.put(source, outcome);
                if (outcome.program != null) {
                    unique.add(outcome.program);
                }
            }
            entries.put(role, new Entry(role, resolution, outcome.program, outcome.problem));
        }
        return new PreparedWorldPrograms(Collections.unmodifiableMap(entries), Collections.unmodifiableList(unique),
                configuration.customUniforms(), configuration.programStates());
    }

    // Programs are prepared from the pack's own file texts, so a folder of another pack with the same paths would
    // quietly get the wrong source. Folder names can match across packs, so only the exact object counts.
    private static boolean ownsDirectory(ShaderPack pack, ProgramDirectory directory) {
        if (directory == pack.root()) {
            return true;
        }
        for (ProgramDirectory dimension : pack.dimensionDirectories().values()) {
            if (dimension == directory) {
                return true;
            }
        }
        return false;
    }

    public Entry forRole(ShaderProgramRole role) {
        return entries.get(Objects.requireNonNull(role, "role"));
    }

    public Map<ShaderProgramRole, Entry> entries() {
        return entries;
    }

    public CustomUniforms customUniforms() {
        return customUniforms;
    }

    public ProgramStates programStates() {
        return programStates;
    }

    public List<PreparedProgram> uniquePrograms() {
        return uniquePrograms;
    }

    public static final class Entry {

        private final ShaderProgramRole role;
        private final ProgramResolution resolution;
        @Nullable
        private final PreparedProgram program;
        @Nullable
        private final String problem;

        Entry(ShaderProgramRole role, ProgramResolution resolution, @Nullable PreparedProgram program,
                @Nullable String problem) {
            this.role = role;
            this.resolution = resolution;
            this.program = program;
            this.problem = problem;
        }

        public ShaderProgramRole role() {
            return role;
        }

        public ProgramResolution resolution() {
            return resolution;
        }

        @Nullable
        public PreparedProgram program() {
            return program;
        }

        @Nullable
        public String problem() {
            return problem;
        }

        public boolean ready() {
            return program != null;
        }

        @Override
        public String toString() {
            return resolution + (program != null ? " ready" : problem != null ? " failed: " + problem : "");
        }
    }

    private static final class Outcome {

        @Nullable
        final PreparedProgram program;
        @Nullable
        final String problem;

        private Outcome(@Nullable PreparedProgram program, @Nullable String problem) {
            this.program = program;
            this.problem = problem;
        }

        static Outcome prepare(ShaderPack pack, ProgramSource source, PackConfiguration configuration) {
            try {
                return new Outcome(PreparedProgram.prepare(pack, source, configuration), null);
            } catch (IncludeException e) {
                return new Outcome(null, e.getMessage());
            }
        }
    }
}
