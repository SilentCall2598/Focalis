// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.render.lifecycle;

/**
 * Runs on the client thread with the GL context current, once per renderer on a hot path, so it has to be cheap and
 * must not allocate.
 */
@FunctionalInterface
public interface RenderCheckpointListener {

    void onCheckpoint(RenderCheckpoint checkpoint);
}
