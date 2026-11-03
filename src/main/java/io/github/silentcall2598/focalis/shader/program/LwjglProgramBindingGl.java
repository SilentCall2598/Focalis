// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.shader.program;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;

final class LwjglProgramBindingGl implements ProgramBindingGl {

    static final LwjglProgramBindingGl INSTANCE = new LwjglProgramBindingGl();

    private LwjglProgramBindingGl() {
    }

    @Override
    public int currentProgram() {
        return GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
    }

    @Override
    public void useProgram(int program) {
        GL20.glUseProgram(program);
    }
}
