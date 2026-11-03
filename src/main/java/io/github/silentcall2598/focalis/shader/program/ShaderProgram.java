// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.shader.program;

/**
 * Whoever receives it owns it and has to call {@link #delete()} on the client thread while a context is current.
 * Nothing frees it automatically.
 */
public final class ShaderProgram {

    private final ShaderGl gl;
    private final int id;
    private final String name;
    private final String driverLog;
    private final VertexInputs vertexInputs;
    private boolean deleted;

    ShaderProgram(ShaderGl gl, int id, String name, String driverLog) {
        this(gl, id, name, driverLog, VertexInputs.NONE);
    }

    ShaderProgram(ShaderGl gl, int id, String name, String driverLog, VertexInputs vertexInputs) {
        this.gl = gl;
        this.id = id;
        this.name = name;
        this.driverLog = driverLog;
        this.vertexInputs = vertexInputs;
    }

    public VertexInputs vertexInputs() {
        return vertexInputs;
    }

    public int id() {
        if (deleted) {
            throw new IllegalStateException(name + " was already deleted");
        }
        return id;
    }

    public String name() {
        return name;
    }

    ShaderGl gl() {
        return gl;
    }

    public String driverLog() {
        return driverLog;
    }

    public boolean isDeleted() {
        return deleted;
    }

    public void delete() {
        if (!deleted) {
            deleted = true;
            gl.deleteProgram(id);
        }
    }
}
