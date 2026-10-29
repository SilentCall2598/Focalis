// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.shader.terrain;

import org.lwjgl.opengl.GL11;

import java.nio.ByteBuffer;

final class StreamArrays {

    private StreamArrays() {
    }

    static void pointAtBuffer(AttributeGl gl, StreamLayout layout) {
        int stride = layout.stride();
        gl.normalPointer(layout.normalType(), stride, 0L);
        gl.attribPointer(StreamLayout.ENTITY_LOCATION, 2, GL11.GL_SHORT, false, stride, layout.entityOffset());
        gl.attribPointer(StreamLayout.MID_TEX_COORD_LOCATION, 2, GL11.GL_FLOAT, false, stride,
                layout.midTexCoordOffset());
        gl.attribPointer(StreamLayout.TANGENT_LOCATION, 4, layout.tangentType(), normalizedTangent(layout), stride,
                layout.tangentOffset());
        if (layout.hasColors()) {
            gl.colorPointer(4, GL11.GL_UNSIGNED_BYTE, stride, layout.colorOffset());
        }
    }

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
