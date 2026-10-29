// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.shader.terrain;

import javax.annotation.Nullable;
import java.nio.ByteBuffer;

interface AttributeGl {

    int arrayBuffer();

    void bindArrayBuffer(int buffer);

    int createBuffer();

    void deleteBuffer(int buffer);

    void bufferData(ByteBuffer data);

    int arrayBufferSize();

    void emptyArrayBuffer();

    boolean normalArrayEnabled();

    void normalArray(boolean enabled);

    int normalArrayType();

    int normalArrayStride();

    int normalArrayBuffer();

    long normalArrayPointer();

    @Nullable
    ByteBuffer normalArrayClientPointer();

    void normalPointer(int type, int stride, long offset);

    void normalPointer(int type, int stride, ByteBuffer client);

    void currentNormal(float[] into);

    void setCurrentNormal(float x, float y, float z);

    boolean colorArrayEnabled();

    void colorArray(boolean enabled);

    int colorArraySize();

    int colorArrayType();

    int colorArrayStride();

    int colorArrayBuffer();

    long colorArrayPointer();

    @Nullable
    ByteBuffer colorArrayClientPointer();

    void colorPointer(int size, int type, int stride, long offset);

    void colorPointer(int size, int type, int stride, ByteBuffer client);

    boolean attribArrayEnabled(int index);

    void attribArray(int index, boolean enabled);

    int attribArraySize(int index);

    int attribArrayType(int index);

    boolean attribArrayNormalized(int index);

    int attribArrayStride(int index);

    int attribArrayBuffer(int index);

    boolean attribArrayInteger(int index);

    int attribArrayDivisor(int index);

    long attribArrayPointer(int index);

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
