// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.render.lifecycle;

// Counts entity and block entity renderers running inside each other, like a block entity that draws an entity, so
// only the outermost one returning counts. Client thread only.
final class RendererNesting {

    private int depth;

    void entered() {
        depth++;
    }

    // True when the outermost renderer just returned. A return without a matching start is taken as outermost.
    boolean returned() {
        if (depth > 0) {
            depth--;
        }
        return depth == 0;
    }

    // A renderer that threw never returns, so its count is dropped at the next world pass.
    void reset() {
        depth = 0;
    }

    int depth() {
        return depth;
    }
}
