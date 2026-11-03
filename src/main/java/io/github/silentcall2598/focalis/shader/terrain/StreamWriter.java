// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.shader.terrain;

import org.lwjgl.opengl.GL11;

import javax.annotation.Nullable;
import java.nio.ByteBuffer;

final class StreamWriter {

    static final int SOURCE_STRIDE = 28;
    private static final int POSITION = 0;
    private static final int TEXTURE = 16;

    private final QuadFrame frame = new QuadFrame();

    void write(ByteBuffer source, int quads, short[] ids, StreamLayout layout, ByteBuffer target) {
        write(source, quads, ids, null, layout, target);
    }

    void write(ByteBuffer source, int quads, short[] ids, @Nullable int[] colors, StreamLayout layout,
            ByteBuffer target) {
        if (layout.hasColors() != (colors != null)) {
            throw new IllegalArgumentException("The " + layout + " layout " + (layout.hasColors() ? "needs" : "has no")
                    + " colors");
        }
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
            if (colors != null) {
                writeColors(target, out, stride, layout.colorOffset(), colors, quad * 4);
            }
        }
    }

    private static void writeColors(ByteBuffer target, int out, int stride, int offset, int[] colors, int first) {
        for (int i = 0; i < 4; i++) {
            int at = out + i * stride + offset;
            int color = colors[first + i];
            target.put(at, (byte) color);
            target.put(at + 1, (byte) (color >> 8));
            target.put(at + 2, (byte) (color >> 16));
            target.put(at + 3, (byte) (color >> 24));
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
