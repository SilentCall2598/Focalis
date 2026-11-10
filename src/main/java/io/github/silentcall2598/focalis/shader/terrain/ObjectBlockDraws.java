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
    private boolean pushed;
    private boolean fedNormals;
    private int maxDepth;

    private long draws;
    private long quads;
    private long coloredQuads;
    private long refused;

    ObjectBlockDraws(AttributeGl gl) {
        this.gl = gl;
    }

    void begun(BufferBuilder buffer, int mode, @Nullable LegacyLighting lighting) {
        if (lighting == null || mode != GL11.GL_QUADS) {
            LightingCapture.releaseObject(buffer);
            return;
        }
        colors.begin(lighting.faceShading(), lighting.separateAo(), lighting.ambientOcclusionLevel());
        LightingCapture.captureObject(buffer, colors);
    }

    void abandon() {
        LightingCapture.dropObject();
        restore();
    }

    boolean drawing(BufferBuilder buffer, @Nullable ObjectBlockPrograms programs) {
        boolean captured = LightingCapture.capturesObject(buffer);
        LightingCapture.releaseObject(buffer);
        int vertices = buffer.getVertexCount();
        // A draw from inside a fed one, through another mod's wrapper of the same call, would replace the state the
        // outer restore needs.
        if (pushed || programs == null || buffer.getDrawMode() != GL11.GL_QUADS || vertices == 0 || vertices % 4 != 0) {
            return false;
        }
        int flags = programs.blockDraw();
        if (flags == ObjectBlockPrograms.NONE) {
            return false;
        }
        // A normal array that is already on belongs to the renderer that turned it on, and this draw reads its normals
        // as it would without Focalis.
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
            return false;
        }
        if (maxDepth == 0) {
            maxDepth = gl.maxClientAttribDepth();
        }
        // A push onto a full stack only raises an error, and the pop would then take another renderer's state.
        if (gl.clientAttribDepth() >= maxDepth) {
            refused++;
            return false;
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
        }
        gl.pushClientArrays();
        pushed = true;
        if (normals) {
            fedNormals = true;
            gl.normalPointer(layout.normalType(), layout.stride(), target);
            gl.normalArray(true);
        }
        if (pack != null) {
            coloredQuads += colors.capturedQuads();
            try {
                target.position(layout.colorOffset());
                gl.colorPointer(4, GL11.GL_UNSIGNED_BYTE, layout.stride(), target);
            } finally {
                target.position(0);
            }
        }
        return true;
    }

    // The pop puts back every vertex array as the draw's preDraw left it, pointers and their buffer bindings included,
    // and LWJGL's references to client buffers with them. The current normal is not client state, and a draw from an
    // enabled normal array leaves it indeterminate.
    void restore() {
        boolean popping = pushed;
        boolean normals = fedNormals;
        pushed = false;
        fedNormals = false;
        if (!popping) {
            return;
        }
        try {
            gl.popClientArrays();
        } finally {
            if (normals) {
                gl.setCurrentNormal(normal[0], normal[1], normal[2]);
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
        return "object block draws " + draws + " with " + quads + " quads, " + coloredQuads + " with captured colors, "
                + refused + " left to vanilla on a full client attribute stack";
    }
}
