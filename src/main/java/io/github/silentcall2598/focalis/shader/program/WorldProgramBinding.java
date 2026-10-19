// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.shader.program;

import io.github.silentcall2598.focalis.render.lifecycle.RenderDrawKind;
import io.github.silentcall2598.focalis.render.lifecycle.RenderStage;
import io.github.silentcall2598.focalis.shader.routing.ShaderProgramRole;
import io.github.silentcall2598.focalis.shader.routing.ShaderProgramRouter;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * Binds the built world program of each precise world stage around its draw and restores the previous program at
 * its END, using the programs selected for the current world pass. The program's alpha test, blend mode and custom
 * textures go with it and are put back the same way. A role without a ready program, or a pass without selected
 * programs, gets a scope that binds nothing. Where vanilla binds its own programs inside a stage, it steps aside
 * until that part is over. Terrain programs that read inputs only the chunk attribute stream supplies are bound only
 * while the stream can feed them, and a single chunk draw without a usable stream steps aside too. Programs of other
 * roles that read those inputs aren't bound at all. It never owns, builds or deletes programs or textures. Client
 * thread only, with the GL context current.
 */
public final class WorldProgramBinding implements DrawSuspension {

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

    /** The roles that draw chunk terrain, the only draws the chunk attribute stream can feed. Read only. */
    public static final Set<ShaderProgramRole> TERRAIN_ROLES = Collections.unmodifiableSet(EnumSet.of(
            ShaderProgramRole.TERRAIN_SOLID, ShaderProgramRole.TERRAIN_CUTOUT_MIPPED, ShaderProgramRole.TERRAIN_CUTOUT,
            ShaderProgramRole.TERRAIN_TRANSLUCENT));

    private final ScopedProgramBinding scopes;
    private final ScopedProgramState state;
    private final FrameInputs frame;
    private final CameraInputs camera;
    private final EnvironmentInputs environment;
    private final FogSource fog;
    @Nullable
    private BuiltWorldPrograms programs;
    @Nullable
    private CustomUniformValues customValues;
    private TerrainStreamGate streams = TerrainStreamGate.CLOSED;
    // Per open scope, innermost last like the program scopes, whether it told the stream gate it needs the stream.
    private boolean[] streamScopes = new boolean[8];

    WorldProgramBinding(ScopedProgramBinding scopes, FrameInputs frame, CameraInputs camera,
            EnvironmentInputs environment, FogSource fog) {
        this(scopes, new ScopedProgramState(), frame, camera, environment, fog);
    }

    WorldProgramBinding(ScopedProgramBinding scopes, ScopedProgramState state, FrameInputs frame, CameraInputs camera,
            EnvironmentInputs environment, FogSource fog) {
        this.scopes = Objects.requireNonNull(scopes, "scopes");
        this.state = Objects.requireNonNull(state, "state");
        this.frame = Objects.requireNonNull(frame, "frame");
        this.camera = Objects.requireNonNull(camera, "camera");
        this.environment = Objects.requireNonNull(environment, "environment");
        this.fog = Objects.requireNonNull(fog, "fog");
    }

    /**
     * Switches the programs the stages bind, or to none. Only allowed while no scope is open or suspended, so every
     * scope ends with the programs it started with. Otherwise every open scope is unwound and this throws.
     */
    public void select(@Nullable BuiltWorldPrograms selected) {
        if (!scopes.isEmpty() || scopes.isSuspended()) {
            abort();
            throw new IllegalStateException("World programs can't be switched while a program scope is open or"
                    + " suspended");
        }
        programs = selected;
    }

    /** The custom uniform values the programs of this binding get, or null when there are none. */
    void customValues(@Nullable CustomUniformValues values) {
        customValues = values;
    }

    /** Where terrain programs that need the chunk attribute stream ask for it. Only while no scope is open. */
    void terrainStreams(TerrainStreamGate gate) {
        if (!scopes.isEmpty()) {
            abort();
            throw new IllegalStateException("The terrain stream gate can't change while a program scope is open");
        }
        streams = Objects.requireNonNull(gate, "gate");
    }

    /** The programs the stages bind, or null when none are selected. */
    @Nullable
    public BuiltWorldPrograms selected() {
        return programs;
    }

    /** The alpha test, blend mode and texture changes of the open scopes. */
    public ScopedProgramState state() {
        return state;
    }

    /**
     * Opens the program scope of a stage. A program bound for the first time in the current frame gets that frame's
     * values first, and the camera values the first time it's bound with the current pass's camera. The fog it gets
     * is whatever Minecraft has set up when the stage starts. Its alpha test, blend mode and textures are set after
     * it's bound. A program that can't use the current values, like one reading the camera before this world pass
     * captured it, binds nothing, the same as a role without a ready program. So does a terrain program that needs
     * the chunk attribute stream while it can't feed it, and any other role that reads attributes only that stream
     * supplies. The stage then draws the vanilla way, or with the program of the scope around it, like the sun inside
     * the sky. Like {@link ScopedProgramBinding}, every open scope is unwound before anything is thrown.
     *
     * @throws IllegalArgumentException for a stage that isn't bound yet, like HAND
     */
    public void start(RenderStage stage, RenderDrawKind drawKind) {
        if (!STAGES.contains(stage)) {
            abort();
            throw new IllegalArgumentException("World programs aren't bound for render stage " + stage);
        }
        ShaderProgramRole role = ShaderProgramRouter.route(stage, drawKind);
        BuiltWorldPrograms.Build build = readyBuild(role);
        CameraSnapshot snapshot = camera.current();
        if (build != null && !build.inputs().accepts(snapshot, environment, fog)) {
            build = null;
        }
        boolean stream = false;
        if (build != null) {
            VertexInputs vertex = build.program().vertexInputs();
            if (TERRAIN_ROLES.contains(role)) {
                // Vanilla chunk data has no normals or IDs, so without the stream the program would read whatever
                // GL had left. The layer draws vanilla instead.
                if (vertex.needsStream() && (vertex.problem() != null || !streams.ready(drawKind))) {
                    build = null;
                } else {
                    stream = vertex.needsStream();
                }
            } else if (vertex.readsStreamAttributes()) {
                // Only chunk terrain supplies these. Other draws would feed the program meaningless values.
                build = null;
            }
        }
        ShaderProgram program = build == null ? null : build.program();
        try {
            scopes.start(stage, drawKind, program);
        } catch (RuntimeException | LinkageError e) {
            // The program scopes already unwound themselves.
            abortState(e);
            resetStreams(e);
            throw e;
        }
        markStream(stream);
        try {
            state.start(build == null ? null : build.drawState());
        } catch (RuntimeException | LinkageError e) {
            abortScopes(e);
            resetStreams(e);
            throw e;
        }
        if (build == null) {
            return;
        }
        // The scope just bound the program or found it current already. Either way it's current now. Programs
        // bound again later in the frame, like after a renderer or outlines, still hold these values.
        try {
            build.inputs().update(frame, snapshot, environment, fog, customValues);
            if (stream) {
                streams.opened(stage, drawKind, this);
            }
        } catch (RuntimeException | LinkageError e) {
            abortState(e);
            abortScopes(e);
            resetStreams(e);
            throw e;
        }
    }

    /** Closes the program scope of a stage. Its state goes back first, then its program. */
    public void end(RenderStage stage, RenderDrawKind drawKind) {
        int innermost = scopes.depth() - 1;
        if (innermost >= 0 && innermost < streamScopes.length && streamScopes[innermost]) {
            streamScopes[innermost] = false;
            try {
                streams.closed(stage, drawKind);
            } catch (RuntimeException | LinkageError e) {
                abortState(e);
                abortScopes(e);
                resetStreams(e);
                throw e;
            }
        }
        try {
            state.end();
        } catch (RuntimeException | LinkageError e) {
            abortScopes(e);
            resetStreams(e);
            throw e;
        }
        try {
            scopes.end(stage, drawKind);
        } catch (RuntimeException | LinkageError e) {
            abortState(e);
            resetStreams(e);
            throw e;
        }
    }

    /**
     * Steps aside when a stage where vanilla binds its own programs starts, putting back the state the open scopes
     * changed. See {@link ScopedProgramBinding#suspend}.
     *
     * @throws IllegalArgumentException for a stage that isn't one of {@link #VANILLA_PROGRAM_STAGES}
     */
    public void suspend(RenderStage stage, RenderDrawKind drawKind) {
        checkVanillaStage(stage);
        stepAside(stage, drawKind);
    }

    /**
     * Takes the program back when that stage ends, and its state when the program came back. See
     * {@link ScopedProgramBinding#resume}.
     */
    public void resume(RenderStage stage, RenderDrawKind drawKind) {
        checkVanillaStage(stage);
        stepBack(stage, drawKind);
    }

    /**
     * Steps aside for one chunk draw inside a terrain stage, the same way as for vanilla's own programs, so the chunk
     * draws vanilla. Only for the innermost open scope, which has to be that terrain stage.
     */
    @Override
    public void suspendForDraw(RenderStage stage, RenderDrawKind drawKind) {
        checkTerrainStage(stage);
        stepAside(stage, drawKind);
    }

    @Override
    public void resumeAfterDraw(RenderStage stage, RenderDrawKind drawKind) {
        checkTerrainStage(stage);
        stepBack(stage, drawKind);
    }

    private void stepAside(RenderStage stage, RenderDrawKind drawKind) {
        try {
            state.suspend();
        } catch (RuntimeException | LinkageError e) {
            abortScopes(e);
            resetStreams(e);
            throw e;
        }
        try {
            scopes.suspend(stage, drawKind);
        } catch (RuntimeException | LinkageError e) {
            abortState(e);
            resetStreams(e);
            throw e;
        }
    }

    private void stepBack(RenderStage stage, RenderDrawKind drawKind) {
        boolean bound;
        try {
            bound = scopes.resume(stage, drawKind);
        } catch (RuntimeException | LinkageError e) {
            abortState(e);
            resetStreams(e);
            throw e;
        }
        try {
            state.resume(bound);
        } catch (RuntimeException | LinkageError e) {
            abortScopes(e);
            resetStreams(e);
            throw e;
        }
    }

    /**
     * Takes the program back after one renderer inside a bound stage returned with something else bound. See
     * {@link ScopedProgramBinding#reassert}. The alpha test, blend mode and textures aren't checked here, they only
     * come back if an earlier resume left them off.
     */
    public boolean reassert() {
        boolean bound;
        try {
            bound = scopes.reassert();
        } catch (RuntimeException | LinkageError e) {
            abortState(e);
            resetStreams(e);
            throw e;
        }
        if (bound) {
            try {
                state.programBoundAgain();
            } catch (RuntimeException | LinkageError e) {
                abortScopes(e);
                resetStreams(e);
                throw e;
            }
        }
        return bound;
    }

    private void checkVanillaStage(RenderStage stage) {
        if (!VANILLA_PROGRAM_STAGES.contains(stage)) {
            abort();
            throw new IllegalArgumentException("Render stage " + stage + " doesn't bind vanilla programs");
        }
    }

    private void checkTerrainStage(RenderStage stage) {
        if (stage != RenderStage.TERRAIN && stage != RenderStage.TRANSLUCENT) {
            abort();
            throw new IllegalArgumentException("Render stage " + stage + " has no chunk draws to step aside for");
        }
    }

    /**
     * The program a role binds, or null when nothing is selected or the role has none ready, and the stage draws the
     * way it did before.
     */
    @Nullable
    public ShaderProgram program(ShaderProgramRole role) {
        BuiltWorldPrograms.Build build = readyBuild(role);
        return build == null ? null : build.program();
    }

    @Nullable
    private BuiltWorldPrograms.Build readyBuild(ShaderProgramRole role) {
        BuiltWorldPrograms selected = programs;
        if (selected == null) {
            return null;
        }
        BuiltWorldPrograms.Entry entry = selected.forRole(role);
        return entry.ready() ? entry.build() : null;
    }

    private void markStream(boolean stream) {
        int index = scopes.depth() - 1;
        if (index >= streamScopes.length) {
            streamScopes = Arrays.copyOf(streamScopes, Math.max(index + 1, streamScopes.length * 2));
        }
        streamScopes[index] = stream;
    }

    public boolean isEmpty() {
        return scopes.isEmpty();
    }

    public boolean isSuspended() {
        return scopes.isSuspended();
    }

    /**
     * Closes every open scope and puts back the state and program each one replaced. Both are attempted even when
     * one throws, and the first failure is rethrown. See {@link ScopedProgramBinding#abort}.
     */
    public void abort() {
        Throwable failure = null;
        try {
            state.abort();
        } catch (RuntimeException | LinkageError e) {
            failure = e;
        }
        try {
            scopes.abort();
        } catch (RuntimeException | LinkageError e) {
            if (failure == null) {
                failure = e;
            } else {
                failure.addSuppressed(e);
            }
        }
        Arrays.fill(streamScopes, false);
        try {
            streams.reset();
        } catch (RuntimeException | LinkageError e) {
            if (failure == null) {
                failure = e;
            } else {
                failure.addSuppressed(e);
            }
        }
        if (failure instanceof RuntimeException) {
            throw (RuntimeException) failure;
        }
        if (failure instanceof LinkageError) {
            throw (LinkageError) failure;
        }
    }

    private void abortState(Throwable cause) {
        try {
            state.abort();
        } catch (RuntimeException | LinkageError e) {
            if (e != cause) {
                cause.addSuppressed(e);
            }
        }
    }

    private void abortScopes(Throwable cause) {
        try {
            scopes.abort();
        } catch (RuntimeException | LinkageError e) {
            if (e != cause) {
                cause.addSuppressed(e);
            }
        }
    }

    private void resetStreams(Throwable cause) {
        Arrays.fill(streamScopes, false);
        try {
            streams.reset();
        } catch (RuntimeException | LinkageError e) {
            if (e != cause) {
                cause.addSuppressed(e);
            }
        }
    }
}
