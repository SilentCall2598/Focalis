// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.shader;

import io.github.silentcall2598.focalis.render.lifecycle.RenderDrawKind;
import io.github.silentcall2598.focalis.render.lifecycle.RenderStage;
import io.github.silentcall2598.focalis.shader.program.BuiltWorldPrograms;

/**
 * Lets development QA tooling watch the world programs. Every method does nothing by default, and normal play only
 * ever uses {@link #NONE}. Client thread only.
 */
public interface WorldProgramMonitor {

    WorldProgramMonitor NONE = new WorldProgramMonitor() {
    };

    /** The programs were built, and the ones that work are about to be bound. */
    default void programsBuilt(BuiltWorldPrograms programs) {
    }

    /** Runs right after a stage's program scope opened, before vanilla draws. */
    default void scopeStarted(RenderStage stage, RenderDrawKind drawKind) {
    }

    default void stopped(String reason) {
    }
}
