// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.shader.terrain;

import javax.annotation.Nullable;

/** Added to VertexBuffer, which keeps the stream buffer of the chunk layer it holds. */
public interface StreamVertexBuffer {

    @Nullable
    BufferStream focalis$stream();

    void focalis$stream(@Nullable BufferStream stream);

    /** The vertex buffer's own GL buffer, -1 once vanilla deleted it. */
    int focalis$glBuffer();

    /** How many vertices vanilla draws from it. */
    int focalis$vertexCount();
}
