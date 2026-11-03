// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.shader.program;

import io.github.silentcall2598.focalis.shader.pack.CustomUniforms;
import io.github.silentcall2598.focalis.shader.pack.ProgramStates;
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
 * Each unique prepared program is built once, and every role that shares it shares the result. This set owns every
 * program it built and nothing else may delete or replace them. Nothing is freed automatically, so {@link #delete()}
 * has to be called.
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
     * No Focalis program scope may be open. A program the pack or driver can't build is recorded as a failure and the
     * others are still built. Any other exception deletes the programs built so far and is rethrown.
     */
    public static BuiltWorldPrograms build(PreparedWorldPrograms prepared, ShaderCapabilities capabilities) {
        return build(prepared, capabilities, ProgramTextures.NONE);
    }

    public static BuiltWorldPrograms build(PreparedWorldPrograms prepared, ShaderCapabilities capabilities,
            ProgramTextures textures) {
        Objects.requireNonNull(prepared, "prepared");
        Objects.requireNonNull(capabilities, "capabilities");
        return build(prepared, new ProgramBuilder(capabilities), textures);
    }

    static BuiltWorldPrograms build(PreparedWorldPrograms prepared, ProgramBuilder builder) {
        return build(prepared, builder, ProgramTextures.NONE);
    }

    static BuiltWorldPrograms build(PreparedWorldPrograms prepared, ProgramBuilder builder, ProgramTextures textures) {
        Objects.requireNonNull(prepared, "prepared");
        Objects.requireNonNull(textures, "textures");
        List<Build> builds = new ArrayList<>();
        try {
            // PreparedProgram has no equals, and roles that share a pack program share the exact object.
            Map<PreparedProgram, Build> byProgram = new IdentityHashMap<>();
            for (PreparedProgram program : prepared.uniquePrograms()) {
                Build build = Build.attempt(builder, program, prepared.customUniforms(), textures,
                        prepared.programStates());
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

    public List<Build> builds() {
        return builds;
    }

    public Entry forRole(ShaderProgramRole role) {
        return entries.get(Objects.requireNonNull(role, "role"));
    }

    public Map<ShaderProgramRole, Entry> entries() {
        return entries;
    }

    public boolean isDeleted() {
        return deleted;
    }

    /**
     * If a delete throws, the rest are still attempted and the first failure is rethrown afterwards. Builds and entries
     * stay readable afterwards.
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

    /** A built program always has its inputs. */
    public static final class Build {

        private final PreparedProgram prepared;
        @Nullable
        private final ShaderProgram program;
        @Nullable
        private final WorldProgramInputs inputs;
        @Nullable
        private final ProgramFailure failure;
        private final ProgramDrawState drawState;

        private Build(PreparedProgram prepared, @Nullable ShaderProgram program, @Nullable WorldProgramInputs inputs,
                @Nullable ProgramFailure failure, ProgramDrawState drawState) {
            this.prepared = prepared;
            this.program = program;
            this.inputs = inputs;
            this.failure = failure;
            this.drawState = drawState;
        }

        // The program is only owned by the set once this returns it, so anything failing before that deletes it.
        static Build attempt(ProgramBuilder builder, PreparedProgram prepared, CustomUniforms customs,
                ProgramTextures textures, ProgramStates states) {
            ShaderProgram program;
            try {
                program = builder.build(prepared);
            } catch (ProgramBuildException e) {
                return new Build(prepared, null, null, e.failure(), ProgramDrawState.NONE);
            }
            try {
                WorldProgramInputs inputs = WorldProgramInputs.connect(program.gl(), program, customs, textures);
                // Directives name the pack program, the same in every folder.
                String name = prepared.name().substring(prepared.name().lastIndexOf('/') + 1);
                ProgramDrawState drawState = ProgramDrawState.of(states.alphaTest(name), states.blend(name),
                        inputs.textureUnits(), inputs.textures());
                return new Build(prepared, program, inputs, null, drawState);
            } catch (ProgramBuildException e) {
                program.delete();
                return new Build(prepared, null, null, e.failure(), ProgramDrawState.NONE);
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

        @Nullable
        public ShaderProgram program() {
            return program;
        }

        @Nullable
        public WorldProgramInputs inputs() {
            return inputs;
        }

        @Nullable
        public ProgramFailure failure() {
            return failure;
        }

        public ProgramDrawState drawState() {
            return drawState;
        }

        public boolean succeeded() {
            return program != null;
        }
    }

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

        public PreparedWorldPrograms.Entry prepared() {
            return prepared;
        }

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

        public boolean ready() {
            ShaderProgram program = program();
            return program != null && !program.isDeleted();
        }
    }
}
