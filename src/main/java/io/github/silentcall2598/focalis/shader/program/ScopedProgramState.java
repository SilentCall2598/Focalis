// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.shader.program;

import io.github.silentcall2598.focalis.shader.pack.ProgramStates;

import javax.annotation.Nullable;
import java.util.Arrays;

/**
 * The alpha test, blend mode and custom textures of the programs the open scopes bound, next to
 * {@link ScopedProgramBinding}, which does the programs themselves. A scope with a program applies what that program
 * asks for, and its END puts back what was there before. A program without a directive gets back what was there
 * before the one around it changed anything, so a directive only ever applies while its own program is bound. Only
 * what Focalis changed is put back, and only while it still holds what Focalis set, since vanilla changes the same
 * state inside the stages it draws and that has to stay. Client thread only, with the GL context current.
 *
 * <p>Like the programs, every open scope is unwound before anything is thrown.
 */
public final class ScopedProgramState {

    private static final int INITIAL_DEPTH = 8;
    private static final int ALPHA = -1;
    private static final int BLEND = -2;
    // Per change, the enabled flag, up to four values and whether the values were changed. Texture changes only use
    // the first slot for the texture.
    private static final int STRIDE = 6;
    private static final int VALUES = 5;

    private final FragmentStateGl fragment;
    private final TextureGl textures;
    private final int[] factors = new int[4];
    private final int[] wantFactors = new int[4];

    // One slot per open scope, innermost last. Null for a scope that bound nothing.
    private ProgramDrawState[] targets = new ProgramDrawState[INITIAL_DEPTH];
    private int depth;
    private boolean suspended;
    // Set when a resume left the open scopes without their state because the program bound wasn't Focalis's.
    private boolean stripped;

    // One entry per change still in effect, newest last. A scope's changes always sit above the ones of the scopes
    // around it. Arrays, so a START or END allocates nothing.
    private int[] changeScope = new int[INITIAL_DEPTH];
    private int[] changeKind = new int[INITIAL_DEPTH];
    private int[] before = new int[INITIAL_DEPTH * STRIDE];
    private int[] after = new int[INITIAL_DEPTH * STRIDE];
    private float[] referenceBefore = new float[INITIAL_DEPTH];
    private float[] referenceAfter = new float[INITIAL_DEPTH];
    private int changes;

    private long applied;
    private long restored;
    private long leftAlone;
    private long queries;

    public ScopedProgramState() {
        this(LwjglFragmentStateGl.INSTANCE, LwjglTextureGl.INSTANCE);
    }

    ScopedProgramState(FragmentStateGl fragment, TextureGl textures) {
        this.fragment = fragment;
        this.textures = textures;
    }

    /**
     * Opens a scope. With a target, which is the state of the program the scope just bound or found bound, that
     * program's directives and textures apply. A null target is a scope that bound nothing, so the state of the
     * program around it stays.
     */
    public void start(@Nullable ProgramDrawState target) {
        try {
            checkNotSuspended("START");
            if (depth == targets.length) {
                targets = Arrays.copyOf(targets, depth * 2);
            }
            targets[depth++] = target;
            if (target != null) {
                apply(target, depth - 1);
            }
        } catch (RuntimeException | LinkageError e) {
            unwind(e);
            throw e;
        }
    }

    /** Closes the innermost scope and puts back what it changed. */
    public void end() {
        try {
            checkNotSuspended("END");
            if (depth == 0) {
                throw new IllegalStateException("Program state END without an open scope");
            }
            int top = --depth;
            targets[top] = null;
            stripped &= depth > 0;
            int keep = changes;
            while (keep > 0 && changeScope[keep - 1] == top) {
                keep--;
            }
            rethrow(restoreDownTo(keep, null));
        } catch (RuntimeException | LinkageError e) {
            unwind(e);
            throw e;
        }
    }

    /**
     * Puts back everything the open scopes changed while a region that binds its own programs runs. The scopes stay
     * open, and nothing can start or end until {@link #resume}.
     */
    public void suspend() {
        try {
            checkNotSuspended("suspension");
            suspended = true;
            stripped = false;
            rethrow(restoreDownTo(0, null));
        } catch (RuntimeException | LinkageError e) {
            unwind(e);
            throw e;
        }
    }

    /**
     * Ends the suspension. With {@code programBound}, which is when the Focalis program was bound again, every open
     * scope applies its program's state again from the outside in. Otherwise nothing is applied, since the program
     * bound now isn't Focalis's.
     */
    public void resume(boolean programBound) {
        try {
            if (!suspended) {
                throw new IllegalStateException("Program state resume without a suspension");
            }
            suspended = false;
            if (!programBound) {
                stripped = depth > 0;
                return;
            }
            applyAll();
        } catch (RuntimeException | LinkageError e) {
            unwind(e);
            throw e;
        }
    }

    /**
     * After the Focalis program was bound again by something other than a resume, like after a renderer returned.
     * If an earlier resume left the open scopes without their state, every one applies its program's state again
     * from the outside in. Otherwise nothing changes.
     */
    public void programBoundAgain() {
        if (!stripped || suspended) {
            return;
        }
        try {
            stripped = false;
            applyAll();
        } catch (RuntimeException | LinkageError e) {
            unwind(e);
            throw e;
        }
    }

    private void applyAll() {
        for (int scope = 0; scope < depth; scope++) {
            if (targets[scope] != null) {
                apply(targets[scope], scope);
            }
        }
    }

    /**
     * Emergency cleanup. Closes every scope and puts back everything still changed, newest first. If that throws,
     * the rest is still attempted and the first failure is rethrown with later ones suppressed.
     */
    public void abort() {
        rethrow(unwind(null));
    }

    public int depth() {
        return depth;
    }

    public boolean isEmpty() {
        return depth == 0;
    }

    public boolean isSuspended() {
        return suspended;
    }

    /** How many changes are in effect right now. */
    public int changesInEffect() {
        return changes;
    }

    /** How many state changes were made, counting a directive's enable and values as one. */
    public long applied() {
        return applied;
    }

    /** How many changes were put back. */
    public long restored() {
        return restored;
    }

    /** How many changes weren't put back, or only partly, since something else had changed that state since. */
    public long leftAlone() {
        return leftAlone;
    }

    /** How many GL state queries were made, for checking that only directive programs cost any. */
    public long queries() {
        return queries;
    }

    private void apply(ProgramDrawState target, int scope) {
        alpha(scope, target.alphaTest());
        blend(scope, target.blend());
        int count = target.textureCount();
        if (count == 0) {
            return;
        }
        int active = textures.activeUnit();
        queries++;
        try {
            for (int i = 0; i < count; i++) {
                int unit = target.unit(i);
                int texture = target.texture(i);
                textures.activeUnit(unit);
                int bound = textures.boundTexture();
                queries++;
                if (bound != texture) {
                    int change = push(scope, unit);
                    before[change * STRIDE] = bound;
                    after[change * STRIDE] = texture;
                    textures.bindOnUnit(unit, texture);
                    applied++;
                }
            }
        } finally {
            // Also when a bind threw, so unwinding reads the unit that was really active.
            textures.restoreUnit(active);
        }
    }

    // A null test from a program that sets none gives back what the program around it changed, as far as it's still
    // what Focalis set.
    private void alpha(int scope, @Nullable ProgramStates.AlphaTest test) {
        int outer = latest(ALPHA);
        if (test == null && outer < 0) {
            return;
        }
        boolean enabled = fragment.alphaTestEnabled();
        int function = fragment.alphaFunction();
        float reference = fragment.alphaReference();
        queries += 3;
        boolean wantEnabled;
        boolean setValues;
        int wantFunction;
        float wantReference;
        if (test != null) {
            wantEnabled = test.enabled();
            setValues = test.enabled();
            wantFunction = test.function();
            wantReference = test.reference();
        } else {
            int at = outer * STRIDE;
            boolean enabledOwned = before[at] != after[at] && enabled == (after[at] != 0);
            wantEnabled = enabledOwned ? before[at] != 0 : enabled;
            setValues = after[at + VALUES] != 0 && function == after[at + 1]
                    && Float.compare(reference, referenceAfter[outer]) == 0;
            wantFunction = before[at + 1];
            wantReference = referenceBefore[outer];
        }
        boolean values = setValues && (function != wantFunction || Float.compare(reference, wantReference) != 0);
        if (enabled == wantEnabled && !values) {
            return;
        }
        int change = push(scope, ALPHA);
        int at = change * STRIDE;
        before[at] = enabled ? 1 : 0;
        before[at + 1] = function;
        referenceBefore[change] = reference;
        after[at] = wantEnabled ? 1 : 0;
        after[at + 1] = values ? wantFunction : function;
        after[at + VALUES] = values ? 1 : 0;
        referenceAfter[change] = reference;
        if (values) {
            fragment.alphaFunction(wantFunction, wantReference);
            // GL clamps the reference and may store it with less precision, so what counts is what it kept.
            referenceAfter[change] = fragment.alphaReference();
            queries++;
        }
        if (enabled != wantEnabled) {
            fragment.alphaTest(wantEnabled);
        }
        applied++;
    }

    private void blend(int scope, @Nullable ProgramStates.Blend mode) {
        int outer = latest(BLEND);
        if (mode == null && outer < 0) {
            return;
        }
        boolean enabled = fragment.blendEnabled();
        fragment.blendFactors(factors);
        queries += 5;
        boolean wantEnabled;
        boolean setValues;
        int[] want = wantFactors;
        if (mode != null) {
            wantEnabled = mode.enabled();
            setValues = mode.enabled();
            want[0] = mode.sourceColor();
            want[1] = mode.destinationColor();
            want[2] = mode.sourceAlpha();
            want[3] = mode.destinationAlpha();
        } else {
            int at = outer * STRIDE;
            boolean enabledOwned = before[at] != after[at] && enabled == (after[at] != 0);
            wantEnabled = enabledOwned ? before[at] != 0 : enabled;
            setValues = after[at + VALUES] != 0 && sameFactors(after, at);
            for (int i = 0; i < 4; i++) {
                want[i] = before[at + 1 + i];
            }
        }
        boolean values = setValues && (factors[0] != want[0] || factors[1] != want[1] || factors[2] != want[2]
                || factors[3] != want[3]);
        if (enabled == wantEnabled && !values) {
            return;
        }
        int change = push(scope, BLEND);
        int at = change * STRIDE;
        before[at] = enabled ? 1 : 0;
        after[at] = wantEnabled ? 1 : 0;
        for (int i = 0; i < 4; i++) {
            before[at + 1 + i] = factors[i];
            after[at + 1 + i] = values ? want[i] : factors[i];
        }
        after[at + VALUES] = values ? 1 : 0;
        if (values) {
            fragment.blendFactors(want[0], want[1], want[2], want[3]);
        }
        if (enabled != wantEnabled) {
            fragment.blend(wantEnabled);
        }
        applied++;
    }

    // Whether the factors read last are the four stored at this offset plus one.
    private boolean sameFactors(int[] values, int at) {
        return factors[0] == values[at + 1] && factors[1] == values[at + 2] && factors[2] == values[at + 3]
                && factors[3] == values[at + 4];
    }

    private int latest(int kind) {
        for (int i = changes - 1; i >= 0; i--) {
            if (changeKind[i] == kind) {
                return i;
            }
        }
        return -1;
    }

    private int push(int scope, int kind) {
        if (changes == changeScope.length) {
            int grown = changes * 2;
            changeScope = Arrays.copyOf(changeScope, grown);
            changeKind = Arrays.copyOf(changeKind, grown);
            before = Arrays.copyOf(before, grown * STRIDE);
            after = Arrays.copyOf(after, grown * STRIDE);
            referenceBefore = Arrays.copyOf(referenceBefore, grown);
            referenceAfter = Arrays.copyOf(referenceAfter, grown);
        }
        changeScope[changes] = scope;
        changeKind[changes] = kind;
        int at = changes * STRIDE;
        Arrays.fill(before, at, at + STRIDE, 0);
        Arrays.fill(after, at, at + STRIDE, 0);
        return changes++;
    }

    // Puts back changes newest first until only keep are left. Every one gets its attempt. Returns the first failure,
    // or the one passed in, with later ones suppressed onto it.
    @Nullable
    private Throwable restoreDownTo(int keep, @Nullable Throwable failure) {
        int active = -1;
        while (changes > keep) {
            int change = --changes;
            try {
                int kind = changeKind[change];
                if (kind == ALPHA) {
                    restoreAlpha(change);
                } else if (kind == BLEND) {
                    restoreBlend(change);
                } else {
                    if (active < 0) {
                        active = textures.activeUnit();
                        queries++;
                    }
                    restoreTexture(change, kind);
                }
            } catch (RuntimeException | LinkageError e) {
                failure = collect(failure, e);
            }
        }
        if (active >= 0) {
            try {
                textures.restoreUnit(active);
            } catch (RuntimeException | LinkageError e) {
                failure = collect(failure, e);
            }
        }
        return failure;
    }

    private void restoreAlpha(int change) {
        int at = change * STRIDE;
        boolean kept = false;
        if (after[at + VALUES] != 0) {
            int function = fragment.alphaFunction();
            float reference = fragment.alphaReference();
            queries += 2;
            if (function == after[at + 1] && Float.compare(reference, referenceAfter[change]) == 0) {
                fragment.alphaFunction(before[at + 1], referenceBefore[change]);
            } else {
                kept = true;
            }
        }
        if (before[at] != after[at]) {
            boolean enabled = fragment.alphaTestEnabled();
            queries++;
            if (enabled == (after[at] != 0)) {
                fragment.alphaTest(before[at] != 0);
            } else {
                kept = true;
            }
        }
        count(kept);
    }

    private void restoreBlend(int change) {
        int at = change * STRIDE;
        boolean kept = false;
        if (after[at + VALUES] != 0) {
            fragment.blendFactors(factors);
            queries += 4;
            if (sameFactors(after, at)) {
                fragment.blendFactors(before[at + 1], before[at + 2], before[at + 3], before[at + 4]);
            } else {
                kept = true;
            }
        }
        if (before[at] != after[at]) {
            boolean enabled = fragment.blendEnabled();
            queries++;
            if (enabled == (after[at] != 0)) {
                fragment.blend(before[at] != 0);
            } else {
                kept = true;
            }
        }
        count(kept);
    }

    private void restoreTexture(int change, int unit) {
        int at = change * STRIDE;
        textures.activeUnit(unit);
        int bound = textures.boundTexture();
        queries++;
        if (bound == after[at]) {
            textures.bindOnUnit(unit, before[at]);
            count(false);
        } else {
            count(true);
        }
    }

    private void count(boolean kept) {
        if (kept) {
            leftAlone++;
        } else {
            restored++;
        }
    }

    private void checkNotSuspended(String what) {
        if (suspended) {
            throw new IllegalStateException("Program state " + what + " while suspended");
        }
    }

    @Nullable
    private Throwable unwind(@Nullable Throwable failure) {
        Throwable result = restoreDownTo(0, failure);
        Arrays.fill(targets, 0, depth, null);
        depth = 0;
        suspended = false;
        stripped = false;
        return result;
    }

    private static Throwable collect(@Nullable Throwable first, Throwable next) {
        if (first == null) {
            return next;
        }
        if (first != next) {
            first.addSuppressed(next);
        }
        return first;
    }

    private static void rethrow(@Nullable Throwable failure) {
        if (failure instanceof RuntimeException) {
            throw (RuntimeException) failure;
        }
        if (failure instanceof LinkageError) {
            throw (LinkageError) failure;
        }
    }
}
