// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.render.lifecycle;

import io.github.silentcall2598.focalis.core.FocalisLog;

import javax.annotation.Nullable;

/**
 * Where Focalis's Mixins report render boundaries that Forge has no event for. The Mixins only call in here, and
 * everything past that is up to the lifecycle. Until {@link #install} runs every hook does nothing.
 */
public final class RenderHooks {

    @Nullable
    private static volatile RenderLifecycle lifecycle;
    private static boolean worldStartSeen;

    private RenderHooks() {
    }

    /** Called once by the render subsystem during pre-init. */
    public static synchronized void install(RenderLifecycle target) {
        if (lifecycle != null) {
            throw new IllegalStateException("Render hooks are already installed");
        }
        target.markDispatched(RenderStage.WORLD);
        lifecycle = target;
    }

    // The start of EntityRenderer.renderWorldPass. WORLD END still comes from Forge, see ForgeRenderEventBridge.
    public static void worldPassStart(float partialTicks) {
        RenderLifecycle target = lifecycle;
        if (target == null) {
            return;
        }
        if (!worldStartSeen) {
            worldStartSeen = true;
            FocalisLog.LOGGER.info("WORLD START hook in EntityRenderer.renderWorldPass is active");
        }
        target.dispatch(RenderStage.WORLD, RenderPhase.START, partialTicks);
    }
}
