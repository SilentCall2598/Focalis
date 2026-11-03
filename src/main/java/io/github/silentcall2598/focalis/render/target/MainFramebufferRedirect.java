// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.render.target;

/**
 * Vanilla rebinds its framebuffer in the middle of the world pass, after drawing entity outlines, so without this
 * redirect the rest of the world would miss the Focalis target.
 */
public final class MainFramebufferRedirect {

    private static int source;
    private static int target;
    private static boolean hookCalled;

    private MainFramebufferRedirect() {
    }

    public static int substitute(int framebuffer) {
        hookCalled = true;
        return target != 0 && framebuffer == source ? target : framebuffer;
    }

    public static boolean hookCalled() {
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
