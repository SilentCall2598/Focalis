// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.shader.program;

import io.github.silentcall2598.focalis.render.lifecycle.RenderDrawKind;
import io.github.silentcall2598.focalis.render.lifecycle.RenderStage;

/**
 * Where the world program binding asks whether chunk terrain can feed a program the attribute stream, and tells the
 * stream which terrain scopes need it. Client thread only.
 */
public interface TerrainStreamGate {

    /** Never ready, so terrain programs that need the stream draw the vanilla way. */
    TerrainStreamGate CLOSED = new TerrainStreamGate() {
        @Override
        public boolean ready(RenderDrawKind layer) {
            return false;
        }

        @Override
        public void opened(RenderStage stage, RenderDrawKind layer, DrawSuspension suspension) {
            throw new IllegalStateException("No terrain scope can use the attribute stream");
        }

        @Override
        public void closed(RenderStage stage, RenderDrawKind layer) {
            throw new IllegalStateException("No terrain scope can use the attribute stream");
        }

        @Override
        public void reset() {
        }
    };

    /** Whether draws of this terrain layer can get the stream right now. */
    boolean ready(RenderDrawKind layer);

    /**
     * A terrain scope whose program needs the stream was just opened and its program bound. Its chunk draws use the
     * stream, and a chunk without a usable one steps aside through {@code suspension}.
     */
    void opened(RenderStage stage, RenderDrawKind layer, DrawSuspension suspension);

    /** That scope is about to close. Calls are nested like the scopes. */
    void closed(RenderStage stage, RenderDrawKind layer);

    /** Every scope was unwound at once, like after a failure. */
    void reset();
}
