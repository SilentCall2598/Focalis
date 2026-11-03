// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.shader.terrain;

import java.util.Arrays;

/**
 * Quads nothing assigned, like ones a mod appends outside the block loop, get the unmapped ID. A block that adds part
 * of a quad breaks the quad boundaries, and the layer can't have a stream then. Not thread safe.
 */
final class QuadIds {

    static final short UNMAPPED = -1;

    private short[] ids = new short[1024];
    private short[] scratch = new short[0];
    private int quads;
    private int blockStart = -1;
    private boolean broken;

    void reset() {
        quads = 0;
        blockStart = -1;
        broken = false;
    }

    int capacity() {
        return (ids.length + scratch.length) * 2;
    }

    void release() {
        reset();
        if (ids.length > 16) {
            ids = new short[16];
        }
        if (scratch.length > 0) {
            scratch = new short[0];
        }
    }

    void blockStarted(int vertices) {
        if (broken) {
            return;
        }
        if (blockStart >= 0 || vertices % 4 != 0 || vertices / 4 < quads) {
            broken = true;
            return;
        }
        fill(vertices / 4, UNMAPPED);
        blockStart = vertices;
    }

    void blockEnded(int vertices, short id) {
        if (broken) {
            return;
        }
        if (blockStart < 0 || vertices % 4 != 0 || vertices < blockStart) {
            broken = true;
            return;
        }
        blockStart = -1;
        fill(vertices / 4, id);
    }

    boolean finish(int vertices) {
        if (broken || blockStart >= 0 || vertices % 4 != 0 || vertices / 4 < quads) {
            broken = true;
            return false;
        }
        fill(vertices / 4, UNMAPPED);
        return true;
    }

    boolean broken() {
        return broken;
    }

    int count() {
        return quads;
    }

    short[] array() {
        return ids;
    }

    void permute(int[] order, int count) {
        if (count != quads) {
            throw new IllegalArgumentException("A sort of " + count + " quads doesn't fit " + quads + " IDs");
        }
        if (scratch.length < count) {
            scratch = new short[ids.length];
        }
        for (int p = 0; p < count; p++) {
            scratch[p] = ids[order[p]];
        }
        short[] sorted = scratch;
        scratch = ids;
        ids = sorted;
    }

    void load(short[] saved) {
        reset();
        ensure(saved.length);
        System.arraycopy(saved, 0, ids, 0, saved.length);
        quads = saved.length;
    }

    short[] copy() {
        return Arrays.copyOf(ids, quads);
    }

    private void fill(int upTo, short id) {
        ensure(upTo);
        Arrays.fill(ids, quads, upTo, id);
        quads = upTo;
    }

    private void ensure(int size) {
        if (ids.length < size) {
            ids = Arrays.copyOf(ids, Math.max(size, ids.length + (ids.length >> 1)));
        }
    }
}
