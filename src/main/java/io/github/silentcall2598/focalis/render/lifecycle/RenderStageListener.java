// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.render.lifecycle;

/** Runs on the client thread with the GL context current. Leave GL state the way you found it. */
@FunctionalInterface
public interface RenderStageListener {

    void onRenderStage(RenderStage stage, RenderPhase phase, float partialTicks);
}
