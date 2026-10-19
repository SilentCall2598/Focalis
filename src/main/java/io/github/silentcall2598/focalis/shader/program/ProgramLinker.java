// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.shader.program;

import io.github.silentcall2598.focalis.shader.terrain.StreamLayout;

import javax.annotation.Nullable;
import java.util.List;

/** Links compiled stages into a program. It never deletes the stage shaders, their owner does. */
final class ProgramLinker {

    private final ShaderGl gl;

    ProgramLinker(ShaderGl gl) {
        this.gl = gl;
    }

    /**
     * Returns a program the caller owns, or throws with the full driver log. A failed program is always deleted. The
     * names the chunk attribute stream feeds are bound to its locations first. If the driver won't link with them, the
     * program is linked again without, so it still draws everything that doesn't need the stream.
     */
    ShaderProgram link(String programName, List<CompiledShader> shaders) throws ProgramBuildException {
        Attempt bound = attempt(programName, shaders, true);
        if (bound.program != 0) {
            return finish(programName, shaders, bound, null);
        }
        // Some drivers alias generic locations with built-in attributes and refuse a collision at link time.
        Attempt plain = attempt(programName, shaders, false);
        if (plain.program == 0) {
            throw new ProgramBuildException(ProgramFailure.link(programName, plain.log,
                    DriverLogLocator.locate(plain.log, null)));
        }
        return finish(programName, shaders, plain, "the driver refused the stream's attribute locations: "
                + firstLine(bound.log));
    }

    private static final class Attempt {
        // 0 when linking failed, and the program is already deleted then.
        final int program;
        final String log;

        Attempt(int program, String log) {
            this.program = program;
            this.log = log;
        }
    }

    private Attempt attempt(String programName, List<CompiledShader> shaders, boolean bindStream) {
        int program = gl.createProgram();
        if (program == 0) {
            throw new IllegalStateException("The driver couldn't create a program object for " + programName);
        }
        boolean linked = false;
        try {
            for (CompiledShader shader : shaders) {
                gl.attachShader(program, shader.id());
            }
            if (bindStream) {
                gl.bindAttribLocation(program, StreamLayout.ENTITY_LOCATION, StreamLayout.ENTITY);
                gl.bindAttribLocation(program, StreamLayout.MID_TEX_COORD_LOCATION, StreamLayout.MID_TEX_COORD);
                gl.bindAttribLocation(program, StreamLayout.TANGENT_LOCATION, StreamLayout.TANGENT);
            }
            gl.linkProgram(program);
            linked = gl.linkSucceeded(program);
            String log = gl.programLog(program);
            // A linked program doesn't need its stage shaders anymore, so they're detached and can be freed.
            for (CompiledShader shader : shaders) {
                gl.detachShader(program, shader.id());
            }
            return new Attempt(linked ? program : 0, log);
        } finally {
            if (!linked) {
                gl.deleteProgram(program);
            }
        }
    }

    private ShaderProgram finish(String programName, List<CompiledShader> shaders, Attempt attempt,
            @Nullable String refused) {
        int program = attempt.program;
        ShaderProgram linked = null;
        try {
            VertexInputs inputs = VertexInputs.read(gl.activeAttributes(program),
                    name -> gl.attribLocation(program, name), refused);
            linked = new ShaderProgram(gl, program, programName, combinedLog(shaders, attempt.log), inputs);
            return linked;
        } finally {
            if (linked == null) {
                gl.deleteProgram(program);
            }
        }
    }

    private static String firstLine(String log) {
        String trimmed = log.trim();
        int end = trimmed.indexOf('\n');
        return end < 0 ? trimmed : trimmed.substring(0, end).trim();
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
