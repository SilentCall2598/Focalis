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
}
