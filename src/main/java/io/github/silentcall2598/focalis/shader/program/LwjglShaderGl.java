// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.shader.program;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;

/** {@link ShaderGl} on LWJGL 2, which throws if no context is current on the calling thread. */
final class LwjglShaderGl implements ShaderGl {

    static final LwjglShaderGl INSTANCE = new LwjglShaderGl();

    private LwjglShaderGl() {
    }

    @Override
    public int createShader(int type) {
        return GL20.glCreateShader(type);
    }

    @Override
    public void shaderSource(int shader, String source) {
        GL20.glShaderSource(shader, source);
    }

    @Override
    public void compileShader(int shader) {
        GL20.glCompileShader(shader);
    }

    @Override
    public boolean compileSucceeded(int shader) {
        return GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == GL11.GL_TRUE;
    }

    @Override
    public String shaderLog(int shader) {
        int length = GL20.glGetShaderi(shader, GL20.GL_INFO_LOG_LENGTH);
        return length > 1 ? GL20.glGetShaderInfoLog(shader, length) : "";
    }

    @Override
    public void deleteShader(int shader) {
        GL20.glDeleteShader(shader);
    }

    @Override
    public int createProgram() {
        return GL20.glCreateProgram();
    }

    @Override
    public void attachShader(int program, int shader) {
        GL20.glAttachShader(program, shader);
    }

    @Override
    public void detachShader(int program, int shader) {
        GL20.glDetachShader(program, shader);
    }

    @Override
    public void linkProgram(int program) {
        GL20.glLinkProgram(program);
    }

    @Override
    public boolean linkSucceeded(int program) {
        return GL20.glGetProgrami(program, GL20.GL_LINK_STATUS) == GL11.GL_TRUE;
    }

    @Override
    public String programLog(int program) {
        int length = GL20.glGetProgrami(program, GL20.GL_INFO_LOG_LENGTH);
        return length > 1 ? GL20.glGetProgramInfoLog(program, length) : "";
    }

    @Override
    public void deleteProgram(int program) {
        GL20.glDeleteProgram(program);
    }
}
