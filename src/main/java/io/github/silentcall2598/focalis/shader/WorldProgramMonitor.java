// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.shader;

import io.github.silentcall2598.focalis.render.lifecycle.RenderDrawKind;
import io.github.silentcall2598.focalis.render.lifecycle.RenderStage;
import io.github.silentcall2598.focalis.shader.material.MaterialIds;
import io.github.silentcall2598.focalis.shader.pack.PackConfiguration;
import io.github.silentcall2598.focalis.shader.pack.ProgramDirectory;
import io.github.silentcall2598.focalis.shader.program.BuiltWorldPrograms;
import io.github.silentcall2598.focalis.shader.program.CameraSnapshot;
import io.github.silentcall2598.focalis.shader.program.EnvironmentInputs;
import io.github.silentcall2598.focalis.shader.program.ProgramTextures;

import javax.annotation.Nullable;

public interface WorldProgramMonitor {

    WorldProgramMonitor NONE = new WorldProgramMonitor() {
    };

    default void packConfigured(PackConfiguration configuration) {
    }

    default void texturesMade(ProgramTextures textures) {
    }

    default void materialsResolved(MaterialIds ids) {
    }

    default void programsBuilt(ProgramDirectory directory, BuiltWorldPrograms programs) {
    }

    default void programsSelected(int dimension, ProgramDirectory directory, BuiltWorldPrograms programs) {
    }

    default void cameraCaptured(RenderDrawKind drawKind, CameraSnapshot snapshot) {
    }

    default void environmentCaptured(CameraSnapshot snapshot, EnvironmentInputs environment) {
    }

    default void scopeStarted(RenderStage stage, RenderDrawKind drawKind) {
    }

    /** {@code object} is the entity or block entity, or null for the batch of fast block entity renderers. */
    default void objectStarted(RenderDrawKind kind, @Nullable Object object, int id) {
    }

    default void objectEnded() {
    }

    default void specialDrawStarted(RenderDrawKind kind) {
    }

    /**
     * Runs right after a special draw ended and the object's program is back. The kind is DEFAULT after a failure ended
     * every special draw of the innermost object.
     */
    default void specialDrawEnded(RenderDrawKind kind) {
    }

    default void stopped(String reason) {
    }
}
