// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.shader.terrain;

import org.lwjgl.opengl.GL11;

import java.nio.ByteBuffer;

/**
 * Points the normal array and the generic arrays at 1, 6 and 7 at a stream, and turns them on or off. Whatever they
 * held before has to be captured in an {@link AttributeState} first. Client thread only.
 */
final class StreamArrays {

    private StreamArrays() {
    }

    /** For a stream at offset 0 of the buffer bound to GL_ARRAY_BUFFER. */
    static void pointAtBuffer(AttributeGl gl, StreamLayout layout) {
        int stride = layout.stride();
        gl.normalPointer(layout.normalType(), stride, 0L);
        gl.attribPointer(StreamLayout.ENTITY_LOCATION, 2, GL11.GL_SHORT, false, stride, layout.entityOffset());
        gl.attribPointer(StreamLayout.MID_TEX_COORD_LOCATION, 2, GL11.GL_FLOAT, false, stride,
                layout.midTexCoordOffset());
        gl.attribPointer(StreamLayout.TANGENT_LOCATION, 4, layout.tangentType(), normalizedTangent(layout), stride,
                layout.tangentOffset());
    }

    /**
     * For a stream in client memory, with buffer 0 bound to GL_ARRAY_BUFFER. GL takes the address of each pointer when
     * it's set, so the buffer's position only moves for the calls and is back at 0 afterwards.
     */
    static void pointAtClient(AttributeGl gl, StreamLayout layout, ByteBuffer stream) {
        int stride = layout.stride();
        try {
            stream.position(0);
            gl.normalPointer(layout.normalType(), stride, stream);
            stream.position(layout.entityOffset());
            gl.attribPointer(StreamLayout.ENTITY_LOCATION, 2, GL11.GL_SHORT, false, stride, stream);
            stream.position(layout.midTexCoordOffset());
            gl.attribPointer(StreamLayout.MID_TEX_COORD_LOCATION, 2, GL11.GL_FLOAT, false, stride, stream);
            stream.position(layout.tangentOffset());
            gl.attribPointer(StreamLayout.TANGENT_LOCATION, 4, layout.tangentType(), normalizedTangent(layout),
                    stride, stream);
        } finally {
            stream.position(0);
        }
    }

    static void enable(AttributeGl gl, boolean on) {
        gl.normalArray(on);
        for (int index : AttributeState.GENERIC) {
            gl.attribArray(index, on);
        }
    }

    /** A divisor left on one of the arrays would make every vertex read the first vertex's values. */
    static void clearDivisors(AttributeGl gl, AttributeState saved) {
        for (int slot = 0; slot < AttributeState.GENERIC.length; slot++) {
            if (saved.hadDivisor(slot)) {
                gl.attribDivisor(AttributeState.GENERIC[slot], 0);
            }
        }
    }

    private static boolean normalizedTangent(StreamLayout layout) {
        return layout.tangentType() != GL11.GL_FLOAT;
    }
}
