// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.shader.terrain;

/**
 * The material IDs of a translucent layer in the order of the sorted data vanilla keeps for sorting it again. They're
 * attached to that saved data itself, so the two can't be separated or mixed up between chunks or concurrent sorts.
 * Immutable once attached.
 */
public final class SortedIds {

    private final short[] ids;
    private final int generation;

    SortedIds(short[] ids, int generation) {
        this.ids = ids;
        this.generation = generation;
    }

    /** One ID per quad. Not a copy, and never written after it was attached. */
    short[] ids() {
        return ids;
    }

    /** The plan generation the IDs were resolved under. */
    int generation() {
        return generation;
    }
}
