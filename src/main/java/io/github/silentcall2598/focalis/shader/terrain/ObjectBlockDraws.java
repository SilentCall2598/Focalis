// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.shader.terrain;

import io.github.silentcall2598.focalis.shader.pack.LegacyLighting;
import io.github.silentcall2598.focalis.shader.program.ObjectBlockPrograms;
import net.minecraft.client.renderer.BufferBuilder;
import org.lwjgl.opengl.GL11;

import javax.annotation.Nullable;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

final class ObjectBlockDraws {

    private final AttributeGl gl;
    private final VertexColors colors = new VertexColors();
    private final StreamWriter writer = new StreamWriter();
    private final float[] normal = new float[3];
    private int[] ids = new int[0];
    @Nullable
    private ByteBuffer stream;
    private boolean fedNormals;
    @Nullable
    private ByteBuffer fedColorsOf;
    private int colorOffset;
    private int colorStride;

    private long draws;
    private long quads;
    private long coloredQuads;

    ObjectBlockDraws(AttributeGl gl) {
        this.gl = gl;
    }

    void begun(BufferBuilder buffer, int mode, @Nullable LegacyLighting lighting) {
        if (lighting == null || mode != GL11.GL_QUADS) {
            LightingCapture.releaseObject(buffer);
            return;
        }
        colors.begin(lighting.faceShading(), lighting.separateAo());
        LightingCapture.captureObject(buffer, colors);
    }

    void abandon() {
        LightingCapture.releaseObject(LightingCapture.objectBuffer());
        restore();
    }

    void drawing(BufferBuilder buffer, @Nullable ObjectBlockPrograms programs) {
        boolean captured = LightingCapture.capturesObject(buffer);
        LightingCapture.releaseObject(buffer);
        // Only a draw that threw leaves a feed behind. preDraw has already pointed the colors at this draw's buffer.
        fedColorsOf = null;
        restore();
        int vertices = buffer.getVertexCount();
        if (programs == null || buffer.getDrawMode() != GL11.GL_QUADS || vertices == 0 || vertices % 4 != 0) {
            return;
        }
        int flags = programs.blockDraw();
        if (flags == ObjectBlockPrograms.NONE) {
            return;
        }
        // Another renderer left the normal array on, so this draw reads its normals already. Taking it over would mean
        // reading its pointer back, and LWJGL allocates a buffer for every such read.
        boolean normals = (flags & ObjectBlockPrograms.NORMAL) != 0 && !gl.normalArrayEnabled();
        ByteBuffer data = buffer.getByteBuffer();
        int[] pack = null;
        if ((flags & ObjectBlockPrograms.COLORS) != 0 && captured) {
            colors.resolve(data, vertices);
            if (colors.capturedQuads() > 0) {
                pack = colors.array();
            }
        }
        if (!normals && pack == null) {
            return;
        }
        StreamLayout layout = pack != null ? StreamLayout.BUFFER_COLOR : StreamLayout.BUFFER;
        int count = vertices / 4;
        ByteBuffer target = target(vertices * layout.stride());
        if (ids.length < count) {
            ids = new int[Math.max(count, ids.length + (ids.length >> 1))];
            Arrays.fill(ids, QuadIds.UNMAPPED);
        }
        writer.write(data, count, ids, pack, layout, target);
        draws++;
        quads += count;
        if (normals) {
            gl.currentNormal(normal);
            gl.normalPointer(layout.normalType(), layout.stride(), target);
            fedNormals = true;
            gl.normalArray(true);
        }
        if (pack != null) {
            coloredQuads += colors.capturedQuads();
            colorOffset = buffer.getVertexFormat().getColorOffset();
            colorStride = buffer.getVertexFormat().getSize();
            fedColorsOf = data;
            try {
                target.position(layout.colorOffset());
                gl.colorPointer(4, GL11.GL_UNSIGNED_BYTE, layout.stride(), target);
            } finally {
                target.position(0);
            }
        }
    }

    // The normal array goes off with its pointer left on the stream, which LWJGL keeps referenced. Forge's preDraw, the
    // only place vanilla and Forge turn the array on, sets the pointer first, and every draw of a format with normals
    // moves it anyway. The color pointer goes back to vanilla's buffer exactly as its preDraw set it.
    void restore() {
        ByteBuffer vanilla = fedColorsOf;
        boolean normals = fedNormals;
        fedColorsOf = null;
        fedNormals = false;
        try {
            if (normals) {
                gl.normalArray(false);
                gl.setCurrentNormal(normal[0], normal[1], normal[2]);
            }
        } finally {
            if (vanilla != null) {
                int position = vanilla.position();
                try {
                    vanilla.position(colorOffset);
                    gl.colorPointer(4, GL11.GL_UNSIGNED_BYTE, colorStride, vanilla);
                } finally {
                    vanilla.position(position);
                }
            }
        }
    }

    private ByteBuffer target(int bytes) {
        ByteBuffer current = stream;
        if (current == null || current.capacity() < bytes) {
            int capacity = Math.max(bytes, current == null ? 0 : current.capacity() + (current.capacity() >> 1));
            current = ByteBuffer.allocateDirect(Math.max(capacity, 4096)).order(ByteOrder.nativeOrder());
            stream = current;
        }
        current.position(0);
        return current;
    }

    String statistics() {
        return "object block draws " + draws + " with " + quads + " quads, " + coloredQuads + " with captured colors";
    }
}
