// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.shader.terrain;

import javax.annotation.Nullable;
import java.nio.ByteBuffer;

/**
 * The GL calls the stream makes on vertex arrays and buffers, so the state handling can be tested without a context.
 * Client thread only, with the context current.
 */
interface AttributeGl {

    int arrayBuffer();

    void bindArrayBuffer(int buffer);

    int createBuffer();

    void deleteBuffer(int buffer);

    /** Uploads to the buffer bound to GL_ARRAY_BUFFER, as static draw data. */
    void bufferData(ByteBuffer data);

    /** The bytes the driver kept for the buffer bound to GL_ARRAY_BUFFER, 0 when it never made a data store. */
    int arrayBufferSize();

    /** Gives the buffer bound to GL_ARRAY_BUFFER an empty data store, so nothing it held is left. */
    void emptyArrayBuffer();

    boolean normalArrayEnabled();

    void normalArray(boolean enabled);

    int normalArrayType();

    int normalArrayStride();

    int normalArrayBuffer();

    /** The normal array's pointer, an offset when its buffer isn't 0. 0 for a null pointer. */
    long normalArrayPointer();

    /** The same pointer as a buffer to hand back to GL, or null for a null pointer. Allocates. */
    @Nullable
    ByteBuffer normalArrayClientPointer();

    void normalPointer(int type, int stride, long offset);

    void normalPointer(int type, int stride, ByteBuffer client);

    void currentNormal(float[] into);

    void setCurrentNormal(float x, float y, float z);

    boolean attribArrayEnabled(int index);

    void attribArray(int index, boolean enabled);

    int attribArraySize(int index);

    int attribArrayType(int index);

    boolean attribArrayNormalized(int index);

    int attribArrayStride(int index);

    int attribArrayBuffer(int index);

    /** Whether the array was set up with glVertexAttribIPointer. False without GL 3.0. */
    boolean attribArrayInteger(int index);

    /** The instancing divisor. 0 without instanced arrays. */
    int attribArrayDivisor(int index);

    long attribArrayPointer(int index);

    /** The same pointer as a buffer to hand back to GL, or null for a null pointer. Allocates. */
    @Nullable
    ByteBuffer attribArrayClientPointer(int index);

    void attribPointer(int index, int size, int type, boolean normalized, int stride, long offset);

    void attribPointer(int index, int size, int type, boolean normalized, int stride, ByteBuffer client);

    void attribIntegerPointer(int index, int size, int type, int stride, long offset);

    void attribIntegerPointer(int index, int size, int type, int stride, ByteBuffer client);

    void attribDivisor(int index, int divisor);

    void currentAttrib(int index, float[] into, int offset);

    void setCurrentAttrib(int index, float x, float y, float z, float w);
}
