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
 * its END. A role without a ready program gets a scope that binds nothing. Where vanilla binds its own programs
 * inside a stage, it steps aside until that part is over. It never owns, builds or deletes programs. Client thread
 * only, with the GL context current.
 */
public final class WorldProgramBinding {

    /**
     * The stages bound so far. All of them happen inside the world pass. HAND comes after the world pass ends, so
     * its program would run outside the world target, and it isn't bound yet. Read only.
     */
    public static final Set<RenderStage> STAGES = Collections.unmodifiableSet(EnumSet.of(RenderStage.SKY,
            RenderStage.TERRAIN, RenderStage.ENTITIES, RenderStage.PARTICLES, RenderStage.TRANSLUCENT,
            RenderStage.WEATHER, RenderStage.CLOUDS));

    /**
     * Stages inside a bound stage where vanilla binds its own shader programs, so the open scopes step aside until
     * they end. Read only.
     */
    public static final Set<RenderStage> VANILLA_PROGRAM_STAGES = Collections.unmodifiableSet(
            EnumSet.of(RenderStage.ENTITY_OUTLINES));

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

    /**
     * Steps aside when a stage where vanilla binds its own programs starts. See {@link ScopedProgramBinding#suspend}.
     *
     * @throws IllegalArgumentException for a stage that isn't one of {@link #VANILLA_PROGRAM_STAGES}
     */
    public void suspend(RenderStage stage, RenderDrawKind drawKind) {
        checkVanillaStage(stage);
        scopes.suspend(stage, drawKind);
    }

    /** Takes the program back when that stage ends. See {@link ScopedProgramBinding#resume}. */
    public void resume(RenderStage stage, RenderDrawKind drawKind) {
        checkVanillaStage(stage);
        scopes.resume(stage, drawKind);
    }

    /**
     * Takes the program back after one renderer inside a bound stage returned with something else bound. See
     * {@link ScopedProgramBinding#reassert}.
     */
    public boolean reassert() {
        return scopes.reassert();
    }

    private void checkVanillaStage(RenderStage stage) {
        if (!VANILLA_PROGRAM_STAGES.contains(stage)) {
            scopes.abort();
            throw new IllegalArgumentException("Render stage " + stage + " doesn't bind vanilla programs");
        }
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

    public boolean isSuspended() {
        return scopes.isSuspended();
    }

    /** Closes every open scope and restores the program each one replaced. See {@link ScopedProgramBinding#abort}. */
    public void abort() {
        scopes.abort();
    }
}
