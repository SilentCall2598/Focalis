// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.shader.terrain;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

/**
 * Follows vanilla's translucent sort without touching its data. The sort leaves its index array in the order it moved
 * the quads in, so quad p after the sort came from order[p] before it, and the IDs follow that. The quads are copied
 * aside before the sort, and afterwards each one has to equal the quad the order says it came from, byte for byte, or
 * the order isn't used. Not thread safe.
 */
final class SortCheck {

    private ByteBuffer before = newBuffer(0);
    private int[] order = new int[0];
    private int[] seen = new int[0];
    private int stamp;
    private int quads = -1;
    private int quadBytes;
    private boolean armed;
    private int observations;
    private boolean unreadable;

    /** Copies the quads aside before the sort and starts listening for it. */
    void prepare(ByteBuffer data, int quadCount, int bytesPerQuad) {
        if (bytesPerQuad <= 0 || bytesPerQuad % 8 != 0) {
            throw new IllegalArgumentException("A quad of " + bytesPerQuad + " bytes can't be compared in longs");
        }
        int bytes = quadCount * bytesPerQuad;
        if (before.capacity() < bytes) {
            // Headroom, so a slightly bigger layer next time doesn't allocate again.
            before = newBuffer(Math.max(bytes, before.capacity() + (before.capacity() >> 1)));
        }
        if (order.length < quadCount) {
            int size = Math.max(quadCount, order.length + (order.length >> 1));
            order = new int[size];
            seen = new int[size];
        }
        // Its own view, so the builder's position and limit stay untouched.
        ByteBuffer source = data.duplicate();
        source.clear();
        source.limit(bytes);
        before.clear();
        before.put(source);
        quads = quadCount;
        quadBytes = bytesPerQuad;
        armed = true;
        observations = 0;
        unreadable = false;
    }

    /** The index array right after the sort sorted it. */
    void observed(Object[] sorted) {
        if (!armed) {
            return;
        }
        observations++;
        if (sorted.length != quads) {
            unreadable = true;
            return;
        }
        for (int p = 0; p < sorted.length; p++) {
            Object index = sorted[p];
            if (!(index instanceof Integer)) {
                unreadable = true;
                return;
            }
            order[p] = (Integer) index;
        }
    }

    /**
     * Stops listening and checks the data after the sort.
     *
     * @return null when the order can be used, otherwise why not
     */
    String verify(ByteBuffer data) {
        if (!armed) {
            return "vanilla's translucent sort wasn't prepared for";
        }
        armed = false;
        if (observations != 1) {
            return observations == 0 ? "vanilla's translucent sort wasn't observed"
                    : "vanilla's translucent sort sorted more than once";
        }
        if (unreadable) {
            return "the sort's index array didn't hold the quad indices";
        }
        if (++stamp == 0) {
            // Wrapped around, so old stamps could look current.
            Arrays.fill(seen, 0);
            stamp = 1;
        }
        for (int p = 0; p < quads; p++) {
            int from = order[p];
            if (from < 0 || from >= quads || seen[from] == stamp) {
                return "the sort's order isn't a permutation";
            }
            seen[from] = stamp;
        }
        for (int p = 0; p < quads; p++) {
            if (!sameQuad(data, p * quadBytes, order[p] * quadBytes)) {
                return "the sorted data doesn't match the sort's order";
            }
        }
        return null;
    }

    /** Quad p after the sort came from order()[p] before it. Valid up to the sorted count after a good verify. */
    int[] order() {
        return order;
    }

    int quads() {
        return quads;
    }

    void abandon() {
        armed = false;
    }

    /** The bytes the buffers hold on to. */
    int capacity() {
        return before.capacity() + (order.length + seen.length) * 4;
    }

    /** Stops listening and lets go of the buffers, which grow to the biggest layer there was. */
    void release() {
        abandon();
        if (before.capacity() > 0) {
            before = newBuffer(0);
        }
        if (order.length > 0) {
            order = new int[0];
            seen = new int[0];
        }
        stamp = 0;
        quads = -1;
    }

    // The quad after the sort at one offset against the saved quad at another.
    private boolean sameQuad(ByteBuffer data, int dataOffset, int savedOffset) {
        for (int i = 0; i < quadBytes; i += 8) {
            if (data.getLong(dataOffset + i) != before.getLong(savedOffset + i)) {
                return false;
            }
        }
        return true;
    }

    private static ByteBuffer newBuffer(int bytes) {
        return ByteBuffer.allocateDirect(bytes).order(ByteOrder.nativeOrder());
    }
}
