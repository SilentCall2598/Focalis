// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.shader.program;

/** The two OpenGL calls program binding needs. Both need a current context on the calling thread. */
interface ProgramBindingGl {

    /** The program name GL_CURRENT_PROGRAM reports, 0 when none is active. */
    int currentProgram();

    void useProgram(int program);
}
