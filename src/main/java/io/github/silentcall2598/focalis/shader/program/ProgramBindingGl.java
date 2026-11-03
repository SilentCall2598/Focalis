// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.shader.program;

interface ProgramBindingGl {

    int currentProgram();

    void useProgram(int program);
}
