// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.shader.program;

import io.github.silentcall2598.focalis.shader.pack.ProgramStage;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL32;
import org.lwjgl.opengl.GL43;

/** Compiles one stage. The source goes to the driver exactly as prepared, nothing is patched here. */
final class ShaderCompiler {

    private final ShaderGl gl;

    ShaderCompiler(ShaderGl gl) {
        this.gl = gl;
    }

    static int glShaderType(ProgramStage stage) {
        switch (stage) {
            case VERTEX:
                return GL20.GL_VERTEX_SHADER;
            case GEOMETRY:
                return GL32.GL_GEOMETRY_SHADER;
            case FRAGMENT:
                return GL20.GL_FRAGMENT_SHADER;
            case COMPUTE:
                return GL43.GL_COMPUTE_SHADER;
            default:
                throw new IllegalArgumentException("No shader type for " + stage);
        }
    }

    /** Returns a shader the caller owns, or throws with the full driver log. A failed shader is always deleted. */
    CompiledShader compile(String programName, PreparedProgram.Stage stage) throws ProgramBuildException {
        int shader = gl.createShader(glShaderType(stage.stage()));
        if (shader == 0) {
            throw new IllegalStateException("The driver couldn't create a shader object for " + stage.file());
        }
        CompiledShader compiled = null;
        try {
            gl.shaderSource(shader, stage.source().text());
            gl.compileShader(shader);
            boolean succeeded = gl.compileSucceeded(shader);
            String log = gl.shaderLog(shader);
            if (!succeeded) {
                throw new ProgramBuildException(ProgramFailure.compile(programName, stage, log,
                        DriverLogLocator.locate(log, stage.source())));
            }
            compiled = new CompiledShader(gl, shader, stage, log);
            return compiled;
        } finally {
            if (compiled == null) {
                gl.deleteShader(shader);
            }
        }
    }
}
