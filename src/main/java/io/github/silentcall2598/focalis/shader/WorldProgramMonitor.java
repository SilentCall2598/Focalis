// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.shader;

import io.github.silentcall2598.focalis.render.lifecycle.RenderDrawKind;
import io.github.silentcall2598.focalis.render.lifecycle.RenderStage;
import io.github.silentcall2598.focalis.shader.pack.ProgramDirectory;
import io.github.silentcall2598.focalis.shader.program.BuiltWorldPrograms;
import io.github.silentcall2598.focalis.shader.program.CameraSnapshot;

/**
 * Lets an optional development probe watch the world programs. Every method does nothing by default, and normal play
 * only ever uses {@link #NONE}. Client thread only.
 */
public interface WorldProgramMonitor {

    WorldProgramMonitor NONE = new WorldProgramMonitor() {
    };

    /** The programs of one folder were built, which happens once per folder and session. */
    default void programsBuilt(ProgramDirectory directory, BuiltWorldPrograms programs) {
    }

    /**
     * A world pass selected other programs than the one before, or the same ones for another dimension. Runs before
     * any of its stages.
     */
    default void programsSelected(int dimension, ProgramDirectory directory, BuiltWorldPrograms programs) {
    }

    /** The camera of a world pass was just captured, right before its first world stage binds anything. */
    default void cameraCaptured(RenderDrawKind drawKind, CameraSnapshot snapshot) {
    }

    /** Runs right after a stage's program scope opened, before vanilla draws. */
    default void scopeStarted(RenderStage stage, RenderDrawKind drawKind) {
    }

    default void stopped(String reason) {
    }
}
