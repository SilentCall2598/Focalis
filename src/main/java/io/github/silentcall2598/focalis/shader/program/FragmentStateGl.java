// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.shader.program;

/** Changes go through GlStateManager so its cache keeps up, and to GL directly as well. */
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

    boolean cacheKnown();

    boolean cachedAlphaTestEnabled();

    int cachedAlphaFunction();

    float cachedAlphaReference();

    boolean cachedBlendEnabled();

    void cachedBlendFactors(int[] into);

    /**
     * GlStateManager calls changing the held components neither reach GL nor its cache until {@link #release}. This one's
     * own changes still go through.
     */
    void hold(int components);

    /** Returns the components GlStateManager calls asked to change meanwhile. */
    int release();

    boolean requestedAlphaTest();

    int requestedAlphaFunction();

    float requestedAlphaReference();

    boolean requestedBlend();

    int requestedBlendFactor(int index);
}
