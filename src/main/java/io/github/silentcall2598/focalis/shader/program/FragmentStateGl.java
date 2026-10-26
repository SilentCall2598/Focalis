// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.shader.program;

/**
 * The alpha test and blend calls program directives need. Queries read GL itself. Changes go through GlStateManager
 * so its cache keeps up, and to GL directly as well. Every method needs a current context on the calling thread.
 */
interface FragmentStateGl {

    boolean alphaTestEnabled();

    int alphaFunction();

    float alphaReference();

    void alphaTest(boolean enabled);

    void alphaFunction(int function, float reference);

    boolean blendEnabled();

    /** The four blend factors in the order source color, destination color, source alpha, destination alpha. */
    void blendFactors(int[] into);

    void blend(boolean enabled);

    void blendFactors(int sourceColor, int destinationColor, int sourceAlpha, int destinationAlpha);

    /** Whether GlStateManager's cache of this state can be read. The cached methods need it. They ask nothing of GL. */
    boolean cacheKnown();

    boolean cachedAlphaTestEnabled();

    int cachedAlphaFunction();

    float cachedAlphaReference();

    boolean cachedBlendEnabled();

    /** The cached factors, in the same order as {@link #blendFactors(int[])}. */
    void cachedBlendFactors(int[] into);

    /**
     * Holds the {@link io.github.silentcall2598.focalis.render.state.FragmentHold} components given, so GlStateManager
     * calls changing them neither reach GL nor its cache until {@link #release}. This one's own changes still go
     * through.
     */
    void hold(int components);

    /**
     * Ends the hold. Returns the components GlStateManager calls asked to change meanwhile, whose last requested values
     * the requested methods give until the next hold.
     */
    int release();

    boolean requestedAlphaTest();

    int requestedAlphaFunction();

    float requestedAlphaReference();

    boolean requestedBlend();

    /** One requested factor, in the same order as {@link #blendFactors(int[])}. */
    int requestedBlendFactor(int index);
}
