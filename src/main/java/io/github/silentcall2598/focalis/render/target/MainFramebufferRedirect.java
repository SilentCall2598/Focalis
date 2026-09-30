// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.render.target;

/**
 * While the world is drawn into a Focalis target, binding Minecraft's framebuffer through OpenGlHelper binds that
 * target instead. Vanilla rebinds its framebuffer in the middle of the world pass, after drawing entity outlines, and
 * without this the rest of the world would miss the target. Client thread only.
 */
public final class MainFramebufferRedirect {

    private static int source;
    private static int target;
    private static boolean hookCalled;

    private MainFramebufferRedirect() {
    }

    /** Called by the Mixin in OpenGlHelper.glBindFramebuffer with the framebuffer about to be bound. */
    public static int substitute(int framebuffer) {
        hookCalled = true;
        return target != 0 && framebuffer == source ? target : framebuffer;
    }

    // Minecraft binds its framebuffer through OpenGlHelper every frame before the world, so a hook that never ran
    // by then didn't apply.
    static boolean hookCalled() {
        return hookCalled;
    }

    static void redirect(int minecraftFramebuffer, int focalisFramebuffer) {
        source = minecraftFramebuffer;
        target = focalisFramebuffer;
    }

    static void clear() {
        source = 0;
        target = 0;
    }
}
