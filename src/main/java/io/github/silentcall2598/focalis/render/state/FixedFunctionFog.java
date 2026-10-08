// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.render.state;

import javax.annotation.Nullable;

/**
 * The fog GlStateManager keeps in its cache, which is what Minecraft last set through it. Reading it costs no GL call.
 * Its cache objects register here when GlStateManager loads, and if that never happens the fog stays unavailable for
 * the session. Fog that a mod sets with raw GL calls doesn't show up here, the same as in GlStateManager itself.
 */
public final class FixedFunctionFog {

    /** GlStateManager's cached fog mode, density, start and end. */
    public interface Cache {
        int focalis$mode();

        float focalis$density();

        float focalis$start();

        float focalis$end();
    }

    /** One of GlStateManager's cached GL capabilities. */
    public interface Capability {
        boolean focalis$enabled();
    }

    @Nullable
    private static Cache cache;
    @Nullable
    private static Capability fog;

    private FixedFunctionFog() {
    }

    // GlStateManager makes exactly one of each, so a second one would be some other copy and is ignored.
    public static void register(Cache created) {
        if (cache == null) {
            cache = created;
        }
    }

    public static void registerFogCapability(Capability created) {
        if (fog == null) {
            fog = created;
        }
    }

    public static boolean available() {
        return cache != null && fog != null;
    }

    public static boolean enabled() {
        return require(fog).focalis$enabled();
    }

    public static int mode() {
        return require(cache).focalis$mode();
    }

    public static float density() {
        return require(cache).focalis$density();
    }

    public static float start() {
        return require(cache).focalis$start();
    }

    public static float end() {
        return require(cache).focalis$end();
    }

    private static <T> T require(@Nullable T value) {
        if (value == null) {
            throw new IllegalStateException("GlStateManager's fog wasn't found");
        }
        return value;
    }
}
