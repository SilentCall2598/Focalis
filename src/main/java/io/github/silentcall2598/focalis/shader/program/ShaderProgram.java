// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.shader.program;

/**
 * A linked OpenGL program. Whoever receives it owns it and has to call {@link #delete()} on the client thread
 * while a context is current. Nothing frees it automatically.
 */
public final class ShaderProgram {

    private final ShaderGl gl;
    private final int id;
    private final String name;
    private final String driverLog;
    private boolean deleted;

    ShaderProgram(ShaderGl gl, int id, String name, String driverLog) {
        this.gl = gl;
        this.id = id;
        this.name = name;
        this.driverLog = driverLog;
    }

    /** The OpenGL program name. Throws once the program has been deleted. */
    public int id() {
        if (deleted) {
            throw new IllegalStateException(name + " was already deleted");
        }
        return id;
    }

    public String name() {
        return name;
    }

    /** Warnings the driver reported while building the program, grouped by file, or an empty string. */
    public String driverLog() {
        return driverLog;
    }

    public boolean isDeleted() {
        return deleted;
    }

    /** Deletes the program. Calling it again does nothing. */
    public void delete() {
        if (!deleted) {
            deleted = true;
            gl.deleteProgram(id);
        }
    }
}
