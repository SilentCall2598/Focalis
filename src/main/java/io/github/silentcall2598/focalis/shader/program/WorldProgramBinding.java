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
 * Binds the built world program of each precise world stage around its draw and restores the previous program at its
 * END, using the programs selected for the current world pass. The program's alpha test, blend mode and custom textures
 * go with it and are put back the same way. In the sky, weather and clouds vanilla draws, the directives are held
 * against vanilla's own alpha and blend calls, and after each outermost entity or block entity renderer the directives
 * and textures come back where the renderer changed them. A role without a ready program, or a pass without selected
 * programs, gets a scope that binds nothing. Where vanilla binds its own programs inside a stage, it steps aside until
 * that part is over. Terrain programs that read inputs only the chunk attribute stream supplies are bound only while
 * the stream can feed them, and a single chunk draw without a usable stream steps aside too. Programs of other roles
 * that read those inputs aren't bound at all. Inside the entity passes, each entity and block entity, and each special
 * draw nested in one like armor glint, gets its role's program the same nested way. It never owns, builds or deletes
 * programs or textures. Client thread only, with the GL context current.
 */
public final class WorldProgramBinding implements DrawSuspension {

    /**
     * The stages bound so far. All of them happen inside the world pass. HAND comes after the world pass ends, so
     * its program would run outside the world target, and it isn't bound yet. Read only.
     */
    public static final Set<RenderStage> STAGES = Collections.unmodifiableSet(EnumSet.of(RenderStage.SKY,
            RenderStage.TERRAIN, RenderStage.ENTITIES, RenderStage.BLOCK_DAMAGE, RenderStage.PARTICLES,
            RenderStage.TRANSLUCENT, RenderStage.WEATHER, RenderStage.CLOUDS));

    /**
     * Stages inside a bound stage where vanilla binds its own shader programs, so the open scopes step aside until
     * they end. Read only.
     */
    public static final Set<RenderStage> VANILLA_PROGRAM_STAGES = Collections.unmodifiableSet(
            EnumSet.of(RenderStage.ENTITY_OUTLINES));

    /**
     * The stages whose vanilla code sets the alpha test and blending itself between the draws of the program bound,
     * like the weather's alpha function or the sky turning the alpha test off for the sunrise and the stars. Their
     * scopes hold the program's directives, see {@link ScopedProgramState#start(ProgramDrawState, boolean)}, so the
     * directive is what the draws get. Read only.
     */
    public static final Set<RenderStage> HELD_STAGES = Collections.unmodifiableSet(EnumSet.of(RenderStage.SKY,
            RenderStage.WEATHER, RenderStage.CLOUDS));

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
    // Per open scope, the build it bound, or null for one that bound nothing.
    private BuiltWorldPrograms.Build[] scopeBuilds = new BuiltWorldPrograms.Build[8];

    // The entities, block entities and special draws being drawn, innermost last. Arrays so an object allocates
    // nothing. A special draw's frame has no values of its own, it shows the innermost object's.
    private static final byte PASSIVE = 0;
    private static final byte CONTEXT = 1;
    private static final byte SCOPED = 2;
    private byte[] objectModes = new byte[8];
    // For an object with its own scope, whether the scopes' program was known to be current when it opened.
    private boolean[] objectOwnedAtStart = new boolean[8];
    private RenderDrawKind[] objectKinds = new RenderDrawKind[8];
    private int[] objectEntityIds = new int[8];
    private int[] objectBlockEntityIds = new int[8];
    private float[] objectColors = new float[32];
    private int objectDepth;
    // Set when an object's end just bound the scopes' program again, so the renderer checkpoint right after can skip it.
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
        // Objects drawn outside any scope, like in a screen, whose end never came.
        objectDepth = 0;
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
        start(stage, drawKind, false);
    }

    /**
     * Like {@link #start(RenderStage, RenderDrawKind)}. With {@code vanillaDraws}, the stage is drawn by vanilla's own
     * code, so a scope of the {@link #HELD_STAGES} holds its program's directives. A sky Focalis classifies is always
     * vanilla's, but the weather and the clouds can come from a dimension's own renderer, whose state is its own.
     */
    public void start(RenderStage stage, RenderDrawKind drawKind, boolean vanillaDraws) {
        if (!STAGES.contains(stage)) {
            abort();
            throw new IllegalArgumentException("World programs aren't bound for render stage " + stage);
        }
        reasserted = false;
        BuiltWorldPrograms.Build build = usableBuild(stage, drawKind);
        // A DEFAULT sky is a dimension's own renderer or the End, which bind nothing anyway.
        boolean hold = vanillaDraws && HELD_STAGES.contains(stage)
                && (stage != RenderStage.SKY || drawKind != RenderDrawKind.DEFAULT);
        open(stage, drawKind, build, hold);
        // Nothing drawn by the stage itself is an entity or block entity.
        if (build != null) {
            applyObject(build, -1);
        }
    }

    // The build a stage binds, or null when it binds nothing.
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
                // Vanilla chunk data has no normals or IDs, so without the stream the program would read whatever
                // GL had left. The layer draws vanilla instead.
                if (vertex.needsStream() && (vertex.problem() != null || !streams.ready(drawKind))) {
                    return null;
                }
            } else if (vertex.readsStreamAttributes()) {
                // Only chunk terrain supplies these. Other draws would feed the program meaningless values.
                return null;
            }
        }
        return build;
    }

    private void open(RenderStage stage, RenderDrawKind drawKind, @Nullable BuiltWorldPrograms.Build build,
            boolean hold) {
        CameraSnapshot snapshot = camera.current();
        boolean stream = build != null && TERRAIN_ROLES.contains(ShaderProgramRouter.route(stage, drawKind))
                && build.program().vertexInputs().needsStream();
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
        // The scope just bound the program or found it current already. Either way it's current now. Programs
        // bound again later in the frame, like after a renderer or outlines, still hold these values.
        try {
            build.inputs().update(frame, snapshot, environment, fog, customValues);
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

    /**
     * Closes the program scope of a stage. Its state goes back first, then its program. An entity pass still holding
     * objects whose end never came closes them first, innermost first.
     */
    public void end(RenderStage stage, RenderDrawKind drawKind) {
        reasserted = false;
        if (stage == RenderStage.ENTITIES && objectDepth > 0) {
            dropObjects();
        }
        close(stage, drawKind);
    }

    // Returns whether the scope bound the program from before it again.
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
     * Right after the outermost entity or block entity renderer returned. Like {@link #reassert}, unless the object's own
     * end just did that. The renderer may also have changed the alpha test, blending or the textures the program
     * samples, with or without GlStateManager, while keeping or restoring the program itself. So once the scopes'
     * program is bound, what its directives and textures need is put back too, see
     * {@link ScopedProgramState#reestablish}. Only the outermost renderer counts, so one drawing another inside itself
     * keeps whatever it set for its own draws.
     *
     * @return whether the program had to be bound again
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
        if (index >= scopeBuilds.length) {
            scopeBuilds = Arrays.copyOf(scopeBuilds, Math.max(index + 1, scopeBuilds.length * 2));
        }
        streamScopes[index] = stream;
    }

    /**
     * An entity ({@link RenderDrawKind#ENTITY}), block entity ({@link RenderDrawKind#BLOCK_ENTITY}) or block entity's
     * breaking overlay ({@link RenderDrawKind#DAMAGED_BLOCK_ENTITY}) starts drawing, with its material ID, -1 when it
     * has none. Inside a bound entity pass it gets the program of its role, unless that is already the one current, and
     * its ID goes on the program, with -1 for the other kind of ID and no color. Outside one, while the scopes stepped
     * aside, or while a renderer around it has its own program bound, nothing changes. Every start needs its
     * {@link #objectEnd}, also the ones that changed nothing.
     */
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
        // The object's renderer runs next and may bind what it wants.
        ownedCurrent = false;
    }

    /**
     * The innermost entity or block entity is done. Special draws of it whose code threw end first. Its program, if it
     * bound one, and the values of the object around it, or the defaults when there is none, come back. A renderer may
     * have left its own program bound, so the scopes' program is bound again if needed. An end without a start is
     * ignored.
     */
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
            // Closing the object's own scope binds what was current at its start, which was the scopes' program when
            // Focalis knew that, so GL needs no asking then.
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
            // An object or special draw around this one is still drawing, and its code runs on.
            if (activeFrameBelow(top) >= 0) {
                ownedCurrent = false;
            }
        } catch (RuntimeException | LinkageError e) {
            objectDepth = 0;
            throw e;
        }
    }

    /**
     * A special draw nested in the innermost object starts, like its armor glint. Inside a bound entity pass it gets
     * the program of its role, unless that is already the one current, and that program gets the innermost object's
     * IDs and color, so it still knows what it draws on. A role without a ready program leaves the object's program
     * on. Outside a bound entity pass, while the scopes stepped aside, or while a renderer has its own program bound,
     * nothing changes. Every start needs its {@link #specialEnd}, or {@link #specialsAbandoned} or its object's end
     * when its code throws.
     *
     * @throws IllegalArgumentException for a kind that isn't a special draw
     */
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
                // The renderer drawing this may have bound its own program, and keeps it.
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
                open(RenderStage.ENTITIES, kind, build, false);
                applyObject(build, activeObjectBelow(index));
            }
        } catch (RuntimeException | LinkageError e) {
            objectDepth = 0;
            throw e;
        }
        // The draw's own code runs next, and the model it draws may be a mod's.
        ownedCurrent = false;
    }

    /**
     * The special draw of that kind returned. Its program, if it bound one, goes back to the object's, with the
     * object's current values. An end that doesn't match the innermost draw, like one whose start never ran, is
     * ignored.
     */
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

    /**
     * A living entity renderer caught a failure and draws on, so the special draws started since the innermost object
     * end now, innermost first, the same way as {@link #specialEnd}.
     */
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

    // The special draw at top, which has to be the innermost frame, ends.
    private void endSpecial(int top) {
        objectDepth = top;
        if (objectModes[top] != SCOPED) {
            return;
        }
        if (close(RenderStage.ENTITIES, objectKinds[top]) && objectOwnedAtStart[top]) {
            ownedCurrent = true;
        }
        // The object's color may have changed while the special draw's program was current.
        BuiltWorldPrograms.Build owned = ownedBuild();
        if (owned != null) {
            applyObjectIfCurrent(owned, activeObjectBelow(top));
        }
        if (activeFrameBelow(top) >= 0) {
            ownedCurrent = false;
        }
    }

    /**
     * The color vanilla multiplies the innermost entity with, like red while it's hurt, or (0, 0, 0, 0) for none. It goes
     * on the program when that is the one current.
     */
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
        // The renderer runs while this is set, and it may have bound its own program.
        if (owned != null) {
            applyObjectIfCurrent(owned, top);
            ownedCurrent = false;
        }
    }

    /** How many entities, block entities and special draws are being drawn, nested ones included. */
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

    // The innermost object below index that is drawn with a Focalis program, or -1. Its values are the ones a program
    // shows there, also inside a special draw of it.
    private int activeObjectBelow(int index) {
        for (int i = index - 1; i >= 0; i--) {
            if (objectModes[i] != PASSIVE && isObject(objectKinds[i])) {
                return i;
            }
        }
        return -1;
    }

    // The innermost object or special draw below index that is drawn with a Focalis program, whose code is still
    // running, or -1.
    private int activeFrameBelow(int index) {
        for (int i = index - 1; i >= 0; i--) {
            if (objectModes[i] != PASSIVE) {
                return i;
            }
        }
        return -1;
    }

    // The innermost object below depth, passive or not, or -1.
    private int innermostObject(int depth) {
        for (int i = depth - 1; i >= 0; i--) {
            if (isObject(objectKinds[i])) {
                return i;
            }
        }
        return -1;
    }

    // Like applyObject, for a moment where the build's program may not be current. GL is only asked when a value
    // actually has to change and Focalis doesn't already know its program is current.
    private void applyObjectIfCurrent(BuiltWorldPrograms.Build build, int index) {
        WorldProgramInputs inputs = build.inputs();
        boolean differs = index < 0 ? inputs.objectDiffers(-1, -1, 0f, 0f, 0f, 0f)
                : inputs.objectDiffers(objectEntityIds[index], objectBlockEntityIds[index], objectColors[index * 4],
                        objectColors[index * 4 + 1], objectColors[index * 4 + 2], objectColors[index * 4 + 3]);
        if (!differs) {
            return;
        }
        if (!ownedCurrent && !scopes.ownsCurrent()) {
            // Something else is bound, so the values wait until the program is bound again.
            return;
        }
        ownedCurrent = true;
        applyObject(build, index);
    }

    // The values of the object at index, or the defaults for -1, go on the build, whose program has to be current.
    private void applyObject(BuiltWorldPrograms.Build build, int index) {
        if (index < 0) {
            build.inputs().object(-1, -1, 0f, 0f, 0f, 0f);
            return;
        }
        int at = index * 4;
        build.inputs().object(objectEntityIds[index], objectBlockEntityIds[index], objectColors[at],
                objectColors[at + 1], objectColors[at + 2], objectColors[at + 3]);
    }

    // The build of the innermost scope that bound one, which is the program the scopes want current.
    @Nullable
    private BuiltWorldPrograms.Build ownedBuild() {
        for (int i = scopes.depth() - 1; i >= 0; i--) {
            if (i < scopeBuilds.length && scopeBuilds[i] != null) {
                return scopeBuilds[i];
            }
        }
        return null;
    }

    // Objects and special draws whose end never came. Renderers also end when they throw, so this is only a safety net,
    // like after a VM error or a special draw that threw. Their scopes close innermost first.
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

    /**
     * Closes every open scope and puts back the state and program each one replaced. Both are attempted even when
     * one throws, and the first failure is rethrown. See {@link ScopedProgramBinding#abort}.
     */
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
