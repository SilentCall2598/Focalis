// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.shader.terrain;

import javax.annotation.Nullable;

public interface StreamVertexState {

    @Nullable
    SortedIds focalis$sortedIds();

    void focalis$sortedIds(SortedIds ids);
}
