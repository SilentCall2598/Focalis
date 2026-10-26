// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.render.state;

import javax.annotation.Nullable;

/**
 * The alpha test and blend state GlStateManager keeps in its cache, which is what Minecraft last set through it and
 * what its next calls compare against. Reading it costs no GL call. Its cache objects register here when
 * GlStateManager loads, and if that never happens the cache stays unknown for the session.
 */
public final class FragmentCache {

    /** GlStateManager's cached alpha function and reference. */
    public interface Alpha {
        int focalis$function();

        float focalis$reference();
    }

    /** GlStateManager's cached blend factors. Its two factor blendFunc only updates the color ones. */
    public interface Blend {
        int focalis$factor(int index);
    }

    @Nullable
    private static Alpha alpha;
    @Nullable
    private static Blend blend;
    @Nullable
    private static FixedFunctionFog.Capability alphaTest;
    @Nullable
    private static FixedFunctionFog.Capability blending;

    private FragmentCache() {
    }

    // GlStateManager makes exactly one of each, so a second one would be some other copy and is ignored.
    public static void register(Alpha created) {
        if (alpha == null) {
            alpha = created;
        }
    }

    public static void register(Blend created) {
        if (blend == null) {
            blend = created;
        }
    }

    public static void registerAlphaTest(FixedFunctionFog.Capability created) {
        if (alphaTest == null) {
            alphaTest = created;
        }
    }

    public static void registerBlend(FixedFunctionFog.Capability created) {
        if (blending == null) {
            blending = created;
        }
    }

    public static boolean available() {
        return alpha != null && blend != null && alphaTest != null && blending != null;
    }

    public static boolean alphaTestEnabled() {
        return require(alphaTest).focalis$enabled();
    }

    public static int alphaFunction() {
        return require(alpha).focalis$function();
    }

    public static float alphaReference() {
        return require(alpha).focalis$reference();
    }

    public static boolean blendEnabled() {
        return require(blending).focalis$enabled();
    }

    /** One cached factor, in the order source color, destination color, source alpha, destination alpha. */
    public static int blendFactor(int index) {
        return require(blend).focalis$factor(index);
    }

    private static <T> T require(@Nullable T value) {
        if (value == null) {
            throw new IllegalStateException("GlStateManager's alpha test and blend cache wasn't found");
        }
        return value;
    }
}
