// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.shader.terrain;

import javax.annotation.Nullable;
import java.nio.ByteBuffer;

/**
 * The vertex array state the stream changes: the normal array and the generic arrays at 1, 6 and 7 with their buffers,
 * pointers, enables and divisors, and separately the current normal and generic values. Captured right before the
 * stream first changes it and put back exactly, so arrays left enabled or pointing somewhere unusual stay that way.
 * Client thread only.
 */
final class AttributeState {

    /** The generic locations the stream feeds, in slot order. */
    static final int[] GENERIC = {StreamLayout.TANGENT_LOCATION, StreamLayout.ENTITY_LOCATION,
            StreamLayout.MID_TEX_COORD_LOCATION};

    private boolean normalEnabled;
    private int normalType;
    private int normalStride;
    private int normalBuffer;
    private long normalOffset;
    @Nullable
    private ByteBuffer normalClient;

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
                // Something left a generic array on client memory. Only a buffer at that address can point back.
                offset[slot] = 0L;
                client[slot] = gl.attribArrayClientPointer(index);
            }
        }
    }

    void captureCurrent(AttributeGl gl) {
        gl.currentNormal(normal);
        for (int slot = 0; slot < GENERIC.length; slot++) {
            gl.currentAttrib(GENERIC[slot], current, slot * 4);
        }
    }

    /** Whether the generic array in {@code slot} had an instancing divisor, which the stream has to clear. */
    boolean hadDivisor(int slot) {
        return divisor[slot] != 0;
    }

    /**
     * Puts the arrays back and leaves GL_ARRAY_BUFFER bound to what it is now. A null pointer without a buffer can't
     * be set directly, so the array points at a scratch buffer that's deleted at the end, which resets it to buffer 0.
     * The scratch buffer is made before anything changes, and always deleted. If the arrays can't all be put back, the
     * stream's arrays are turned off, since half of them pointing at the stream is worse than off.
     */
    void restoreArrays(AttributeGl gl) {
        int bound = gl.arrayBuffer();
        int active = bound;
        int scratch = 0;
        boolean restored = false;
        try {
            if (needsScratch()) {
                scratch = gl.createBuffer();
                if (scratch == 0) {
                    throw new IllegalStateException("The driver gave no buffer to put the vertex arrays back with");
                }
            }
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
            for (int slot = 0; slot < GENERIC.length; slot++) {
                int index = GENERIC[slot];
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
            restored = true;
        } finally {
            try {
                bind(gl, bound, active);
                if (scratch != 0) {
                    gl.deleteBuffer(scratch);
                }
            } finally {
                // Nothing to keep alive past this point.
                normalClient = null;
                for (int slot = 0; slot < GENERIC.length; slot++) {
                    client[slot] = null;
                }
                if (!restored) {
                    turnOff(gl);
                }
            }
        }
    }

    // Whether any array was on no buffer and on no client memory, which only a scratch buffer can restore.
    private boolean needsScratch() {
        if (normalBuffer == 0 && normalClient == null) {
            return true;
        }
        for (int slot = 0; slot < GENERIC.length; slot++) {
            if (buffer[slot] == 0 && client[slot] == null) {
                return true;
            }
        }
        return false;
    }

    private static void turnOff(AttributeGl gl) {
        try {
            StreamArrays.enable(gl, false);
        } catch (RuntimeException | LinkageError e) {
            // Whatever made the restore fail may stop this too, and there is nothing left to try.
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
