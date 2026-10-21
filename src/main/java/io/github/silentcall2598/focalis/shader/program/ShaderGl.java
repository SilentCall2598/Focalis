// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.shader.program;

import java.util.Collections;
import java.util.List;

/**
 * The OpenGL calls this package makes, nothing more. Every method needs a current context on the calling thread.
 */
interface ShaderGl {

    /**
     * Binds a generic attribute name to a location, effective at the next link. Names a program doesn't declare are
     * allowed. Recording fakes may leave this out.
     */
    default void bindAttribLocation(int program, int location, String name) {
    }

    /** Every active attribute of a linked program, built-in gl_ ones included. */
    default List<ActiveAttribute> activeAttributes(int program) {
        return Collections.emptyList();
    }

    /** Returns -1 when the linked program has no active generic attribute with that name. */
    default int attribLocation(int program, String name) {
        return -1;
    }

    /** Returns 0 if the driver couldn't create the shader. */
    int createShader(int type);

    void shaderSource(int shader, String source);

    void compileShader(int shader);

    boolean compileSucceeded(int shader);

    /** The complete info log, or an empty string when the driver said nothing. */
    String shaderLog(int shader);

    void deleteShader(int shader);

    /** Returns 0 if the driver couldn't create the program. */
    int createProgram();

    void attachShader(int program, int shader);

    void detachShader(int program, int shader);

    void linkProgram(int program);

    boolean linkSucceeded(int program);

    /** The complete info log, or an empty string when the driver said nothing. */
    String programLog(int program);

    void deleteProgram(int program);

    /** Every active uniform of a linked program. Some drivers list built-in {@code gl_} uniforms too. */
    List<ActiveUniform> activeUniforms(int program);

    /** Returns -1 when the program has no active uniform with that name. */
    int uniformLocation(int program, String name);

    /** Sets an int or sampler uniform of the program that is current right now. */
    void uniform1i(int location, int value);

    /** Sets an ivec2 uniform of the program that is current right now. */
    void uniform2i(int location, int x, int y);

    /** Sets a float uniform of the program that is current right now. */
    void uniform1f(int location, float value);

    /** Sets a vec3 uniform of the program that is current right now. */
    void uniform3f(int location, float x, float y, float z);

    /** Sets a vec4 uniform of the program that is current right now. */
    void uniform4f(int location, float x, float y, float z, float w);

    /**
     * Sets a mat4 uniform of the program that is current right now. The 16 values are column by column, the order
     * OpenGL returns matrices in, and are sent without transposing.
     */
    void uniformMatrix4(int location, float[] columns);

    /** The program name GL_CURRENT_PROGRAM reports, 0 when none is active. */
    int currentProgram();

    void useProgram(int program);
}
