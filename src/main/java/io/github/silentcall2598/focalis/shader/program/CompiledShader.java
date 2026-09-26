// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.shader.program;

/** A compiled stage shader. It only lives inside {@link ProgramBuilder#build}, which always deletes it. */
final class CompiledShader {

    private final ShaderGl gl;
    private final int id;
    private final PreparedProgram.Stage stage;
    private final String driverLog;
    private boolean deleted;

    CompiledShader(ShaderGl gl, int id, PreparedProgram.Stage stage, String driverLog) {
        this.gl = gl;
        this.id = id;
        this.stage = stage;
        this.driverLog = driverLog;
    }

    int id() {
        if (deleted) {
            throw new IllegalStateException(stage.file() + " was already deleted");
        }
        return id;
    }

    PreparedProgram.Stage stage() {
        return stage;
    }

    /** Warnings the driver reported while compiling successfully, or an empty string. */
    String driverLog() {
        return driverLog;
    }

    void delete() {
        if (!deleted) {
            deleted = true;
            gl.deleteShader(id);
        }
    }
}
