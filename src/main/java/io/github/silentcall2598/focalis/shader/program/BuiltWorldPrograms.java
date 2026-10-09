// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.shader.program;

import io.github.silentcall2598.focalis.shader.pack.CustomUniforms;
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
 * The OpenGL programs built from one {@link PreparedWorldPrograms}. Each unique prepared program is built once, and
 * every role that shares it shares the result. This set owns every program it built and nothing else may delete or
 * replace them. Nothing is bound, and nothing is freed automatically, so {@link #delete()} has to be called.
 */
public final class BuiltWorldPrograms {

    private final List<Build> builds;
    private final Map<ShaderProgramRole, Entry> entries;
    private boolean deleted;

    private BuiltWorldPrograms(List<Build> builds, Map<ShaderProgramRole, Entry> entries) {
        this.builds = builds;
        this.entries = entries;
    }

    /**
     * Builds every unique program in {@code prepared} and connects its samplers. Must run on the client thread with
     * the OpenGL context current and no Focalis program scope open.
     * A program the pack or driver can't build is recorded as a failure and the others are still built. Any other
     * exception deletes the programs built so far and is rethrown.
     */
    public static BuiltWorldPrograms build(PreparedWorldPrograms prepared, ShaderCapabilities capabilities) {
        Objects.requireNonNull(prepared, "prepared");
        Objects.requireNonNull(capabilities, "capabilities");
        return build(prepared, new ProgramBuilder(capabilities));
    }

    static BuiltWorldPrograms build(PreparedWorldPrograms prepared, ProgramBuilder builder) {
        Objects.requireNonNull(prepared, "prepared");
        List<Build> builds = new ArrayList<>();
        try {
            // PreparedProgram has no equals, and roles that share a pack program share the exact object.
            Map<PreparedProgram, Build> byProgram = new IdentityHashMap<>();
            for (PreparedProgram program : prepared.uniquePrograms()) {
                Build build = Build.attempt(builder, program, prepared.customUniforms());
                builds.add(build);
                byProgram.put(program, build);
            }
            Map<ShaderProgramRole, Entry> entries = new EnumMap<>(ShaderProgramRole.class);
            for (PreparedWorldPrograms.Entry entry : prepared.entries().values()) {
                PreparedProgram program = entry.program();
                entries.put(entry.role(), new Entry(entry, program == null ? null : byProgram.get(program)));
            }
            return new BuiltWorldPrograms(Collections.unmodifiableList(builds), Collections.unmodifiableMap(entries));
        } catch (RuntimeException | LinkageError e) {
            // The program that threw was already deleted by ProgramBuilder or Build.attempt. The ones before it are
            // still owned here.
            deleteNewestFirst(builds, e);
            throw e;
        }
    }

    // Every program gets its delete attempt even when an earlier one throws. Returns the first failure, or the one
    // passed in, with any later failures suppressed onto it.
    @Nullable
    private static Throwable deleteNewestFirst(List<Build> builds, @Nullable Throwable failure) {
        for (int i = builds.size() - 1; i >= 0; i--) {
            ShaderProgram program = builds.get(i).program;
            if (program == null) {
                continue;
            }
            try {
                program.delete();
            } catch (RuntimeException | LinkageError e) {
                if (failure == null) {
                    failure = e;
                } else if (failure != e) {
                    failure.addSuppressed(e);
                }
            }
        }
        return failure;
    }

    /** One result for every unique prepared program, in the same order, failures included. Read only. */
    public List<Build> builds() {
        return builds;
    }

    /** The entry for a role. Every role has one, even the ones nothing was built for. */
    public Entry forRole(ShaderProgramRole role) {
        return entries.get(Objects.requireNonNull(role, "role"));
    }

    /** Every role's entry in role order. Read only. */
    public Map<ShaderProgramRole, Entry> entries() {
        return entries;
    }

    public boolean isDeleted() {
        return deleted;
    }

    /**
     * Deletes every program this set built, each one once, newest first. Must run on the client thread with the
     * same OpenGL context current. If a delete throws, the rest are still attempted and the first failure is
     * rethrown afterwards. Either way the set counts as deleted and calling it again does nothing. Builds and
     * entries stay readable afterwards.
     */
    public void delete() {
        if (deleted) {
            return;
        }
        Throwable failure = deleteNewestFirst(builds, null);
        deleted = true;
        if (failure instanceof RuntimeException) {
            throw (RuntimeException) failure;
        }
        if (failure instanceof LinkageError) {
            throw (LinkageError) failure;
        }
    }

    /**
     * The one build attempt for a unique prepared program. Exactly one of program and failure is set, and a built
     * program always has its inputs.
     */
    public static final class Build {

        private final PreparedProgram prepared;
        @Nullable
        private final ShaderProgram program;
        @Nullable
        private final WorldProgramInputs inputs;
        @Nullable
        private final ProgramFailure failure;

        private Build(PreparedProgram prepared, @Nullable ShaderProgram program, @Nullable WorldProgramInputs inputs,
                @Nullable ProgramFailure failure) {
            this.prepared = prepared;
            this.program = program;
            this.inputs = inputs;
            this.failure = failure;
        }

        // The program is only owned by the set once this returns it, so anything failing before that deletes it.
        static Build attempt(ProgramBuilder builder, PreparedProgram prepared, CustomUniforms customs) {
            ShaderProgram program;
            try {
                program = builder.build(prepared);
            } catch (ProgramBuildException e) {
                return new Build(prepared, null, null, e.failure());
            }
            try {
                return new Build(prepared, program, WorldProgramInputs.connect(program.gl(), program, customs), null);
            } catch (ProgramBuildException e) {
                program.delete();
                return new Build(prepared, null, null, e.failure());
            } catch (RuntimeException | LinkageError e) {
                // If the inputs couldn't put the previous program back and this one is still current, GL only flags it
                // for deletion and frees it once something else is bound.
                try {
                    program.delete();
                } catch (RuntimeException | LinkageError deleteFailure) {
                    e.addSuppressed(deleteFailure);
                }
                throw e;
            }
        }

        public PreparedProgram prepared() {
            return prepared;
        }

        /** The built program, owned by the set, or null if the build failed. */
        @Nullable
        public ShaderProgram program() {
            return program;
        }

        /** The inputs Focalis set up for the built program, or null if the build failed. */
        @Nullable
        public WorldProgramInputs inputs() {
            return inputs;
        }

        /** Why the pack or driver couldn't build the program, or null if it built. */
        @Nullable
        public ProgramFailure failure() {
            return failure;
        }

        public boolean succeeded() {
            return program != null;
        }
    }

    /** How one role came out. Roles that share a prepared program share its {@link Build}. */
    public static final class Entry {

        private final PreparedWorldPrograms.Entry prepared;
        @Nullable
        private final Build build;

        Entry(PreparedWorldPrograms.Entry prepared, @Nullable Build build) {
            this.prepared = prepared;
            this.build = build;
        }

        public ShaderProgramRole role() {
            return prepared.role();
        }

        /** How the role resolved and prepared, including any source problem. */
        public PreparedWorldPrograms.Entry prepared() {
            return prepared;
        }

        /** The build this role uses, or null when the role had no prepared program to build. */
        @Nullable
        public Build build() {
            return build;
        }

        @Nullable
        public ShaderProgram program() {
            return build == null ? null : build.program;
        }

        @Nullable
        public ProgramFailure failure() {
            return build == null ? null : build.failure;
        }

        /** True when the role has a built program that hasn't been deleted. */
        public boolean ready() {
            ShaderProgram program = program();
            return program != null && !program.isDeleted();
        }
    }
}
