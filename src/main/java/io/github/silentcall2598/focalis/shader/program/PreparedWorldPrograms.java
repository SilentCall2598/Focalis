// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.shader.program;

import io.github.silentcall2598.focalis.shader.pack.IncludeException;
import io.github.silentcall2598.focalis.shader.pack.PackConfiguration;
import io.github.silentcall2598.focalis.shader.pack.ProgramDirectory;
import io.github.silentcall2598.focalis.shader.pack.ProgramSource;
import io.github.silentcall2598.focalis.shader.pack.ShaderMacros;
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

/**
 * The prepared source for every program role in one program folder. Roles that land on the same pack program share
 * one {@link PreparedProgram}, so each pack program only has to be built once. Immutable, and no OpenGL is involved.
 */
public final class PreparedWorldPrograms {

    private final Map<ShaderProgramRole, Entry> entries;
    private final List<PreparedProgram> uniquePrograms;

    private PreparedWorldPrograms(Map<ShaderProgramRole, Entry> entries, List<PreparedProgram> uniquePrograms) {
        this.entries = entries;
        this.uniquePrograms = uniquePrograms;
    }

    /**
     * Resolves every role in {@code directory} under the configuration and prepares the programs they select. Only
     * that folder is used, so picking the folder for a dimension is up to the caller. Programs the configuration
     * disables count as missing. A program that fails to prepare is recorded on the roles that selected it and
     * doesn't stop the others.
     *
     * @throws IllegalArgumentException if {@code directory} isn't one of {@code pack}'s own folders or the
     *     configuration is for another pack
     */
    public static PreparedWorldPrograms prepare(ShaderPack pack, ProgramDirectory directory, ShaderMacros environment,
            PackConfiguration configuration) {
        Objects.requireNonNull(pack, "pack");
        Objects.requireNonNull(directory, "directory");
        Objects.requireNonNull(environment, "environment");
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
        // ProgramSource has no equals, and each one is a distinct program the pack owns.
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
                outcome = Outcome.prepare(pack, source, environment, configuration);
                outcomes.put(source, outcome);
                if (outcome.program != null) {
                    unique.add(outcome.program);
                }
            }
            entries.put(role, new Entry(role, resolution, outcome.program, outcome.problem));
        }
        return new PreparedWorldPrograms(Collections.unmodifiableMap(entries), Collections.unmodifiableList(unique));
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

    /** The entry for a role. Every role has one, even the ones that don't draw anything. */
    public Entry forRole(ShaderProgramRole role) {
        return entries.get(Objects.requireNonNull(role, "role"));
    }

    /** Every role's entry in role order. Read only. */
    public Map<ShaderProgramRole, Entry> entries() {
        return entries;
    }

    /** Each successfully prepared program once, in the order roles first selected it. Read only. */
    public List<PreparedProgram> uniquePrograms() {
        return uniquePrograms;
    }

    /** How one role came out. Roles that selected the same pack program share its program or its problem. */
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

        /** The prepared program, or null unless the role resolved and its program prepared cleanly. */
        @Nullable
        public PreparedProgram program() {
            return program;
        }

        /**
         * Why the selected program couldn't be prepared, or null when nothing was selected or it worked. A role whose
         * programs are all disabled has no problem, its resolution says DISABLED instead.
         */
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

    // What preparing one pack program gave, shared by every role that selected it.
    private static final class Outcome {

        @Nullable
        final PreparedProgram program;
        @Nullable
        final String problem;

        private Outcome(@Nullable PreparedProgram program, @Nullable String problem) {
            this.program = program;
            this.problem = problem;
        }

        // A broken include only fails this program. Anything else propagates.
        static Outcome prepare(ShaderPack pack, ProgramSource source, ShaderMacros environment,
                PackConfiguration configuration) {
            try {
                return new Outcome(PreparedProgram.prepare(pack, source, environment, configuration), null);
            } catch (IncludeException e) {
                return new Outcome(null, e.getMessage());
            }
        }
    }
}
