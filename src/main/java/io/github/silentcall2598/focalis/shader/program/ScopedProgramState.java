// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.shader.program;

import io.github.silentcall2598.focalis.render.state.FragmentHold;
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
 * <p>A scope can also hold its directives while its program is the one bound, for vanilla code that sets that state
 * itself between the program's draws. GlStateManager calls changing held state then neither reach GL nor its cache.
 * What they ask for is what the scope's END puts back instead of the state from before, so the code after the scope
 * sees the state it would have had anyway.
 *
 * <p>Whatever this changes goes to GL and to GlStateManager's cache together. A directive also counts as not in effect
 * while only the cache disagrees, so applying it leaves the cache matching GL.
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
    private final int[] cachedFactors = new int[4];

    // One slot per open scope, innermost last. Null for a scope that bound nothing.
    private ProgramDrawState[] targets = new ProgramDrawState[INITIAL_DEPTH];
    // Per open scope, whether it holds its directives while it's the innermost one with a program.
    private boolean[] holds = new boolean[INITIAL_DEPTH];
    private int depth;
    private boolean suspended;
    // Set when a resume left the open scopes without their state because the program bound wasn't Focalis's.
    private boolean stripped;
    // The scope whose directives are held right now, or -1.
    private int holding = -1;

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
    private long held;
    private long requests;
    private long checks;
    private long reestablished;

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
        start(target, false);
    }

    /**
     * Like {@link #start(ProgramDrawState)}, and with {@code hold} the target's directives are also held while this is
     * the innermost scope with a program. Vanilla code that draws with the program and sets that state itself, like the
     * weather or the sky, then can't replace the directive before its draws.
     */
    public void start(@Nullable ProgramDrawState target, boolean hold) {
        try {
            checkNotSuspended("START");
            if (target != null) {
                // The requests so far belong to the scope around this one, whose program stops being the one bound.
                releaseHold();
            }
            if (depth == targets.length) {
                targets = Arrays.copyOf(targets, depth * 2);
                holds = Arrays.copyOf(holds, depth * 2);
            }
            targets[depth] = target;
            holds[depth] = hold && target != null;
            depth++;
            if (target != null) {
                apply(target, depth - 1);
                engageInnermost(false);
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
            if (holding == top) {
                releaseHold();
            }
            boolean hadTarget = targets[top] != null;
            targets[top] = null;
            holds[top] = false;
            stripped &= depth > 0;
            int keep = changes;
            while (keep > 0 && changeScope[keep - 1] == top) {
                keep--;
            }
            rethrow(restoreDownTo(keep, null));
            if (hadTarget) {
                // The program around this one is the one bound again, and vanilla may have changed held state while it
                // wasn't.
                engageInnermost(true);
            }
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
            releaseHold();
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

    /**
     * Right after code Focalis doesn't control returned with the program of the innermost scope bound, like one entity
     * renderer. It may have changed the alpha test, blending or the textures on the program's units while it drew, with
     * or without GlStateManager. What that program's directives and textures need is put back in GL and in
     * GlStateManager's cache, so the next draw with it gets them again. The state from before the scopes, which their
     * ENDs restore, stays as it is. Programs without directives and textures cost no GL call.
     *
     * @return whether anything had to be put back
     */
    public boolean reestablish() {
        if (suspended || depth == 0 || holding >= 0) {
            return false;
        }
        try {
            if (stripped) {
                stripped = false;
                applyAll();
                return true;
            }
            int scope = innermostTarget();
            if (scope < 0) {
                return false;
            }
            ProgramDrawState target = targets[scope];
            boolean changed = false;
            checks++;
            if (target.alphaTest() != null) {
                changed |= ensureAlpha(scope, target.alphaTest(), false);
            }
            if (target.blend() != null) {
                changed |= ensureBlend(scope, target.blend(), false);
            }
            if (target.textureCount() > 0) {
                changed |= ensureTextures(target);
            }
            if (changed) {
                reestablished++;
            }
            return changed;
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
        engageInnermost(false);
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

    /** Whether directives are held right now. */
    public boolean isHolding() {
        return holding >= 0;
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

    /** How many times directives started being held. */
    public long holds() {
        return held;
    }

    /** How many held components GlStateManager calls asked to change, counted once per component and hold. */
    public long heldRequests() {
        return requests;
    }

    /** How many times {@link #reestablish} looked at a program's state, and how many times it had to put any back. */
    public long reestablishChecks() {
        return checks;
    }

    public long reestablished() {
        return reestablished;
    }

    private void apply(ProgramDrawState target, int scope) {
        boolean hold = holds[scope];
        alpha(scope, target.alphaTest(), hold);
        blend(scope, target.blend(), hold);
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
    // what Focalis set. A held directive always gets an entry, even when nothing differs, since that's where the held
    // requests go.
    private void alpha(int scope, @Nullable ProgramStates.AlphaTest test, boolean hold) {
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
        boolean staleEnabled = false;
        boolean staleValues = false;
        if (test != null && fragment.cacheKnown()) {
            staleEnabled = fragment.cachedAlphaTestEnabled() != wantEnabled;
            staleValues = setValues && (fragment.cachedAlphaFunction() != wantFunction
                    || Float.compare(fragment.cachedAlphaReference(), wantReference) != 0);
        }
        boolean record = hold && test != null;
        if (enabled == wantEnabled && !values && !staleEnabled && !staleValues && !record) {
            return;
        }
        int change = push(scope, ALPHA);
        int at = change * STRIDE;
        before[at] = enabled ? 1 : 0;
        before[at + 1] = function;
        referenceBefore[change] = reference;
        after[at] = wantEnabled ? 1 : 0;
        after[at + 1] = values ? wantFunction : function;
        after[at + VALUES] = setValues && (values || staleValues || record) ? 1 : 0;
        referenceAfter[change] = reference;
        boolean wrote = false;
        if (values || staleValues) {
            fragment.alphaFunction(wantFunction, wantReference);
            // GL clamps the reference and may store it with less precision, so what counts is what it kept.
            referenceAfter[change] = fragment.alphaReference();
            queries++;
            wrote = true;
        }
        if (enabled != wantEnabled || staleEnabled) {
            fragment.alphaTest(wantEnabled);
            wrote = true;
        }
        if (wrote) {
            applied++;
        }
    }

    private void blend(int scope, @Nullable ProgramStates.Blend mode, boolean hold) {
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
        boolean values = setValues && !same(factors, want);
        boolean staleEnabled = false;
        boolean staleValues = false;
        if (mode != null && fragment.cacheKnown()) {
            staleEnabled = fragment.cachedBlendEnabled() != wantEnabled;
            if (setValues) {
                fragment.cachedBlendFactors(cachedFactors);
                staleValues = !same(cachedFactors, want);
            }
        }
        boolean record = hold && mode != null;
        if (enabled == wantEnabled && !values && !staleEnabled && !staleValues && !record) {
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
        after[at + VALUES] = setValues && (values || staleValues || record) ? 1 : 0;
        boolean wrote = false;
        if (values || staleValues) {
            fragment.blendFactors(want[0], want[1], want[2], want[3]);
            wrote = true;
        }
        if (enabled != wantEnabled || staleEnabled) {
            fragment.blend(wantEnabled);
            wrote = true;
        }
        if (wrote) {
            applied++;
        }
    }

    // Puts the scope's alpha test back where GL or the cache lost it. With requests, what GL held instead counts as
    // what vanilla asked for, so the scope's END puts that back. Returns whether anything changed.
    private boolean ensureAlpha(int scope, ProgramStates.AlphaTest test, boolean requests) {
        boolean enabled = fragment.alphaTestEnabled();
        queries++;
        boolean wantEnabled = test.enabled();
        int function = 0;
        float reference = 0;
        boolean values = false;
        int entry = entryOf(scope, ALPHA);
        float kept = entry >= 0 && after[entry * STRIDE + VALUES] != 0 ? referenceAfter[entry] : test.reference();
        if (wantEnabled) {
            function = fragment.alphaFunction();
            reference = fragment.alphaReference();
            queries += 2;
            values = function != test.function() || Float.compare(reference, kept) != 0;
        }
        boolean staleEnabled = false;
        boolean staleValues = false;
        if (fragment.cacheKnown()) {
            staleEnabled = fragment.cachedAlphaTestEnabled() != wantEnabled;
            staleValues = wantEnabled && (fragment.cachedAlphaFunction() != test.function()
                    || Float.compare(fragment.cachedAlphaReference(), test.reference()) != 0);
        }
        if (enabled == wantEnabled && !values && !staleEnabled && !staleValues) {
            return false;
        }
        if (requests && entry >= 0) {
            int at = entry * STRIDE;
            if (enabled != wantEnabled) {
                before[at] = enabled ? 1 : 0;
            }
            if (values) {
                before[at + 1] = function;
                referenceBefore[entry] = reference;
            }
        }
        if (values || staleValues) {
            fragment.alphaFunction(test.function(), test.reference());
            if (entry >= 0) {
                referenceAfter[entry] = fragment.alphaReference();
                queries++;
            }
        }
        if (enabled != wantEnabled || staleEnabled) {
            fragment.alphaTest(wantEnabled);
        }
        return true;
    }

    private boolean ensureBlend(int scope, ProgramStates.Blend mode, boolean requests) {
        boolean enabled = fragment.blendEnabled();
        queries++;
        boolean wantEnabled = mode.enabled();
        int[] want = wantFactors;
        want[0] = mode.sourceColor();
        want[1] = mode.destinationColor();
        want[2] = mode.sourceAlpha();
        want[3] = mode.destinationAlpha();
        boolean values = false;
        if (wantEnabled) {
            fragment.blendFactors(factors);
            queries += 4;
            values = !same(factors, want);
        }
        boolean staleEnabled = false;
        boolean staleValues = false;
        if (fragment.cacheKnown()) {
            staleEnabled = fragment.cachedBlendEnabled() != wantEnabled;
            if (wantEnabled) {
                fragment.cachedBlendFactors(cachedFactors);
                staleValues = !same(cachedFactors, want);
            }
        }
        if (enabled == wantEnabled && !values && !staleEnabled && !staleValues) {
            return false;
        }
        int entry = entryOf(scope, BLEND);
        if (requests && entry >= 0) {
            int at = entry * STRIDE;
            if (enabled != wantEnabled) {
                before[at] = enabled ? 1 : 0;
            }
            if (values) {
                for (int i = 0; i < 4; i++) {
                    before[at + 1 + i] = factors[i];
                }
            }
        }
        if (values || staleValues) {
            fragment.blendFactors(want[0], want[1], want[2], want[3]);
        }
        if (enabled != wantEnabled || staleEnabled) {
            fragment.blend(wantEnabled);
        }
        return true;
    }

    // Binds each of the target's textures again on its unit where something else is bound now. GlStateManager's
    // cache follows the units it tracks, and the active unit is left as found.
    private boolean ensureTextures(ProgramDrawState target) {
        boolean changed = false;
        int active = textures.activeUnit();
        queries++;
        try {
            for (int i = 0; i < target.textureCount(); i++) {
                int unit = target.unit(i);
                int texture = target.texture(i);
                textures.activeUnit(unit);
                int bound = textures.boundTexture();
                queries++;
                if (bound != texture) {
                    textures.bindOnUnit(unit, texture);
                    changed = true;
                }
            }
        } finally {
            textures.restoreUnit(active);
        }
        return changed;
    }

    // Holds the directives of the innermost scope with a program, if it holds them and isn't held already. After a
    // nested scope, vanilla may have changed that state while the nested program was bound, so the directive is put
    // back first and what GL held counts as vanilla's request.
    private void engageInnermost(boolean enforce) {
        if (suspended || stripped || holding >= 0) {
            return;
        }
        int scope = innermostTarget();
        if (scope < 0 || !holds[scope]) {
            return;
        }
        ProgramDrawState target = targets[scope];
        int components = 0;
        ProgramStates.AlphaTest test = target.alphaTest();
        if (test != null) {
            components |= FragmentHold.ALPHA_TEST | (test.enabled() ? FragmentHold.ALPHA_FUNCTION : 0);
            if (enforce) {
                ensureAlpha(scope, test, true);
            }
        }
        ProgramStates.Blend mode = target.blend();
        if (mode != null) {
            components |= FragmentHold.BLEND | (mode.enabled() ? FragmentHold.BLEND_FACTORS : 0);
            if (enforce) {
                ensureBlend(scope, mode, true);
            }
        }
        if (components == 0) {
            return;
        }
        fragment.hold(components);
        holding = scope;
        held++;
    }

    // Ends the hold and keeps what GlStateManager calls asked for meanwhile as what the holding scope's END puts back.
    private void releaseHold() {
        if (holding < 0) {
            return;
        }
        int scope = holding;
        holding = -1;
        int asked = fragment.release();
        if (asked == 0) {
            return;
        }
        int alpha = entryOf(scope, ALPHA);
        if (alpha >= 0) {
            int at = alpha * STRIDE;
            if ((asked & FragmentHold.ALPHA_TEST) != 0) {
                before[at] = fragment.requestedAlphaTest() ? 1 : 0;
                requests++;
            }
            if ((asked & FragmentHold.ALPHA_FUNCTION) != 0) {
                before[at + 1] = fragment.requestedAlphaFunction();
                referenceBefore[alpha] = fragment.requestedAlphaReference();
                requests++;
            }
        }
        int blend = entryOf(scope, BLEND);
        if (blend >= 0) {
            int at = blend * STRIDE;
            if ((asked & FragmentHold.BLEND) != 0) {
                before[at] = fragment.requestedBlend() ? 1 : 0;
                requests++;
            }
            if ((asked & FragmentHold.BLEND_FACTORS) != 0) {
                for (int i = 0; i < 4; i++) {
                    before[at + 1 + i] = fragment.requestedBlendFactor(i);
                }
                requests++;
            }
        }
    }

    // The scope whose program the scopes leave bound, the innermost one with a target, or -1.
    private int innermostTarget() {
        for (int i = depth - 1; i >= 0; i--) {
            if (targets[i] != null) {
                return i;
            }
        }
        return -1;
    }

    // The newest entry of that kind the scope made, or -1.
    private int entryOf(int scope, int kind) {
        for (int i = changes - 1; i >= 0 && changeScope[i] >= scope; i--) {
            if (changeScope[i] == scope && changeKind[i] == kind) {
                return i;
            }
        }
        return -1;
    }

    // Whether the factors read last are the four stored at this offset plus one.
    private boolean sameFactors(int[] values, int at) {
        return factors[0] == values[at + 1] && factors[1] == values[at + 2] && factors[2] == values[at + 3]
                && factors[3] == values[at + 4];
    }

    private static boolean same(int[] first, int[] second) {
        return first[0] == second[0] && first[1] == second[1] && first[2] == second[2] && first[3] == second[3];
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
                if (function != before[at + 1] || Float.compare(reference, referenceBefore[change]) != 0) {
                    fragment.alphaFunction(before[at + 1], referenceBefore[change]);
                }
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
                if (!sameFactors(before, at)) {
                    fragment.blendFactors(before[at + 1], before[at + 2], before[at + 3], before[at + 4]);
                }
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
        Throwable result = failure;
        try {
            releaseHold();
        } catch (RuntimeException | LinkageError e) {
            // The hold is off either way, since releasing clears it before anything can throw.
            holding = -1;
            result = collect(result, e);
        }
        result = restoreDownTo(0, result);
        Arrays.fill(targets, 0, depth, null);
        Arrays.fill(holds, 0, depth, false);
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
