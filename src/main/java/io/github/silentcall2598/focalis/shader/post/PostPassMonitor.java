// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.shader.post;

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

    default void passSkipped(String reason) {
    }

    default void passStopped(String problem) {
    }
}
