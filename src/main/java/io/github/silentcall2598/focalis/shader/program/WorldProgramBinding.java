// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.shader.program;

import io.github.silentcall2598.focalis.render.lifecycle.RenderDrawKind;
import io.github.silentcall2598.focalis.render.lifecycle.RenderStage;
import io.github.silentcall2598.focalis.shader.routing.ShaderProgramRole;
import io.github.silentcall2598.focalis.shader.routing.ShaderProgramRouter;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * Binds the built world program of each precise world stage around its draw and restores the previous program at
 * its END. A role without a ready program gets a scope that binds nothing. It never owns, builds or deletes
 * programs. Client thread only, with the GL context current.
 */
public final class WorldProgramBinding {

    /**
     * The stages bound so far. All of them happen inside the world pass. HAND comes after the world pass ends, so
     * its program would run outside the world target, and it isn't bound yet. Read only.
     */
    public static final Set<RenderStage> STAGES = Collections.unmodifiableSet(EnumSet.of(RenderStage.SKY,
            RenderStage.TERRAIN, RenderStage.ENTITIES, RenderStage.PARTICLES, RenderStage.TRANSLUCENT,
            RenderStage.WEATHER, RenderStage.CLOUDS));

    private final BuiltWorldPrograms programs;
    private final ScopedProgramBinding scopes;

    public WorldProgramBinding(BuiltWorldPrograms programs) {
        this(programs, new ScopedProgramBinding());
    }

    WorldProgramBinding(BuiltWorldPrograms programs, ScopedProgramBinding scopes) {
        this.programs = Objects.requireNonNull(programs, "programs");
        this.scopes = scopes;
    }

    /**
     * Opens the program scope of a stage. Like {@link ScopedProgramBinding}, every open scope is unwound before
     * anything is thrown.
     *
     * @throws IllegalArgumentException for a stage that isn't bound yet, like HAND
     */
    public void start(RenderStage stage, RenderDrawKind drawKind) {
        if (!STAGES.contains(stage)) {
            scopes.abort();
            throw new IllegalArgumentException("World programs aren't bound for render stage " + stage);
        }
        scopes.start(stage, drawKind, program(ShaderProgramRouter.route(stage, drawKind)));
    }

    public void end(RenderStage stage, RenderDrawKind drawKind) {
        scopes.end(stage, drawKind);
    }

    /** The program a role binds, or null when it has none ready and the stage draws the way it did before. */
    @Nullable
    public ShaderProgram program(ShaderProgramRole role) {
        BuiltWorldPrograms.Entry entry = programs.forRole(role);
        return entry.ready() ? entry.program() : null;
    }

    public boolean isEmpty() {
        return scopes.isEmpty();
    }

    /** Closes every open scope and restores the program each one replaced. See {@link ScopedProgramBinding#abort}. */
    public void abort() {
        scopes.abort();
    }
}
