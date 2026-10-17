// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.shader.terrain;

import org.lwjgl.BufferUtils;
import org.lwjgl.MemoryUtil;
import org.lwjgl.PointerBuffer;
import org.lwjgl.opengl.ARBInstancedArrays;
import org.lwjgl.opengl.ContextCapabilities;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL33;
import org.lwjgl.opengl.GLContext;

import javax.annotation.Nullable;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

/** The real calls. Its scratch buffers are made on first use and reused, so client thread only. */
final class LwjglAttributeGl implements AttributeGl {

    static final LwjglAttributeGl INSTANCE = new LwjglAttributeGl();

    private static final int INTEGER = 1;
    private static final int DIVISOR = 2;
    private static final int CORE_DIVISOR = 4;

    private IntBuffer ints;
    // glGetFloat checks for room for 16 values whatever it reads.
    private FloatBuffer floats;
    private ByteBuffer pointer;
    // Read on first use, when a context is current.
    private int features = -1;

    private LwjglAttributeGl() {
    }

    @Override
    public int arrayBuffer() {
        return GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
    }

    @Override
    public void bindArrayBuffer(int buffer) {
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, buffer);
    }

    @Override
    public int createBuffer() {
        return GL15.glGenBuffers();
    }

    @Override
    public void deleteBuffer(int buffer) {
        GL15.glDeleteBuffers(buffer);
    }

    @Override
    public void bufferData(ByteBuffer data) {
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, data, GL15.GL_STATIC_DRAW);
    }

    @Override
    public boolean normalArrayEnabled() {
        return GL11.glIsEnabled(GL11.GL_NORMAL_ARRAY);
    }

    @Override
    public void normalArray(boolean enabled) {
        if (enabled) {
            GL11.glEnableClientState(GL11.GL_NORMAL_ARRAY);
        } else {
            GL11.glDisableClientState(GL11.GL_NORMAL_ARRAY);
        }
    }

    @Override
    public int normalArrayType() {
        return GL11.glGetInteger(GL11.GL_NORMAL_ARRAY_TYPE);
    }

    @Override
    public int normalArrayStride() {
        return GL11.glGetInteger(GL11.GL_NORMAL_ARRAY_STRIDE);
    }

    @Override
    public int normalArrayBuffer() {
        return GL11.glGetInteger(GL15.GL_NORMAL_ARRAY_BUFFER_BINDING);
    }

    @Override
    public long normalArrayPointer() {
        ByteBuffer at = GL11.glGetPointer(GL11.GL_NORMAL_ARRAY_POINTER, 1);
        return at == null ? 0L : MemoryUtil.getAddress(at);
    }

    @Nullable
    @Override
    public ByteBuffer normalArrayClientPointer() {
        return GL11.glGetPointer(GL11.GL_NORMAL_ARRAY_POINTER, 1);
    }

    @Override
    public void normalPointer(int type, int stride, long offset) {
        GL11.glNormalPointer(type, stride, offset);
    }

    @Override
    public void normalPointer(int type, int stride, ByteBuffer client) {
        GL11.glNormalPointer(type, stride, client);
    }

    @Override
    public void currentNormal(float[] into) {
        FloatBuffer values = floats();
        values.clear();
        GL11.glGetFloat(GL11.GL_CURRENT_NORMAL, values);
        values.get(into, 0, 3);
    }

    @Override
    public void setCurrentNormal(float x, float y, float z) {
        GL11.glNormal3f(x, y, z);
    }

    @Override
    public boolean attribArrayEnabled(int index) {
        return attribInt(index, GL20.GL_VERTEX_ATTRIB_ARRAY_ENABLED) != 0;
    }

    @Override
    public void attribArray(int index, boolean enabled) {
        if (enabled) {
            GL20.glEnableVertexAttribArray(index);
        } else {
            GL20.glDisableVertexAttribArray(index);
        }
    }

    @Override
    public int attribArraySize(int index) {
        return attribInt(index, GL20.GL_VERTEX_ATTRIB_ARRAY_SIZE);
    }

    @Override
    public int attribArrayType(int index) {
        return attribInt(index, GL20.GL_VERTEX_ATTRIB_ARRAY_TYPE);
    }

    @Override
    public boolean attribArrayNormalized(int index) {
        return attribInt(index, GL20.GL_VERTEX_ATTRIB_ARRAY_NORMALIZED) != 0;
    }

    @Override
    public int attribArrayStride(int index) {
        return attribInt(index, GL20.GL_VERTEX_ATTRIB_ARRAY_STRIDE);
    }

    @Override
    public int attribArrayBuffer(int index) {
        return attribInt(index, GL15.GL_VERTEX_ATTRIB_ARRAY_BUFFER_BINDING);
    }

    @Override
    public boolean attribArrayInteger(int index) {
        return (features() & INTEGER) != 0 && attribInt(index, GL30.GL_VERTEX_ATTRIB_ARRAY_INTEGER) != 0;
    }

    @Override
    public int attribArrayDivisor(int index) {
        return (features() & DIVISOR) != 0 ? attribInt(index, GL33.GL_VERTEX_ATTRIB_ARRAY_DIVISOR) : 0;
    }

    @Override
    public long attribArrayPointer(int index) {
        ByteBuffer address = pointer();
        address.clear();
        GL20.glGetVertexAttribPointer(index, GL20.GL_VERTEX_ATTRIB_ARRAY_POINTER, address);
        return PointerBuffer.is64Bit() ? address.getLong(0) : address.getInt(0) & 0xFFFFFFFFL;
    }

    @Nullable
    @Override
    public ByteBuffer attribArrayClientPointer(int index) {
        return GL20.glGetVertexAttribPointer(index, GL20.GL_VERTEX_ATTRIB_ARRAY_POINTER, 1);
    }

    @Override
    public void attribPointer(int index, int size, int type, boolean normalized, int stride, long offset) {
        GL20.glVertexAttribPointer(index, size, type, normalized, stride, offset);
    }

    @Override
    public void attribPointer(int index, int size, int type, boolean normalized, int stride, ByteBuffer client) {
        GL20.glVertexAttribPointer(index, size, type, normalized, stride, client);
    }

    @Override
    public void attribIntegerPointer(int index, int size, int type, int stride, long offset) {
        GL30.glVertexAttribIPointer(index, size, type, stride, offset);
    }

    @Override
    public void attribIntegerPointer(int index, int size, int type, int stride, ByteBuffer client) {
        GL30.glVertexAttribIPointer(index, size, type, stride, client);
    }

    @Override
    public void attribDivisor(int index, int divisor) {
        if ((features() & CORE_DIVISOR) != 0) {
            GL33.glVertexAttribDivisor(index, divisor);
        } else {
            ARBInstancedArrays.glVertexAttribDivisorARB(index, divisor);
        }
    }

    @Override
    public void currentAttrib(int index, float[] into, int offset) {
        FloatBuffer values = floats();
        values.clear();
        GL20.glGetVertexAttrib(index, GL20.GL_CURRENT_VERTEX_ATTRIB, values);
        values.get(into, offset, 4);
    }

    @Override
    public void setCurrentAttrib(int index, float x, float y, float z, float w) {
        GL20.glVertexAttrib4f(index, x, y, z, w);
    }

    private int attribInt(int index, int name) {
        IntBuffer values = ints();
        values.clear();
        GL20.glGetVertexAttrib(index, name, values);
        return values.get(0);
    }

    private IntBuffer ints() {
        if (ints == null) {
            ints = BufferUtils.createIntBuffer(16);
        }
        return ints;
    }

    private FloatBuffer floats() {
        if (floats == null) {
            floats = BufferUtils.createFloatBuffer(16);
        }
        return floats;
    }

    private ByteBuffer pointer() {
        if (pointer == null) {
            pointer = BufferUtils.createByteBuffer(8);
        }
        return pointer;
    }

    private int features() {
        int known = features;
        if (known < 0) {
            ContextCapabilities caps = GLContext.getCapabilities();
            known = (caps.OpenGL30 ? INTEGER : 0) | (caps.OpenGL33 ? DIVISOR | CORE_DIVISOR : 0)
                    | (caps.GL_ARB_instanced_arrays ? DIVISOR : 0);
            features = known;
        }
        return known;
    }
}
