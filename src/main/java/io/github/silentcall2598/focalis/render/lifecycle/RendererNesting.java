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

    // Renderers also return when they throw, so this only drops a count whose return never came, like after a VM error.
    void reset() {
        depth = 0;
    }

    int depth() {
        return depth;
    }
}
