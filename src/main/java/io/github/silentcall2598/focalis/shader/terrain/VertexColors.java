// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.shader.terrain;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

final class VertexColors {

    private static final int SOURCE_STRIDE = StreamWriter.SOURCE_STRIDE;
    private static final int COLOR = 12;
    private static final int INTS_PER_VERTEX = SOURCE_STRIDE / 4;

    private int[] colors = new int[64];
    private int[] expected = new int[64];
    private int[] stamps = new int[64];
    private int[] scratch = new int[0];
    private int build;
    private boolean faceShading;
    private boolean separateAo;
    private boolean resolved;
    private int quads;
    private int capturedQuads;
    private int uncapturedQuads;
    private int mismatchedQuads;

    private final float[] forgeBase = new float[4];
    private boolean forgeBaseSeen;
    private boolean forgeTinted;
    private int forgeMultiplier;
    private float forgeAo;
    private float forgeDiffuse;

    private boolean modelSmooth;
    private boolean modelTinted;
    private int modelMultiplier;
    private boolean modelDiffused;
    private float modelDiffuse;
    private final float[] modelAo = new float[4];
    private boolean modelAoPending;
    private boolean modelAoTaken;

    private int fluidMultiplier = -1;
    private boolean fluidTinted;

    void begin(boolean keepFaceShading, boolean aoInAlpha) {
        build = build == Integer.MAX_VALUE ? 1 : build + 1;
        faceShading = keepFaceShading;
        separateAo = aoInAlpha;
        resolved = false;
        quads = 0;
        capturedQuads = 0;
        uncapturedQuads = 0;
        mismatchedQuads = 0;
        blockStarted();
    }

    void blockStarted() {
        forgeBaseSeen = false;
        modelAoPending = false;
        modelAoTaken = false;
        fluidTinted = false;
    }

    boolean capturing() {
        return !resolved;
    }

    void forgeBase(float[] color, boolean tinted, int multiplier) {
        System.arraycopy(color, 0, forgeBase, 0, 4);
        forgeTinted = tinted;
        forgeMultiplier = multiplier;
        forgeAo = 1.0F;
        forgeDiffuse = 1.0F;
        forgeBaseSeen = true;
    }

    void forgeAo(float ao) {
        forgeAo = ao;
    }

    void forgeDiffuse(float diffuse) {
        forgeDiffuse = diffuse;
    }

    void forgeColor(int vertex, float[] color) {
        if (!forgeBaseSeen) {
            return;
        }
        forgeBaseSeen = false;
        for (int i = 0; i < 3; i++) {
            if (color[i] != PackColors.forgeChannel(forgeBase[i], forgeTinted, forgeMultiplier, i, forgeAo,
                    forgeDiffuse, true, true)) {
                return;
            }
        }
        if (color[3] != forgeBase[3]) {
            return;
        }
        capture(vertex, PackColors.forgePack(forgeBase, forgeTinted, forgeMultiplier, forgeAo, forgeDiffuse,
                faceShading, separateAo), PackColors.forgeVanilla(forgeBase, forgeTinted, forgeMultiplier, forgeAo,
                forgeDiffuse));
    }

    void modelQuad(int first, int[] data, boolean smooth) {
        modelSmooth = smooth;
        modelTinted = false;
        modelDiffused = false;
        modelAoTaken = smooth && modelAoPending;
        modelAoPending = false;
        if (data.length != 4 * INTS_PER_VERTEX) {
            return;
        }
        boolean little = ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN;
        for (int v = 0; v < 4; v++) {
            int raw = data[v * INTS_PER_VERTEX + COLOR / 4];
            int color = little ? raw : Integer.reverseBytes(raw);
            capture(first + v, color, color);
        }
    }

    void modelAo(float[] vertexColorMultiplier) {
        System.arraycopy(vertexColorMultiplier, 0, modelAo, 0, 4);
        modelAoPending = true;
    }

    void modelDiffuse(float diffuse) {
        modelDiffused = true;
        modelDiffuse = diffuse;
    }

    void modelTint(int multiplier) {
        modelTinted = true;
        modelMultiplier = multiplier;
    }

    void modelVertex(int vertex, int index, int base, float red, float green, float blue) {
        if (modelSmooth && !modelAoTaken || index < 0 || index > 3) {
            return;
        }
        float ao = modelSmooth ? modelAo[index] : 1.0F;
        if (red != PackColors.modelMultiplier(modelSmooth, modelTinted, modelMultiplier, 0, ao, modelDiffused,
                modelDiffuse, true, true)
                || green != PackColors.modelMultiplier(modelSmooth, modelTinted, modelMultiplier, 1, ao, modelDiffused,
                modelDiffuse, true, true)
                || blue != PackColors.modelMultiplier(modelSmooth, modelTinted, modelMultiplier, 2, ao, modelDiffused,
                modelDiffuse, true, true)) {
            forget(vertex);
            return;
        }
        capture(vertex, PackColors.modelPack(base, modelSmooth, modelTinted, modelMultiplier, ao, modelDiffused,
                modelDiffuse, faceShading, separateAo), PackColors.modelVanilla(base, red, green, blue));
    }

    void fluidTint(int multiplier) {
        fluidMultiplier = multiplier;
        fluidTinted = true;
    }

    void fluidVertex(int vertex, float red, float green, float blue, float alpha) {
        if (!fluidTinted) {
            return;
        }
        float face = PackColors.fluidFace(fluidMultiplier, red, green, blue);
        if (face < 0) {
            return;
        }
        capture(vertex, PackColors.fluidPack(fluidMultiplier, face, red, green, blue, alpha, faceShading),
                PackColors.fluidVanilla(red, green, blue, alpha));
    }

    private void capture(int vertex, int color, int vanilla) {
        if (resolved || vertex < 0) {
            return;
        }
        ensure(vertex + 1);
        colors[vertex] = color;
        expected[vertex] = vanilla;
        stamps[vertex] = build;
    }

    private void forget(int vertex) {
        if (!resolved && vertex >= 0 && vertex < stamps.length && stamps[vertex] == build) {
            stamps[vertex] = 0;
        }
    }

    void resolve(ByteBuffer data, int vertices) {
        if (resolved) {
            return;
        }
        resolved = true;
        quads = vertices / 4;
        ensure(quads * 4);
        for (int quad = 0; quad < quads; quad++) {
            int first = quad * 4;
            int seen = 0;
            boolean matches = true;
            for (int v = first; v < first + 4; v++) {
                if (stamps[v] == build) {
                    seen++;
                    matches &= expected[v] == actual(data, v);
                }
            }
            if (seen == 4 && matches) {
                capturedQuads++;
                continue;
            }
            if (seen == 0) {
                uncapturedQuads++;
            } else {
                mismatchedQuads++;
            }
            for (int v = first; v < first + 4; v++) {
                colors[v] = actual(data, v);
            }
        }
    }

    private static int actual(ByteBuffer data, int vertex) {
        int at = vertex * SOURCE_STRIDE + COLOR;
        return PackColors.pack(data.get(at), data.get(at + 1), data.get(at + 2), data.get(at + 3));
    }

    void permute(int[] order, int count) {
        if (count != quads) {
            throw new IllegalArgumentException("A sort of " + count + " quads doesn't fit " + quads + " quads of colors");
        }
        if (scratch.length < count * 4) {
            scratch = new int[colors.length];
        }
        for (int p = 0; p < count; p++) {
            System.arraycopy(colors, order[p] * 4, scratch, p * 4, 4);
        }
        int[] sorted = scratch;
        scratch = colors;
        colors = sorted;
    }

    int[] copy() {
        return Arrays.copyOf(colors, quads * 4);
    }

    void load(int[] saved) {
        ensure(saved.length);
        System.arraycopy(saved, 0, colors, 0, saved.length);
        quads = saved.length / 4;
        resolved = true;
    }

    int[] array() {
        return colors;
    }

    int capturedQuads() {
        return capturedQuads;
    }

    int uncapturedQuads() {
        return uncapturedQuads;
    }

    int mismatchedQuads() {
        return mismatchedQuads;
    }

    int capacity() {
        return (colors.length + expected.length + stamps.length + scratch.length) * 4;
    }

    private void ensure(int size) {
        if (colors.length < size) {
            int grown = Math.max(size, colors.length + (colors.length >> 1));
            colors = Arrays.copyOf(colors, grown);
            expected = Arrays.copyOf(expected, grown);
            stamps = Arrays.copyOf(stamps, grown);
        }
    }
}
