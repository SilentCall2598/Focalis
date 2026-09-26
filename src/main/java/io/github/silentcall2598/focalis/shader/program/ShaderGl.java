// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.shader.program;

/**
 * The OpenGL calls this package makes, nothing more. Every method needs a current context on the calling thread.
 * Tests swap in a recording version to check that failures never leak objects.
 */
interface ShaderGl {

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
}
