// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.shader.program;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;

/** {@link ShaderGl} on LWJGL 2, which throws if no context is current on the calling thread. */
final class LwjglShaderGl implements ShaderGl {

    static final LwjglShaderGl INSTANCE = new LwjglShaderGl();

    // Reused for every matrix upload, which is fine since everything here runs on the client thread.
    private final FloatBuffer matrix = BufferUtils.createFloatBuffer(16);

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

    @Override
    public List<ActiveUniform> activeUniforms(int program) {
        int count = GL20.glGetProgrami(program, GL20.GL_ACTIVE_UNIFORMS);
        int maxLength = GL20.glGetProgrami(program, GL20.GL_ACTIVE_UNIFORM_MAX_LENGTH);
        IntBuffer sizeAndType = BufferUtils.createIntBuffer(2);
        List<ActiveUniform> uniforms = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            String name = GL20.glGetActiveUniform(program, i, maxLength, sizeAndType);
            uniforms.add(new ActiveUniform(name, sizeAndType.get(1), sizeAndType.get(0)));
        }
        return uniforms;
    }

    @Override
    public int uniformLocation(int program, String name) {
        return GL20.glGetUniformLocation(program, name);
    }

    @Override
    public void uniform1i(int location, int value) {
        GL20.glUniform1i(location, value);
    }

    @Override
    public void uniform1f(int location, float value) {
        GL20.glUniform1f(location, value);
    }

    @Override
    public void uniform3f(int location, float x, float y, float z) {
        GL20.glUniform3f(location, x, y, z);
    }

    @Override
    public void uniformMatrix4(int location, float[] columns) {
        FloatBuffer buffer = matrix;
        buffer.clear();
        buffer.put(columns, 0, 16);
        buffer.flip();
        GL20.glUniformMatrix4(location, false, buffer);
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
