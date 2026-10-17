// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.shader.terrain;

/**
 * The stream buffer next to one chunk layer's vertex buffer. It's only drawn while it holds the stream of exactly the
 * data last uploaded to that vertex buffer, under the current plan. Client thread only.
 */
public final class BufferStream {

    // The GL buffer, or 0 when there is none.
    int buffer;
    int vertices;
    int generation;
    // Cleared whenever vanilla puts new data in the vertex buffer, and set again only by the stream upload after it.
    boolean valid;
}
