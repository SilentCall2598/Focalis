// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.shader.program;

import io.github.silentcall2598.focalis.render.lifecycle.RenderDrawKind;
import io.github.silentcall2598.focalis.render.lifecycle.RenderStage;

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

    boolean ready(RenderDrawKind layer);

    void opened(RenderStage stage, RenderDrawKind layer, DrawSuspension suspension);

    void closed(RenderStage stage, RenderDrawKind layer);

    void reset();
}
