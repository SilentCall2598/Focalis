// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.shader.terrain;

/**
 * Added to ListedRenderChunk, which remembers per layer whether its display list was compiled with the stream, and
 * under which plan generation. Client thread only.
 */
public interface StreamDisplayLists {

    /** 0 when the layer's list has no stream. */
    int focalis$streamGeneration(int layer);

    void focalis$streamGeneration(int layer, int generation);
}
