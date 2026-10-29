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

public final class WorldProgramBinding implements DrawSuspension {

    public static final Set<RenderStage> STAGES = Collections.unmodifiableSet(EnumSet.of(RenderStage.SKY,
            RenderStage.TERRAIN, RenderStage.ENTITIES, RenderStage.BLOCK_DAMAGE, RenderStage.PARTICLES,
            RenderStage.TRANSLUCENT, RenderStage.WEATHER, RenderStage.CLOUDS));

    public static final Set<RenderStage> VANILLA_PROGRAM_STAGES = Collections.unmodifiableSet(
            EnumSet.of(RenderStage.ENTITY_OUTLINES));

    public static final Set<RenderStage> HELD_STAGES = Collections.unmodifiableSet(EnumSet.of(RenderStage.SKY,
            RenderStage.WEATHER, RenderStage.CLOUDS, RenderStage.BLOCK_DAMAGE));

    public static final Set<RenderDrawKind> HELD_SPECIAL_DRAWS = Collections.unmodifiableSet(EnumSet.of(
            RenderDrawKind.ARMOR_GLINT, RenderDrawKind.ITEM_GLINT, RenderDrawKind.EYES, RenderDrawKind.BEACON_BEAM));

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
    private boolean[] streamScopes = new boolean[8];
    private BuiltWorldPrograms.Build[] scopeBuilds = new BuiltWorldPrograms.Build[8];

    private static final byte PASSIVE = 0;
    private static final byte CONTEXT = 1;
    private static final byte SCOPED = 2;
    private byte[] objectModes = new byte[8];
    private boolean[] objectOwnedAtStart = new boolean[8];
    private RenderDrawKind[] objectKinds = new RenderDrawKind[8];
    private int[] objectEntityIds = new int[8];
    private int[] objectBlockEntityIds = new int[8];
    private float[] objectColors = new float[32];
    private int objectDepth;
    private boolean reasserted;
    // Whether the program the scopes own is known to be current, so object values can go on it without asking GL.
    // Cleared whenever code Focalis doesn't control may run or a scope closes.
    private boolean ownedCurrent;

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

    public void select(@Nullable BuiltWorldPrograms selected) {
        if (!scopes.isEmpty() || scopes.isSuspended()) {
            abort();
            throw new IllegalStateException("World programs can't be switched while a program scope is open or"
                    + " suspended");
        }
        objectDepth = 0;
        programs = selected;
    }

    void customValues(@Nullable CustomUniformValues values) {
        customValues = values;
    }

    void terrainStreams(TerrainStreamGate gate) {
        if (!scopes.isEmpty()) {
            abort();
            throw new IllegalStateException("The terrain stream gate can't change while a program scope is open");
        }
        streams = Objects.requireNonNull(gate, "gate");
    }

    @Nullable
    public BuiltWorldPrograms selected() {
        return programs;
    }

    public ScopedProgramState state() {
        return state;
    }

    public void start(RenderStage stage, RenderDrawKind drawKind) {
        start(stage, drawKind, false);
    }

    public void start(RenderStage stage, RenderDrawKind drawKind, boolean vanillaDraws) {
        if (!STAGES.contains(stage)) {
            abort();
            throw new IllegalArgumentException("World programs aren't bound for render stage " + stage);
        }
        reasserted = false;
        BuiltWorldPrograms.Build build = usableBuild(stage, drawKind);
        boolean hold = vanillaDraws && HELD_STAGES.contains(stage)
                && (stage != RenderStage.SKY || drawKind != RenderDrawKind.DEFAULT);
        open(stage, drawKind, build, hold);
        if (build != null) {
            applyObject(build, -1);
        }
    }

    @Nullable
    private BuiltWorldPrograms.Build usableBuild(RenderStage stage, RenderDrawKind drawKind) {
        ShaderProgramRole role = ShaderProgramRouter.route(stage, drawKind);
        BuiltWorldPrograms.Build build = readyBuild(role);
        if (build != null && !build.inputs().accepts(camera.current(), environment, fog)) {
            return null;
        }
        if (build != null) {
            VertexInputs vertex = build.program().vertexInputs();
            if (TERRAIN_ROLES.contains(role)) {
                if (vertex.needsStream() && (vertex.problem() != null || !streams.ready(drawKind))) {
                    return null;
                }
            } else if (vertex.readsStreamAttributes()) {
                return null;
            }
        }
        return build;
    }

    private void open(RenderStage stage, RenderDrawKind drawKind, @Nullable BuiltWorldPrograms.Build build,
            boolean hold) {
        CameraSnapshot snapshot = camera.current();
        boolean terrain = TERRAIN_ROLES.contains(ShaderProgramRouter.route(stage, drawKind));
        boolean stream = build != null && terrain && build.program().vertexInputs().needsStream();
        boolean atlas = terrain || stage == RenderStage.BLOCK_DAMAGE;
        ShaderProgram program = build == null ? null : build.program();
        try {
            scopes.start(stage, drawKind, program);
        } catch (RuntimeException | LinkageError e) {
            abortState(e);
            resetStreams(e);
            throw e;
        }
        markStream(stream);
        scopeBuilds[scopes.depth() - 1] = build;
        try {
            state.start(build == null ? null : build.drawState(), hold);
        } catch (RuntimeException | LinkageError e) {
            abortScopes(e);
            resetStreams(e);
            throw e;
        }
        if (build == null) {
            return;
        }
        try {
            build.inputs().update(frame, snapshot, environment, fog, customValues, atlas);
            ownedCurrent = true;
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

    public void end(RenderStage stage, RenderDrawKind drawKind) {
        reasserted = false;
        if (stage == RenderStage.ENTITIES && objectDepth > 0) {
            dropObjects();
        }
        close(stage, drawKind);
    }

    private boolean close(RenderStage stage, RenderDrawKind drawKind) {
        ownedCurrent = false;
        int innermost = scopes.depth() - 1;
        if (innermost >= 0 && innermost < scopeBuilds.length) {
            scopeBuilds[innermost] = null;
        }
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
            return scopes.end(stage, drawKind);
        } catch (RuntimeException | LinkageError e) {
            abortState(e);
            resetStreams(e);
            throw e;
        }
    }

    public void suspend(RenderStage stage, RenderDrawKind drawKind) {
        checkVanillaStage(stage);
        stepAside(stage, drawKind);
    }

    public void resume(RenderStage stage, RenderDrawKind drawKind) {
        checkVanillaStage(stage);
        stepBack(stage, drawKind);
    }

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
        ownedCurrent = false;
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
        ownedCurrent = bound;
        try {
            state.resume(bound);
        } catch (RuntimeException | LinkageError e) {
            abortScopes(e);
            resetStreams(e);
            throw e;
        }
    }

    public boolean reassert() {
        boolean bound;
        try {
            bound = scopes.reassert();
        } catch (RuntimeException | LinkageError e) {
            abortState(e);
            resetStreams(e);
            throw e;
        }
        ownedCurrent = scopes.ownsProgram();
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

    /**
     * Only for the outermost entity or block entity renderer, so a renderer drawing another inside itself keeps
     * the state it set for its own draws.
     */
    public boolean rendererReturned() {
        boolean bound;
        if (reasserted) {
            reasserted = false;
            bound = false;
        } else {
            bound = reassert();
        }
        if (scopes.ownsProgram()) {
            try {
                state.reestablish();
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
        if (index >= scopeBuilds.length) {
            scopeBuilds = Arrays.copyOf(scopeBuilds, Math.max(index + 1, scopeBuilds.length * 2));
        }
        streamScopes[index] = stream;
    }

    public void objectStart(RenderDrawKind kind, int id) {
        if (!isObject(kind)) {
            abort();
            throw new IllegalArgumentException("Draw kind " + kind + " isn't an entity or block entity");
        }
        reasserted = false;
        int index = pushFrame(kind, id);
        if (scopes.isEmpty() || scopes.isSuspended() || scopes.innermostStage() != RenderStage.ENTITIES) {
            return;
        }
        try {
            if (activeFrameBelow(index) >= 0 && !ownedCurrent) {
                if (!scopes.expectsCurrent()) {
                    return;
                }
                ownedCurrent = scopes.ownsProgram();
            }
            BuiltWorldPrograms.Build owned = ownedBuild();
            BuiltWorldPrograms.Build build = usableBuild(RenderStage.ENTITIES, kind);
            if (build == null || build == owned) {
                objectModes[index] = CONTEXT;
                if (owned != null) {
                    applyObjectIfCurrent(owned, index);
                }
            } else {
                objectModes[index] = SCOPED;
                objectOwnedAtStart[index] = ownedCurrent;
                open(RenderStage.ENTITIES, kind, build, false);
                applyObject(build, index);
            }
        } catch (RuntimeException | LinkageError e) {
            objectDepth = 0;
            throw e;
        }
        ownedCurrent = false;
    }

    public void objectEnd() {
        int top = innermostObject(objectDepth);
        if (top < 0) {
            return;
        }
        reasserted = false;
        try {
            endSpecialsAbove(top);
        } catch (RuntimeException | LinkageError e) {
            objectDepth = 0;
            throw e;
        }
        objectDepth = top;
        byte mode = objectModes[top];
        if (mode == PASSIVE) {
            return;
        }
        try {
            if (mode == SCOPED && close(RenderStage.ENTITIES, objectKinds[top]) && objectOwnedAtStart[top]) {
                ownedCurrent = true;
            } else {
                reassert();
            }
            BuiltWorldPrograms.Build owned = ownedBuild();
            if (owned != null) {
                applyObjectIfCurrent(owned, activeObjectBelow(top));
            }
            reasserted = true;
            if (activeFrameBelow(top) >= 0) {
                ownedCurrent = false;
            }
        } catch (RuntimeException | LinkageError e) {
            objectDepth = 0;
            throw e;
        }
    }

    public void specialStart(RenderDrawKind kind) {
        if (!isSpecial(kind)) {
            abort();
            throw new IllegalArgumentException("Draw kind " + kind + " isn't a special draw");
        }
        reasserted = false;
        int index = pushFrame(kind, -1);
        if (scopes.isEmpty() || scopes.isSuspended() || scopes.innermostStage() != RenderStage.ENTITIES) {
            return;
        }
        try {
            if (activeFrameBelow(index) >= 0 && !ownedCurrent) {
                // A renderer around this one may have bound its own program for what it draws inside, and keeps it.
                if (!scopes.expectsCurrent()) {
                    return;
                }
                ownedCurrent = scopes.ownsProgram();
            }
            BuiltWorldPrograms.Build owned = ownedBuild();
            BuiltWorldPrograms.Build build = usableBuild(RenderStage.ENTITIES, kind);
            if (build == null || build == owned) {
                objectModes[index] = CONTEXT;
            } else {
                objectModes[index] = SCOPED;
                objectOwnedAtStart[index] = ownedCurrent;
                open(RenderStage.ENTITIES, kind, build, HELD_SPECIAL_DRAWS.contains(kind));
                applyObject(build, activeObjectBelow(index));
            }
        } catch (RuntimeException | LinkageError e) {
            objectDepth = 0;
            throw e;
        }
        ownedCurrent = false;
    }

    public void specialEnd(RenderDrawKind kind) {
        int top = objectDepth - 1;
        if (top < 0 || objectKinds[top] != kind || !isSpecial(kind)) {
            return;
        }
        reasserted = false;
        try {
            endSpecial(top);
        } catch (RuntimeException | LinkageError e) {
            objectDepth = 0;
            throw e;
        }
    }

    public void specialsAbandoned() {
        int object = innermostObject(objectDepth);
        if (objectDepth - 1 == object) {
            return;
        }
        reasserted = false;
        try {
            endSpecialsAbove(object);
        } catch (RuntimeException | LinkageError e) {
            objectDepth = 0;
            throw e;
        }
    }

    private void endSpecialsAbove(int index) {
        while (objectDepth - 1 > index) {
            endSpecial(objectDepth - 1);
        }
    }

    private void endSpecial(int top) {
        objectDepth = top;
        if (objectModes[top] != SCOPED) {
            return;
        }
        if (close(RenderStage.ENTITIES, objectKinds[top]) && objectOwnedAtStart[top]) {
            ownedCurrent = true;
        }
        BuiltWorldPrograms.Build owned = ownedBuild();
        if (owned != null) {
            applyObjectIfCurrent(owned, activeObjectBelow(top));
        }
        if (activeFrameBelow(top) >= 0) {
            ownedCurrent = false;
        }
    }

    public void objectColor(float red, float green, float blue, float alpha) {
        int top = innermostObject(objectDepth);
        if (top < 0) {
            return;
        }
        int at = top * 4;
        if (objectColors[at] == red && objectColors[at + 1] == green && objectColors[at + 2] == blue
                && objectColors[at + 3] == alpha) {
            return;
        }
        objectColors[at] = red;
        objectColors[at + 1] = green;
        objectColors[at + 2] = blue;
        objectColors[at + 3] = alpha;
        if (objectModes[top] == PASSIVE) {
            return;
        }
        BuiltWorldPrograms.Build owned = ownedBuild();
        if (owned != null) {
            applyObjectIfCurrent(owned, top);
            ownedCurrent = false;
        }
    }

    public int objectDepth() {
        return objectDepth;
    }

    private static boolean isObject(RenderDrawKind kind) {
        return kind == RenderDrawKind.ENTITY || isBlockEntity(kind);
    }

    private static boolean isBlockEntity(RenderDrawKind kind) {
        return kind == RenderDrawKind.BLOCK_ENTITY || kind == RenderDrawKind.DAMAGED_BLOCK_ENTITY;
    }

    private static boolean isSpecial(RenderDrawKind kind) {
        return kind == RenderDrawKind.ARMOR_GLINT || kind == RenderDrawKind.ITEM_GLINT || kind == RenderDrawKind.EYES
                || kind == RenderDrawKind.BEACON_BEAM;
    }

    private int pushFrame(RenderDrawKind kind, int id) {
        int index = objectDepth;
        if (index == objectModes.length) {
            int grown = index * 2;
            objectModes = Arrays.copyOf(objectModes, grown);
            objectOwnedAtStart = Arrays.copyOf(objectOwnedAtStart, grown);
            objectKinds = Arrays.copyOf(objectKinds, grown);
            objectEntityIds = Arrays.copyOf(objectEntityIds, grown);
            objectBlockEntityIds = Arrays.copyOf(objectBlockEntityIds, grown);
            objectColors = Arrays.copyOf(objectColors, grown * 4);
        }
        objectModes[index] = PASSIVE;
        objectOwnedAtStart[index] = false;
        objectKinds[index] = kind;
        objectEntityIds[index] = kind == RenderDrawKind.ENTITY ? id : -1;
        objectBlockEntityIds[index] = isBlockEntity(kind) ? id : -1;
        Arrays.fill(objectColors, index * 4, index * 4 + 4, 0f);
        objectDepth = index + 1;
        return index;
    }

    private int activeObjectBelow(int index) {
        for (int i = index - 1; i >= 0; i--) {
            if (objectModes[i] != PASSIVE && isObject(objectKinds[i])) {
                return i;
            }
        }
        return -1;
    }

    private int activeFrameBelow(int index) {
        for (int i = index - 1; i >= 0; i--) {
            if (objectModes[i] != PASSIVE) {
                return i;
            }
        }
        return -1;
    }

    private int innermostObject(int depth) {
        for (int i = depth - 1; i >= 0; i--) {
            if (isObject(objectKinds[i])) {
                return i;
            }
        }
        return -1;
    }

    private void applyObjectIfCurrent(BuiltWorldPrograms.Build build, int index) {
        WorldProgramInputs inputs = build.inputs();
        boolean differs = index < 0 ? inputs.objectDiffers(-1, -1, 0f, 0f, 0f, 0f)
                : inputs.objectDiffers(objectEntityIds[index], objectBlockEntityIds[index], objectColors[index * 4],
                        objectColors[index * 4 + 1], objectColors[index * 4 + 2], objectColors[index * 4 + 3]);
        if (!differs) {
            return;
        }
        if (!ownedCurrent && !scopes.ownsCurrent()) {
            return;
        }
        ownedCurrent = true;
        applyObject(build, index);
    }

    private void applyObject(BuiltWorldPrograms.Build build, int index) {
        if (index < 0) {
            build.inputs().object(-1, -1, 0f, 0f, 0f, 0f);
            return;
        }
        int at = index * 4;
        build.inputs().object(objectEntityIds[index], objectBlockEntityIds[index], objectColors[at],
                objectColors[at + 1], objectColors[at + 2], objectColors[at + 3]);
    }

    @Nullable
    private BuiltWorldPrograms.Build ownedBuild() {
        for (int i = scopes.depth() - 1; i >= 0; i--) {
            if (i < scopeBuilds.length && scopeBuilds[i] != null) {
                return scopeBuilds[i];
            }
        }
        return null;
    }

    private void dropObjects() {
        while (objectDepth > 0) {
            int top = --objectDepth;
            if (objectModes[top] == SCOPED) {
                close(RenderStage.ENTITIES, objectKinds[top]);
            }
        }
    }

    public boolean isEmpty() {
        return scopes.isEmpty();
    }

    public boolean isSuspended() {
        return scopes.isSuspended();
    }

    public void abort() {
        objectDepth = 0;
        reasserted = false;
        ownedCurrent = false;
        Arrays.fill(scopeBuilds, null);
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
