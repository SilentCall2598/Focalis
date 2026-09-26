// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.shader.program;

import io.github.silentcall2598.focalis.shader.pack.ProgramStage;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Compiles and links pack programs. Only the returned {@link ShaderProgram} outlives a build. Every stage shader is
 * deleted whether the build works or not.
 */
public final class ProgramBuilder {

    private final ShaderCapabilities capabilities;
    private final ShaderCompiler compiler;
    private final ProgramLinker linker;

    public ProgramBuilder(ShaderCapabilities capabilities) {
        this(capabilities, LwjglShaderGl.INSTANCE);
    }

    ProgramBuilder(ShaderCapabilities capabilities, ShaderGl gl) {
        this.capabilities = capabilities;
        this.compiler = new ShaderCompiler(gl);
        this.linker = new ProgramLinker(gl);
    }

    /**
     * Must run on the client thread with the OpenGL context current. Doesn't bind the program or change other GL
     * state.
     *
     * @throws ProgramBuildException if the pack's program can't be built. Anything else thrown is a Focalis problem.
     */
    public ShaderProgram build(PreparedProgram program) throws ProgramBuildException {
        checkStages(program);
        List<CompiledShader> compiled = new ArrayList<>();
        try {
            for (PreparedProgram.Stage stage : program.stages().values()) {
                compiled.add(compiler.compile(program.name(), stage));
            }
            return linker.link(program.name(), compiled);
        } finally {
            for (CompiledShader shader : compiled) {
                shader.delete();
            }
        }
    }

    // Runs before any OpenGL call, so a program that can't work never creates an object.
    private void checkStages(PreparedProgram program) throws ProgramBuildException {
        Set<ProgramStage> stages = program.stages().keySet();
        if (stages.contains(ProgramStage.COMPUTE)) {
            if (stages.size() > 1) {
                throw new ProgramBuildException(ProgramFailure.invalidStages(program.name(),
                        program.name() + " mixes a compute shader with other stages"));
            }
        } else if (!stages.contains(ProgramStage.VERTEX) || !stages.contains(ProgramStage.FRAGMENT)) {
            throw new ProgramBuildException(ProgramFailure.invalidStages(program.name(),
                    program.name() + " needs both a vertex and a fragment shader"));
        }
        for (PreparedProgram.Stage stage : program.stages().values()) {
            if (!capabilities.supports(stage.stage())) {
                throw new ProgramBuildException(ProgramFailure.unsupportedStage(program.name(), stage));
            }
        }
    }
}
