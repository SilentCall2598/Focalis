// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.render.target;

public interface WorldTargetMonitor {

    WorldTargetMonitor NONE = new WorldTargetMonitor() {
    };

    default void targetCreated(int framebuffer, int colorTexture, int depthTexture, int width, int height,
            String depthFormat) {
    }

    default void redirected() {
    }

    /** Runs at the end of the world pass with the copy's framebuffers bound, right before copying. */
    default void beforeCopy() {
    }

    default void skipped(String reason) {
    }

    default void stopped(String reason) {
    }
}
