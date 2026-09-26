// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.shader.program;

import java.util.List;

/** Links compiled stages into a program. It never deletes the stage shaders, their owner does. */
final class ProgramLinker {

    private final ShaderGl gl;

    ProgramLinker(ShaderGl gl) {
        this.gl = gl;
    }

    /** Returns a program the caller owns, or throws with the full driver log. A failed program is always deleted. */
    ShaderProgram link(String programName, List<CompiledShader> shaders) throws ProgramBuildException {
        int program = gl.createProgram();
        if (program == 0) {
            throw new IllegalStateException("The driver couldn't create a program object for " + programName);
        }
        ShaderProgram linked = null;
        try {
            for (CompiledShader shader : shaders) {
                gl.attachShader(program, shader.id());
            }
            gl.linkProgram(program);
            boolean succeeded = gl.linkSucceeded(program);
            String log = gl.programLog(program);
            // A linked program doesn't need its stage shaders anymore, so they're detached and can be freed.
            for (CompiledShader shader : shaders) {
                gl.detachShader(program, shader.id());
            }
            if (!succeeded) {
                throw new ProgramBuildException(ProgramFailure.link(programName, log,
                        DriverLogLocator.locate(log, null)));
            }
            linked = new ShaderProgram(gl, program, programName, combinedLog(shaders, log));
            return linked;
        } finally {
            if (linked == null) {
                gl.deleteProgram(program);
            }
        }
    }

    private static String combinedLog(List<CompiledShader> shaders, String linkLog) {
        StringBuilder log = new StringBuilder();
        for (CompiledShader shader : shaders) {
            append(log, shader.stage().file().toString(), shader.driverLog());
        }
        append(log, "link", linkLog);
        return log.toString();
    }

    private static void append(StringBuilder log, String heading, String text) {
        if (!text.trim().isEmpty()) {
            log.append(heading).append(":\n").append(text);
            if (!text.endsWith("\n")) {
                log.append('\n');
            }
        }
    }
}
