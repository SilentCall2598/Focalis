// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.render.target;

/**
 * Lets an optional development probe watch the world target. Every method does nothing by default, and normal play
 * only ever uses {@link #NONE}. Client thread only.
 */
public interface WorldTargetMonitor {

    WorldTargetMonitor NONE = new WorldTargetMonitor() {
    };

    default void targetCreated(int framebuffer, int colorTexture, int depthTexture, int width, int height,
            String depthFormat) {
    }

    /** The world pass that just started draws into the target. */
    default void redirected() {
    }

    /** Runs at the end of the world pass with the copy's framebuffers bound, right before copying. */
    default void beforeCopy() {
    }

    /** @param reason a short stable key such as {@code other-target} */
    default void skipped(String reason) {
    }

    default void stopped(String reason) {
    }
}
