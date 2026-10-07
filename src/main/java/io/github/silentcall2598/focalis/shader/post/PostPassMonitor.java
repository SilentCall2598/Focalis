// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.shader.post;

/**
 * Lets an optional development probe watch the post pass. Every method does nothing by default, and normal play only
 * ever uses {@link #NONE}. Client thread only.
 */
public interface PostPassMonitor {

    PostPassMonitor NONE = new PostPassMonitor() {
    };

    default void programBuilt(int program) {
    }

    default void captureCreated(int framebuffer, int colorTexture, int depthTexture, int width, int height,
            String depthFormat) {
    }

    /** Runs inside the pass after all of its GL state changes, right before it draws. */
    default void beforeDraw() {
    }

    default void passRendered() {
    }

    /** @param reason a short stable key such as {@code anaglyph} */
    default void passSkipped(String reason) {
    }

    default void passStopped(String problem) {
    }
}
