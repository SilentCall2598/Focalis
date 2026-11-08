// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.shader.terrain;

import java.util.Arrays;

/**
 * One {@link StreamLayout#entityWord} per quad. Quads nothing assigned, like ones a mod appends outside the block loop,
 * get the unmapped word. A block that adds part of a quad breaks the quad boundaries, and the layer can't have a
 * stream then. Not thread safe.
 */
final class QuadIds {

    static final int UNMAPPED = StreamLayout.entityWord(-1, 0);

    private int[] ids = new int[1024];
    private int[] scratch = new int[0];
    private int quads;
    private int blockStart = -1;
    private boolean broken;

    void reset() {
        quads = 0;
        blockStart = -1;
        broken = false;
    }

    int capacity() {
        return (ids.length + scratch.length) * 4;
    }

    void release() {
        reset();
        if (ids.length > 16) {
            ids = new int[16];
        }
        if (scratch.length > 0) {
            scratch = new int[0];
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

    void blockEnded(int vertices, int word) {
        if (broken) {
            return;
        }
        if (blockStart < 0 || vertices % 4 != 0 || vertices < blockStart) {
            broken = true;
            return;
        }
        blockStart = -1;
        fill(vertices / 4, word);
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

    int[] array() {
        return ids;
    }

    void permute(int[] order, int count) {
        if (count != quads) {
            throw new IllegalArgumentException("A sort of " + count + " quads doesn't fit " + quads + " IDs");
        }
        if (scratch.length < count) {
            scratch = new int[ids.length];
        }
        for (int p = 0; p < count; p++) {
            scratch[p] = ids[order[p]];
        }
        int[] sorted = scratch;
        scratch = ids;
        ids = sorted;
    }

    void load(int[] saved) {
        reset();
        ensure(saved.length);
        System.arraycopy(saved, 0, ids, 0, saved.length);
        quads = saved.length;
    }

    int[] copy() {
        return Arrays.copyOf(ids, quads);
    }

    private void fill(int upTo, int word) {
        ensure(upTo);
        Arrays.fill(ids, quads, upTo, word);
        quads = upTo;
    }

    private void ensure(int size) {
        if (ids.length < size) {
            ids = Arrays.copyOf(ids, Math.max(size, ids.length + (ids.length >> 1)));
        }
    }
}
