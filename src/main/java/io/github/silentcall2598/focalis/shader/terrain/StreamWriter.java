// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.shader.terrain;

import org.lwjgl.opengl.GL11;

import java.nio.ByteBuffer;

/**
 * Writes the stream for quads in vanilla's chunk vertex format, DefaultVertexFormats.BLOCK, which keeps the position
 * as three floats at byte 0 and the texture coordinates as two floats at byte 16 of each 28 byte vertex. Only absolute
 * gets and puts are used, so neither buffer's position or limit moves. Both have to be in native byte order. Not
 * thread safe.
 */
final class StreamWriter {

    static final int SOURCE_STRIDE = 28;
    private static final int POSITION = 0;
    private static final int TEXTURE = 16;

    private final QuadFrame frame = new QuadFrame();

    /**
     * Writes {@code quads} quads from the start of {@code source} to the start of {@code target}, which needs room for
     * {@code quads * 4 * layout.stride()} bytes.
     *
     * @param ids one material ID per quad, as mc_Entity stores it
     */
    void write(ByteBuffer source, int quads, short[] ids, StreamLayout layout, ByteBuffer target) {
        int stride = layout.stride();
        boolean bytes = layout.normalType() == GL11.GL_BYTE;
        for (int quad = 0; quad < quads; quad++) {
            int base = quad * 4 * SOURCE_STRIDE;
            for (int i = 0; i < 4; i++) {
                int at = base + i * SOURCE_STRIDE;
                frame.vertex(i, source.getFloat(at + POSITION), source.getFloat(at + POSITION + 4),
                        source.getFloat(at + POSITION + 8), source.getFloat(at + TEXTURE),
                        source.getFloat(at + TEXTURE + 4));
            }
            frame.compute();
            short id = ids[quad];
            int out = quad * 4 * stride;
            if (bytes) {
                writeCompact(target, out, stride, layout, id);
            } else {
                writeFloats(target, out, stride, layout, id);
            }
        }
    }

    private void writeCompact(ByteBuffer target, int out, int stride, StreamLayout layout, short id) {
        byte nx = StreamLayout.snorm8(frame.normalX);
        byte ny = StreamLayout.snorm8(frame.normalY);
        byte nz = StreamLayout.snorm8(frame.normalZ);
        byte tx = StreamLayout.snorm8(frame.tangentX);
        byte ty = StreamLayout.snorm8(frame.tangentY);
        byte tz = StreamLayout.snorm8(frame.tangentZ);
        byte tw = StreamLayout.snorm8(frame.tangentW);
        float midU = (float) frame.midU;
        float midV = (float) frame.midV;
        int entity = layout.entityOffset();
        int mid = layout.midTexCoordOffset();
        int tangent = layout.tangentOffset();
        for (int i = 0; i < 4; i++) {
            int at = out + i * stride;
            target.put(at, nx);
            target.put(at + 1, ny);
            target.put(at + 2, nz);
            target.put(at + 3, (byte) 0);
            target.putShort(at + entity, id);
            target.putShort(at + entity + 2, (short) 0);
            target.putFloat(at + mid, midU);
            target.putFloat(at + mid + 4, midV);
            target.put(at + tangent, tx);
            target.put(at + tangent + 1, ty);
            target.put(at + tangent + 2, tz);
            target.put(at + tangent + 3, tw);
        }
    }

    private void writeFloats(ByteBuffer target, int out, int stride, StreamLayout layout, short id) {
        float nx = (float) frame.normalX;
        float ny = (float) frame.normalY;
        float nz = (float) frame.normalZ;
        float tx = (float) frame.tangentX;
        float ty = (float) frame.tangentY;
        float tz = (float) frame.tangentZ;
        float tw = (float) frame.tangentW;
        float midU = (float) frame.midU;
        float midV = (float) frame.midV;
        int entity = layout.entityOffset();
        int mid = layout.midTexCoordOffset();
        int tangent = layout.tangentOffset();
        for (int i = 0; i < 4; i++) {
            int at = out + i * stride;
            target.putFloat(at, nx);
            target.putFloat(at + 4, ny);
            target.putFloat(at + 8, nz);
            target.putShort(at + entity, id);
            target.putShort(at + entity + 2, (short) 0);
            target.putFloat(at + mid, midU);
            target.putFloat(at + mid + 4, midV);
            target.putFloat(at + tangent, tx);
            target.putFloat(at + tangent + 4, ty);
            target.putFloat(at + tangent + 8, tz);
            target.putFloat(at + tangent + 12, tw);
        }
    }
}
