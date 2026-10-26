// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.render.state;

/**
 * Holds the alpha test and blend state a shader program's directives set, so GlStateManager calls made while the
 * program draws don't replace it. A held call changes neither GL nor GlStateManager's cache, so the two stay equal.
 * What the call asked for is kept instead, and whoever holds the state applies it when the hold ends. Calls made
 * straight to GL bypass GlStateManager and aren't held. Client thread only, like GlStateManager itself.
 */
public final class FragmentHold {

    /** Whether the alpha test is on. */
    public static final int ALPHA_TEST = 1;
    /** The alpha function and reference. */
    public static final int ALPHA_FUNCTION = 2;
    /** Whether blending is on. */
    public static final int BLEND = 4;
    /** The four blend factors. */
    public static final int BLEND_FACTORS = 8;

    private static int held;
    // Set while Focalis itself changes the held state through GlStateManager, so those calls go through.
    private static boolean writing;
    // The components asked for while held, and the values the last call for each asked for.
    private static int requested;
    private static boolean alphaEnabled;
    private static int alphaFunction;
    private static float alphaReference;
    private static boolean blendEnabled;
    private static final int[] FACTORS = new int[4];
    // Whether any hooked GlStateManager call ran yet, so a missing hook shows.
    private static boolean hooked;

    private FragmentHold() {
    }

    /** Starts holding the given components. Requests left from an earlier hold are dropped. */
    public static void hold(int components) {
        held = components;
        requested = 0;
    }

    /** Ends the hold. Returns the components asked for meanwhile, whose values {@link #requested} methods give. */
    public static int release() {
        held = 0;
        int asked = requested;
        requested = 0;
        return asked;
    }

    public static int held() {
        return held;
    }

    public static boolean requestedAlphaTest() {
        return alphaEnabled;
    }

    public static int requestedAlphaFunction() {
        return alphaFunction;
    }

    public static float requestedAlphaReference() {
        return alphaReference;
    }

    public static boolean requestedBlend() {
        return blendEnabled;
    }

    /** A requested blend factor, in the order source color, destination color, source alpha, destination alpha. */
    public static int requestedFactor(int index) {
        return FACTORS[index];
    }

    /** Lets Focalis's own GlStateManager calls through while it changes the state itself. */
    public static void writing(boolean active) {
        writing = active;
    }

    /** Whether a hooked GlStateManager call ran this session, which is false when the hooks didn't apply. */
    public static boolean hooked() {
        return hooked;
    }

    /**
     * Whether a GlStateManager write of these components may go ahead. It can't while one of them is held, unless
     * Focalis itself is writing. It records nothing, since the request was recorded when the call started.
     */
    public static boolean passes(int components) {
        return (held & components) == 0 || writing;
    }

    // The GlStateManager hooks. Each returns whether the call is held, which means it must not run.

    public static boolean alphaTest(boolean enabled) {
        hooked = true;
        if ((held & ALPHA_TEST) == 0 || writing) {
            return false;
        }
        alphaEnabled = enabled;
        requested |= ALPHA_TEST;
        return true;
    }

    public static boolean alphaFunction(int function, float reference) {
        hooked = true;
        if ((held & ALPHA_FUNCTION) == 0 || writing) {
            return false;
        }
        alphaFunction = function;
        alphaReference = reference;
        requested |= ALPHA_FUNCTION;
        return true;
    }

    public static boolean blend(boolean enabled) {
        hooked = true;
        if ((held & BLEND) == 0 || writing) {
            return false;
        }
        blendEnabled = enabled;
        requested |= BLEND;
        return true;
    }

    // The two factor glBlendFunc sets the alpha factors to the color ones, so that is what it asks for.
    public static boolean blendFactors(int sourceColor, int destinationColor, int sourceAlpha, int destinationAlpha) {
        hooked = true;
        if ((held & BLEND_FACTORS) == 0 || writing) {
            return false;
        }
        FACTORS[0] = sourceColor;
        FACTORS[1] = destinationColor;
        FACTORS[2] = sourceAlpha;
        FACTORS[3] = destinationAlpha;
        requested |= BLEND_FACTORS;
        return true;
    }
}
