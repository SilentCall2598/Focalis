// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.shader.terrain;

import java.util.Arrays;

/**
 * The material ID of every quad of one layer build, in buffer order. Each block the chunk renders gives its ID to the
 * quads it added. Quads nothing assigned, like ones a mod appends outside the block loop, get the unmapped ID. A block
 * that adds part of a quad breaks the quad boundaries, and the layer can't have a stream then. Not thread safe.
 */
final class QuadIds {

    static final short UNMAPPED = -1;

    private short[] ids = new short[1024];
    private short[] scratch = new short[0];
    // Quads with an ID so far. Everything below is assigned.
    private int quads;
    private int blockStart = -1;
    private boolean broken;

    void reset() {
        quads = 0;
        blockStart = -1;
        broken = false;
    }

    /** The bytes the arrays hold on to. */
    int capacity() {
        return (ids.length + scratch.length) * 2;
    }

    /** Starts over and lets go of the arrays, which grow to the biggest layer there was. */
    void release() {
        reset();
        if (ids.length > 16) {
            ids = new short[16];
        }
        if (scratch.length > 0) {
            scratch = new short[0];
        }
    }

    /** A block render starts with {@code vertices} in the buffer. */
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

    /** That render ended with {@code vertices} in the buffer, and everything it added has {@code id}. */
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

    /**
     * The build is done with {@code vertices} in the buffer. Anything not assigned yet becomes unmapped.
     *
     * @return whether every quad has an ID that lines up with the quad boundaries
     */
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

    /** How many quads have an ID. */
    int count() {
        return quads;
    }

    /** The IDs, valid up to {@link #count()}. Not a copy. */
    short[] array() {
        return ids;
    }

    /** Moves the IDs the way a sort moved the quads, so quad p gets the ID of the quad that was at order[p]. */
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

    /** Takes over IDs a translucent layer saved with its sorted data, for sorting it again. */
    void load(short[] saved) {
        reset();
        ensure(saved.length);
        System.arraycopy(saved, 0, ids, 0, saved.length);
        quads = saved.length;
    }

    /** A copy of the IDs, for saving with the data they belong to. */
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
