// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.shader.session;

import io.github.silentcall2598.focalis.shader.material.MaterialIds;
import io.github.silentcall2598.focalis.shader.program.TerrainStreamGate;
import io.github.silentcall2598.focalis.shader.terrain.TerrainStreams;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nullable;

interface StreamControl {

    StreamControl TERRAIN_STREAMS = new StreamControl() {
        @Override
        public TerrainStreamGate gate() {
            return TerrainStreams.gate();
        }

        @Override
        public boolean publish(MaterialIds ids) {
            return TerrainStreams.publish(ids);
        }

        @Override
        public void withdraw(boolean reload) {
            TerrainStreams.withdraw(reload);
        }

        @Override
        public boolean active() {
            return TerrainStreams.active();
        }

        @Nullable
        @Override
        public Throwable failure() {
            return TerrainStreams.failure();
        }

        @Override
        public void prepare(Logger logger) {
            TerrainStreams.prepare(logger);
        }
    };

    TerrainStreamGate gate();

    boolean publish(MaterialIds ids);

    void withdraw(boolean reload);

    boolean active();

    @Nullable
    Throwable failure();

    void prepare(Logger logger);
}
