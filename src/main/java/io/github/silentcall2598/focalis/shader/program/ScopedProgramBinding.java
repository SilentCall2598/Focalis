// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.shader.program;

import io.github.silentcall2598.focalis.render.lifecycle.RenderDrawKind;
import io.github.silentcall2598.focalis.render.lifecycle.RenderStage;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.Objects;

/**
 * Binds programs for nested render scopes and puts back whatever program was active before each one, even when that
 * program belongs to Minecraft or another mod. Only changes Focalis made are undone. It never owns or deletes the
 * programs it binds. Client thread only, with the OpenGL context current.
 *
 * <p>The open scopes can step aside for a region that binds its own programs, like vanilla's entity outline shaders.
 * While suspended they stay open but leave the program alone, and resuming binds the Focalis program again. Code that
 * only borrows the program for a moment, like a mod's renderer, is repaired afterwards with {@link #reassert}.
 *
 * <p>If a call throws, every open scope is unwound first, so no Focalis program stays bound after a failure.
 */
public final class ScopedProgramBinding {

    private static final int INITIAL_DEPTH = 8;
    private static final int NO_PROGRAM = -1;

    private final ProgramBindingGl gl;
    // One slot per open scope, innermost last. Arrays so a START or END allocates nothing.
    private RenderStage[] stages = new RenderStage[INITIAL_DEPTH];
    private RenderDrawKind[] kinds = new RenderDrawKind[INITIAL_DEPTH];
    private int[] previousPrograms = new int[INITIAL_DEPTH];
    private int[] targets = new int[INITIAL_DEPTH];
    private boolean[] changed = new boolean[INITIAL_DEPTH];
    private int depth;
    // The region the scopes stepped aside for, or null while they own the program.
    @Nullable
    private RenderStage suspendedStage;
    @Nullable
    private RenderDrawKind suspendedKind;
    // What resuming binds again, or NO_PROGRAM when stepping aside took nothing away.
    private int resumeProgram = NO_PROGRAM;

    public ScopedProgramBinding() {
        this(LwjglProgramBindingGl.INSTANCE);
    }

    ScopedProgramBinding(ProgramBindingGl gl) {
        this.gl = gl;
    }

    /**
     * Opens a scope and binds {@code target}, unless it's already active. A null target opens a scope that changes
     * nothing, so START and END still have to match. Throws if the target was deleted or the scopes are suspended.
     */
    public void start(RenderStage stage, RenderDrawKind drawKind, @Nullable ShaderProgram target) {
        try {
            Objects.requireNonNull(stage, "stage");
            Objects.requireNonNull(drawKind, "drawKind");
            checkNotSuspended("START", stage, drawKind);
            // Checked before any GL call, since a deleted target's id() throws.
            int targetId = target == null ? 0 : target.id();
            int current = gl.currentProgram();
            boolean change = target != null && targetId != current;
            push(stage, drawKind, current, targetId, change);
            if (change) {
                // Pushed first, so if this throws the unwinding below still restores what was active.
                gl.useProgram(targetId);
            }
        } catch (RuntimeException | LinkageError e) {
            unwind(e);
            throw e;
        }
    }

    /**
     * Closes the innermost scope, which has to be the same stage and kind, and restores its previous program. Throws
     * while the scopes are suspended.
     */
    public void end(RenderStage stage, RenderDrawKind drawKind) {
        try {
            Objects.requireNonNull(stage, "stage");
            Objects.requireNonNull(drawKind, "drawKind");
            checkNotSuspended("END", stage, drawKind);
            if (depth == 0) {
                throw new IllegalStateException("Program scope END " + stage + "/" + drawKind
                        + " without an open scope");
            }
            int top = depth - 1;
            if (stages[top] != stage || kinds[top] != drawKind) {
                throw new IllegalStateException("Program scope END " + stage + "/" + drawKind + " while "
                        + stages[top] + "/" + kinds[top] + " is the innermost open scope");
            }
            depth = top;
            // Nothing to undo when Focalis didn't bind anything, even if something else changed the program since.
            if (changed[top]) {
                gl.useProgram(previousPrograms[top]);
            }
        } catch (RuntimeException | LinkageError e) {
            unwind(e);
            throw e;
        }
    }

    /**
     * Steps aside for a region that binds its own programs. The open scopes stay open. If the current program is
     * still the one Focalis bound, the program from before the outermost scope that bound one goes back. Nothing can
     * start or end until {@link #resume} with the same stage and kind. With no Focalis program bound it changes
     * nothing.
     */
    public void suspend(RenderStage stage, RenderDrawKind drawKind) {
        try {
            Objects.requireNonNull(stage, "stage");
            Objects.requireNonNull(drawKind, "drawKind");
            checkNotSuspended("suspension", stage, drawKind);
            int inner = innermostChanged();
            int resume = NO_PROGRAM;
            // A program something else bound since isn't Focalis's to take away, or to bind over later.
            if (inner >= 0 && gl.currentProgram() == targets[inner]) {
                gl.useProgram(previousPrograms[outermostChanged()]);
                resume = targets[inner];
            }
            suspendedStage = stage;
            suspendedKind = drawKind;
            resumeProgram = resume;
        } catch (RuntimeException | LinkageError e) {
            unwind(e);
            throw e;
        }
    }

    /**
     * Ends the suspension, which has to be for the same stage and kind, and binds the innermost Focalis program again
     * if suspending took it away. Whatever the region left bound is replaced.
     */
    public void resume(RenderStage stage, RenderDrawKind drawKind) {
        try {
            Objects.requireNonNull(stage, "stage");
            Objects.requireNonNull(drawKind, "drawKind");
            if (suspendedStage == null) {
                throw new IllegalStateException("Program scope resume " + stage + "/" + drawKind
                        + " without a suspension");
            }
            if (suspendedStage != stage || suspendedKind != drawKind) {
                throw new IllegalStateException("Program scope resume " + stage + "/" + drawKind
                        + " while suspended for " + suspendedStage + "/" + suspendedKind);
            }
            int program = resumeProgram;
            // Cleared first, so if the bind throws the unwinding restores like it does for any open scope.
            clearSuspension();
            if (program != NO_PROGRAM) {
                gl.useProgram(program);
            }
        } catch (RuntimeException | LinkageError e) {
            unwind(e);
            throw e;
        }
    }

    /**
     * Binds the Focalis program the open scopes own again if something else is current now. Meant for right after code
     * Focalis doesn't control returned, like one entity renderer, which may bind whatever it wants while it runs. The
     * scopes, and what their ENDs restore, stay as they are. Does nothing while suspended or when the open scopes own
     * no program.
     *
     * @return whether the program had to be bound again
     */
    public boolean reassert() {
        int owned = ownedProgram();
        if (owned == NO_PROGRAM) {
            return false;
        }
        try {
            if (gl.currentProgram() == owned) {
                return false;
            }
            gl.useProgram(owned);
            return true;
        } catch (RuntimeException | LinkageError e) {
            unwind(e);
            throw e;
        }
    }

    /**
     * Emergency cleanup. Closes every open scope newest first and restores what each one changed. If a restore
     * throws, the rest are still attempted and the first failure is rethrown with later ones suppressed. Suspended
     * scopes are only dropped, since their programs already went back when they stepped aside.
     */
    public void abort() {
        Throwable failure = unwind(null);
        if (failure instanceof RuntimeException) {
            throw (RuntimeException) failure;
        }
        if (failure instanceof LinkageError) {
            throw (LinkageError) failure;
        }
    }

    /** How many scopes are open, suspended or not. */
    public int depth() {
        return depth;
    }

    public boolean isEmpty() {
        return depth == 0;
    }

    public boolean isSuspended() {
        return suspendedStage != null;
    }

    private void checkNotSuspended(String what, RenderStage stage, RenderDrawKind drawKind) {
        if (suspendedStage != null) {
            throw new IllegalStateException("Program scope " + what + " " + stage + "/" + drawKind
                    + " while suspended for " + suspendedStage + "/" + suspendedKind);
        }
    }

    private void clearSuspension() {
        suspendedStage = null;
        suspendedKind = null;
        resumeProgram = NO_PROGRAM;
    }

    // The scope whose program should be current, or -1 when no open scope bound one.
    private int innermostChanged() {
        for (int i = depth - 1; i >= 0; i--) {
            if (changed[i]) {
                return i;
            }
        }
        return -1;
    }

    // The innermost bound program, as long as every scope opened inside it started with that program still current.
    // A scope that started with another program means something else had taken over by then, and that isn't
    // Focalis's to undo.
    private int ownedProgram() {
        if (suspendedStage != null) {
            return NO_PROGRAM;
        }
        int inner = innermostChanged();
        if (inner < 0) {
            return NO_PROGRAM;
        }
        int program = targets[inner];
        for (int i = inner + 1; i < depth; i++) {
            if (previousPrograms[i] != program) {
                return NO_PROGRAM;
            }
        }
        return program;
    }

    private int outermostChanged() {
        for (int i = 0; i < depth; i++) {
            if (changed[i]) {
                return i;
            }
        }
        return -1;
    }

    private void push(RenderStage stage, RenderDrawKind drawKind, int previous, int target, boolean change) {
        if (depth == stages.length) {
            int grown = depth * 2;
            stages = Arrays.copyOf(stages, grown);
            kinds = Arrays.copyOf(kinds, grown);
            previousPrograms = Arrays.copyOf(previousPrograms, grown);
            targets = Arrays.copyOf(targets, grown);
            changed = Arrays.copyOf(changed, grown);
        }
        stages[depth] = stage;
        kinds[depth] = drawKind;
        previousPrograms[depth] = previous;
        targets[depth] = target;
        changed[depth] = change;
        depth++;
    }

    // Restores every open scope that changed the program, newest first, and empties the stack. Returns the first
    // failure, or the one passed in, with any later failures suppressed onto it.
    @Nullable
    private Throwable unwind(@Nullable Throwable failure) {
        if (suspendedStage != null) {
            clearSuspension();
            depth = 0;
            return failure;
        }
        while (depth > 0) {
            int top = --depth;
            if (!changed[top]) {
                continue;
            }
            try {
                gl.useProgram(previousPrograms[top]);
            } catch (RuntimeException | LinkageError e) {
                if (failure == null) {
                    failure = e;
                } else if (failure != e) {
                    failure.addSuppressed(e);
                }
            }
        }
        return failure;
    }
}
