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
 * <p>If a call throws, every open scope is unwound first, so no Focalis program stays bound after a failure.
 */
public final class ScopedProgramBinding {

    private static final int INITIAL_DEPTH = 8;

    private final ProgramBindingGl gl;
    // One slot per open scope, innermost last. Arrays so a START or END allocates nothing.
    private RenderStage[] stages = new RenderStage[INITIAL_DEPTH];
    private RenderDrawKind[] kinds = new RenderDrawKind[INITIAL_DEPTH];
    private int[] previousPrograms = new int[INITIAL_DEPTH];
    private boolean[] changed = new boolean[INITIAL_DEPTH];
    private int depth;

    public ScopedProgramBinding() {
        this(LwjglProgramBindingGl.INSTANCE);
    }

    ScopedProgramBinding(ProgramBindingGl gl) {
        this.gl = gl;
    }

    /**
     * Opens a scope and binds {@code target}, unless it's already active. A null target opens a scope that changes
     * nothing, so START and END still have to match. Throws if the target was deleted.
     */
    public void start(RenderStage stage, RenderDrawKind drawKind, @Nullable ShaderProgram target) {
        try {
            Objects.requireNonNull(stage, "stage");
            Objects.requireNonNull(drawKind, "drawKind");
            // Checked before any GL call, since a deleted target's id() throws.
            int targetId = target == null ? 0 : target.id();
            int current = gl.currentProgram();
            boolean change = target != null && targetId != current;
            push(stage, drawKind, current, change);
            if (change) {
                // Pushed first, so if this throws the unwinding below still restores what was active.
                gl.useProgram(targetId);
            }
        } catch (RuntimeException | LinkageError e) {
            unwind(e);
            throw e;
        }
    }

    /** Closes the innermost scope, which has to be the same stage and kind, and restores its previous program. */
    public void end(RenderStage stage, RenderDrawKind drawKind) {
        try {
            Objects.requireNonNull(stage, "stage");
            Objects.requireNonNull(drawKind, "drawKind");
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
     * Emergency cleanup. Closes every open scope newest first and restores what each one changed. If a restore
     * throws, the rest are still attempted and the first failure is rethrown with later ones suppressed.
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

    /** How many scopes are open. */
    public int depth() {
        return depth;
    }

    public boolean isEmpty() {
        return depth == 0;
    }

    private void push(RenderStage stage, RenderDrawKind drawKind, int previous, boolean change) {
        if (depth == stages.length) {
            int grown = depth * 2;
            stages = Arrays.copyOf(stages, grown);
            kinds = Arrays.copyOf(kinds, grown);
            previousPrograms = Arrays.copyOf(previousPrograms, grown);
            changed = Arrays.copyOf(changed, grown);
        }
        stages[depth] = stage;
        kinds[depth] = drawKind;
        previousPrograms[depth] = previous;
        changed[depth] = change;
        depth++;
    }

    // Restores every open scope that changed the program, newest first, and empties the stack. Returns the first
    // failure, or the one passed in, with any later failures suppressed onto it.
    @Nullable
    private Throwable unwind(@Nullable Throwable failure) {
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
