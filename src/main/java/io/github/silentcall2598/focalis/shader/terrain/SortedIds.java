// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.shader.terrain;

import javax.annotation.Nullable;

public final class SortedIds {

    private final short[] ids;
    private final int generation;
    @Nullable
    private final int[] colors;

    SortedIds(short[] ids, int generation) {
        this(ids, generation, null);
    }

    SortedIds(short[] ids, int generation, @Nullable int[] colors) {
        this.ids = ids;
        this.generation = generation;
        this.colors = colors;
    }

    short[] ids() {
        return ids;
    }

    int generation() {
        return generation;
    }

    @Nullable
    int[] colors() {
        return colors;
    }
}
