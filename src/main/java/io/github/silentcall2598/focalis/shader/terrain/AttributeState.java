// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.shader.terrain;

import javax.annotation.Nullable;
import java.nio.ByteBuffer;

final class AttributeState {

    static final int[] GENERIC = {StreamLayout.TANGENT_LOCATION, StreamLayout.ENTITY_LOCATION,
            StreamLayout.MID_TEX_COORD_LOCATION};

    private boolean normalEnabled;
    private int normalType;
    private int normalStride;
    private int normalBuffer;
    private long normalOffset;
    @Nullable
    private ByteBuffer normalClient;

    private boolean colorCaptured;
    private boolean colorEnabled;
    private int colorSize;
    private int colorType;
    private int colorStride;
    private int colorBuffer;
    private long colorOffset;
    @Nullable
    private ByteBuffer colorClient;

    private final boolean[] enabled = new boolean[GENERIC.length];
    private final int[] size = new int[GENERIC.length];
    private final int[] type = new int[GENERIC.length];
    private final boolean[] normalized = new boolean[GENERIC.length];
    private final boolean[] integer = new boolean[GENERIC.length];
    private final int[] stride = new int[GENERIC.length];
    private final int[] buffer = new int[GENERIC.length];
    private final int[] divisor = new int[GENERIC.length];
    private final long[] offset = new long[GENERIC.length];
    private final ByteBuffer[] client = new ByteBuffer[GENERIC.length];

    private final float[] normal = new float[3];
    private final float[] current = new float[GENERIC.length * 4];

    void captureArrays(AttributeGl gl) {
        colorCaptured = false;
        normalEnabled = gl.normalArrayEnabled();
        normalType = gl.normalArrayType();
        normalStride = gl.normalArrayStride();
        normalBuffer = gl.normalArrayBuffer();
        if (normalBuffer != 0) {
            normalOffset = gl.normalArrayPointer();
            normalClient = null;
        } else {
            normalOffset = 0L;
            normalClient = gl.normalArrayClientPointer();
        }
        for (int slot = 0; slot < GENERIC.length; slot++) {
            int index = GENERIC[slot];
            enabled[slot] = gl.attribArrayEnabled(index);
            size[slot] = gl.attribArraySize(index);
            type[slot] = gl.attribArrayType(index);
            normalized[slot] = gl.attribArrayNormalized(index);
            integer[slot] = gl.attribArrayInteger(index);
            stride[slot] = gl.attribArrayStride(index);
            buffer[slot] = gl.attribArrayBuffer(index);
            divisor[slot] = gl.attribArrayDivisor(index);
            long pointer = gl.attribArrayPointer(index);
            if (buffer[slot] != 0 || pointer == 0L) {
                offset[slot] = pointer;
                client[slot] = null;
            } else {
                offset[slot] = 0L;
                client[slot] = gl.attribArrayClientPointer(index);
            }
        }
    }

    void captureColor(AttributeGl gl) {
        colorEnabled = gl.colorArrayEnabled();
        colorSize = gl.colorArraySize();
        colorType = gl.colorArrayType();
        colorStride = gl.colorArrayStride();
        colorBuffer = gl.colorArrayBuffer();
        if (colorBuffer != 0) {
            colorOffset = gl.colorArrayPointer();
            colorClient = null;
        } else {
            colorOffset = 0L;
            colorClient = gl.colorArrayClientPointer();
        }
        colorCaptured = true;
    }

    void endColorOn(int buffer) {
        if (colorCaptured && colorBuffer != 0 && buffer > 0) {
            colorBuffer = buffer;
        }
    }

    void captureCurrent(AttributeGl gl) {
        gl.currentNormal(normal);
        for (int slot = 0; slot < GENERIC.length; slot++) {
            gl.currentAttrib(GENERIC[slot], current, slot * 4);
        }
    }

    boolean hadDivisor(int slot) {
        return divisor[slot] != 0;
    }

    /**
     * A null pointer without a buffer can't be set directly, so the array points at a scratch buffer that's deleted
     * at the end, which resets it to buffer 0.
     */
    void restoreArrays(AttributeGl gl) {
        restore(gl, gl.arrayBuffer(), false);
    }

    void restoreForDraw(AttributeGl gl, int bound) {
        restore(gl, bound, true);
    }

    private void restore(AttributeGl gl, int bound, boolean forDraw) {
        int active = bound;
        int scratch = 0;
        boolean restored = false;
        try {
            if (needsScratch(forDraw)) {
                scratch = gl.createBuffer();
                if (scratch == 0) {
                    throw new IllegalStateException("The driver gave no buffer to put the vertex arrays back with");
                }
            }
            if (forDraw && !normalEnabled) {
                gl.normalArray(false);
            } else {
                if (normalBuffer != 0) {
                    active = bind(gl, normalBuffer, active);
                    gl.normalPointer(normalType, normalStride, normalOffset);
                } else if (normalClient != null) {
                    active = bind(gl, 0, active);
                    gl.normalPointer(normalType, normalStride, normalClient);
                } else {
                    active = bind(gl, scratch, active);
                    gl.normalPointer(normalType, normalStride, 0L);
                }
                gl.normalArray(normalEnabled);
            }
            for (int slot = 0; slot < GENERIC.length; slot++) {
                int index = GENERIC[slot];
                if (forDraw && !enabled[slot]) {
                    gl.attribArray(index, false);
                    continue;
                }
                ByteBuffer clientPointer = client[slot];
                if (buffer[slot] != 0 || clientPointer == null) {
                    active = bind(gl, buffer[slot] != 0 ? buffer[slot] : scratch, active);
                    if (integer[slot]) {
                        gl.attribIntegerPointer(index, size[slot], type[slot], stride[slot], offset[slot]);
                    } else {
                        gl.attribPointer(index, size[slot], type[slot], normalized[slot], stride[slot], offset[slot]);
                    }
                } else {
                    active = bind(gl, 0, active);
                    if (integer[slot]) {
                        gl.attribIntegerPointer(index, size[slot], type[slot], stride[slot], clientPointer);
                    } else {
                        gl.attribPointer(index, size[slot], type[slot], normalized[slot], stride[slot], clientPointer);
                    }
                }
                gl.attribArray(index, enabled[slot]);
                if (divisor[slot] != 0) {
                    gl.attribDivisor(index, divisor[slot]);
                }
            }
            if (colorCaptured && forDraw) {
                if (!colorEnabled) {
                    gl.colorArray(false);
                }
            } else if (colorCaptured) {
                if (colorBuffer != 0 || colorClient == null) {
                    active = bind(gl, colorBuffer != 0 ? colorBuffer : scratch, active);
                    gl.colorPointer(colorSize, colorType, colorStride, colorOffset);
                } else {
                    active = bind(gl, 0, active);
                    gl.colorPointer(colorSize, colorType, colorStride, colorClient);
                }
                gl.colorArray(colorEnabled);
            }
            restored = true;
        } finally {
            try {
                bind(gl, bound, active);
                if (scratch != 0) {
                    gl.deleteBuffer(scratch);
                }
            } finally {
                if (!forDraw) {
                    normalClient = null;
                    colorClient = null;
                    for (int slot = 0; slot < GENERIC.length; slot++) {
                        client[slot] = null;
                    }
                }
                if (!restored) {
                    turnOff(gl);
                }
            }
        }
    }

    private boolean needsScratch(boolean forDraw) {
        if ((!forDraw || normalEnabled) && normalBuffer == 0 && normalClient == null) {
            return true;
        }
        if (colorCaptured && !forDraw && colorBuffer == 0 && colorClient == null) {
            return true;
        }
        for (int slot = 0; slot < GENERIC.length; slot++) {
            if ((!forDraw || enabled[slot]) && buffer[slot] == 0 && client[slot] == null) {
                return true;
            }
        }
        return false;
    }

    private static void turnOff(AttributeGl gl) {
        try {
            StreamArrays.enable(gl, false);
        } catch (RuntimeException | LinkageError e) {
        }
    }

    void restoreCurrent(AttributeGl gl) {
        gl.setCurrentNormal(normal[0], normal[1], normal[2]);
        for (int slot = 0; slot < GENERIC.length; slot++) {
            int at = slot * 4;
            gl.setCurrentAttrib(GENERIC[slot], current[at], current[at + 1], current[at + 2], current[at + 3]);
        }
    }

    private static int bind(AttributeGl gl, int buffer, int current) {
        if (buffer != current) {
            gl.bindArrayBuffer(buffer);
        }
        return buffer;
    }
}
