// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.shader.terrain;

import javax.annotation.Nullable;

/** Added to BufferBuilder.State, the sorted translucent data vanilla keeps per chunk for sorting it again. */
public interface StreamVertexState {

    @Nullable
    SortedIds focalis$sortedIds();

    void focalis$sortedIds(SortedIds ids);
}
