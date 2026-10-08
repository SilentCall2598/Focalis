// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.shader.program;

import io.github.silentcall2598.focalis.shader.pack.IncludeException;
import io.github.silentcall2598.focalis.shader.pack.PackConfiguration;
import io.github.silentcall2598.focalis.shader.pack.ProgramSource;
import io.github.silentcall2598.focalis.shader.pack.ProgramStage;
import io.github.silentcall2598.focalis.shader.pack.ResolvedSource;
import io.github.silentcall2598.focalis.shader.pack.ShaderMacros;
import io.github.silentcall2598.focalis.shader.pack.ShaderPack;
import io.github.silentcall2598.focalis.shader.pack.ShaderPath;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/** The exact text that will be handed to the driver for each stage of one program. No OpenGL is involved yet. */
public final class PreparedProgram {

    private final String name;
    private final Map<ProgramStage, Stage> stages;

    PreparedProgram(String name, Map<ProgramStage, Stage> stages) {
        this.name = name;
        this.stages = Collections.unmodifiableMap(new EnumMap<>(stages));
    }

    /**
     * Expands a program's includes with the configuration's option values written into the pack's own definition
     * lines, then adds the environment defines to every stage.
     */
    public static PreparedProgram prepare(ShaderPack pack, ProgramSource program, ShaderMacros environment,
            PackConfiguration configuration) throws IncludeException {
        Map<ProgramStage, Stage> stages = new EnumMap<>(ProgramStage.class);
        for (ProgramStage stage : program.stages().keySet()) {
            ResolvedSource source = pack.resolve(program, stage, configuration).withDefines(environment,
                    ShaderMacros.empty());
            stages.put(stage, new Stage(stage, program.file(stage), source));
        }
        String name = program.directory().isEmpty() ? program.name() : program.directory() + "/" + program.name();
        return new PreparedProgram(name, stages);
    }

    /** The program name as users see it, like {@code world-1/composite}. */
    public String name() {
        return name;
    }

    public Map<ProgramStage, Stage> stages() {
        return stages;
    }

    public static final class Stage {

        private final ProgramStage stage;
        private final ShaderPath file;
        private final ResolvedSource source;

        Stage(ProgramStage stage, ShaderPath file, ResolvedSource source) {
            this.stage = stage;
            this.file = file;
            this.source = source;
        }

        public ProgramStage stage() {
            return stage;
        }

        public ShaderPath file() {
            return file;
        }

        public ResolvedSource source() {
            return source;
        }
    }
}
