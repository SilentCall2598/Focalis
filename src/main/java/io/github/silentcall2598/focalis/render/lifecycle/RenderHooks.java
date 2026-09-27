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

    private static final HookAvailability WORLD_START = new HookAvailability();

    @Nullable
    private static volatile RenderLifecycle lifecycle;

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

    /** Whether WORLD START from the Mixin hook works this session. Unknown until the first world pass ends. */
    public static HookAvailability worldStart() {
        return WORLD_START;
    }

    // The start of EntityRenderer.renderWorldPass. WORLD END still comes from Forge, see ForgeRenderEventBridge.
    public static void worldPassStart(float partialTicks) {
        RenderLifecycle target = lifecycle;
        if (target == null) {
            return;
        }
        if (WORLD_START.hookFired()) {
            FocalisLog.LOGGER.info("WORLD START hook in EntityRenderer.renderWorldPass is active");
        }
        target.dispatch(RenderStage.WORLD, RenderPhase.START, partialTicks);
    }

    // Forge fires WORLD END inside the same world pass, after the start hook. If that hook still hasn't fired, the
    // Mixin didn't apply. WORLD END itself keeps working either way.
    static void worldPassEnd() {
        if (lifecycle != null && WORLD_START.hookExpected()) {
            FocalisLog.LOGGER.warn("The WORLD START hook in EntityRenderer.renderWorldPass never fired, so precise"
                    + " WORLD START is unavailable this session. The Focalis Mixin probably didn't apply. Mixin"
                    + " messages about mixins.focalis.json earlier in the log say why. Features that only need"
                    + " WORLD END keep working.");
        }
    }
}
