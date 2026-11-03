// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.shader.terrain;

import javax.annotation.Nullable;

public interface StreamVertexBuffer {

    @Nullable
    BufferStream focalis$stream();

    void focalis$stream(@Nullable BufferStream stream);

    int focalis$glBuffer();

    int focalis$vertexCount();
}
