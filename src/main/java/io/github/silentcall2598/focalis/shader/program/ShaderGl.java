// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.shader.program;

import java.util.Collections;
import java.util.List;

interface ShaderGl {

    /**
     * Binds a generic attribute name to a location, effective at the next link. Names a program doesn't declare are
     * allowed.
     */
    default void bindAttribLocation(int program, int location, String name) {
    }

    default List<ActiveAttribute> activeAttributes(int program) {
        return Collections.emptyList();
    }

    default int attribLocation(int program, String name) {
        return -1;
    }

    int createShader(int type);

    void shaderSource(int shader, String source);

    void compileShader(int shader);

    boolean compileSucceeded(int shader);

    String shaderLog(int shader);

    void deleteShader(int shader);

    int createProgram();

    void attachShader(int program, int shader);

    void detachShader(int program, int shader);

    void linkProgram(int program);

    boolean linkSucceeded(int program);

    String programLog(int program);

    void deleteProgram(int program);

    List<ActiveUniform> activeUniforms(int program);

    int uniformLocation(int program, String name);

    void uniform1i(int location, int value);

    void uniform2i(int location, int x, int y);

    void uniform1f(int location, float value);

    void uniform3f(int location, float x, float y, float z);

    void uniform4f(int location, float x, float y, float z, float w);

    void uniformMatrix4(int location, float[] columns);

    int currentProgram();

    void useProgram(int program);
}
