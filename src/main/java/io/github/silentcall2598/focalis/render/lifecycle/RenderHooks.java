// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.render.lifecycle;

import io.github.silentcall2598.focalis.core.FocalisLog;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Where Focalis's Mixins report render boundaries that Forge has no event for. The Mixins only call in here, and
 * everything past that is up to the lifecycle. Until {@link #install} runs every hook does nothing.
 */
public final class RenderHooks {

    /** Stages the Mixins wrap around vanilla calls inside the world pass. Read only. */
    public static final Set<RenderStage> PRECISE_STAGES = Collections.unmodifiableSet(EnumSet.of(RenderStage.SKY,
            RenderStage.TERRAIN, RenderStage.ENTITIES, RenderStage.PARTICLES, RenderStage.TRANSLUCENT,
            RenderStage.WEATHER, RenderStage.CLOUDS, RenderStage.HAND));

    private static final HookAvailability WORLD_START = new HookAvailability();
    private static final FirstPassStages FIRST_PASS = new FirstPassStages();

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
        for (RenderStage stage : PRECISE_STAGES) {
            target.markDispatched(stage);
        }
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
        if (FIRST_PASS.worldPassStarted()) {
            reportFirstPass();
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

    // Called by the Mixins right before and after a wrapped vanilla render call, with the same kind both times.
    public static void stageStart(RenderStage stage, RenderDrawKind drawKind, float partialTicks) {
        RenderLifecycle target = lifecycle;
        if (target == null) {
            return;
        }
        FIRST_PASS.stageStarted(stage, drawKind);
        target.dispatch(stage, RenderPhase.START, drawKind, partialTicks);
    }

    public static void stageEnd(RenderStage stage, RenderDrawKind drawKind, float partialTicks) {
        RenderLifecycle target = lifecycle;
        if (target != null) {
            target.dispatch(stage, RenderPhase.END, drawKind, partialTicks);
        }
    }

    public static void stageStart(RenderStage stage, float partialTicks) {
        stageStart(stage, RenderDrawKind.DEFAULT, partialTicks);
    }

    public static void stageEnd(RenderStage stage, float partialTicks) {
        stageEnd(stage, RenderDrawKind.DEFAULT, partialTicks);
    }

    // Same checks and order as the start of RenderGlobal.renderSky. Only the vanilla surface sky has its sun and moon
    // hooked, so a Forge sky renderer or the End stays DEFAULT even in a surface dimension.
    public static RenderDrawKind skyKind(boolean customRenderer, boolean end, boolean surface) {
        if (customRenderer || end || !surface) {
            return RenderDrawKind.DEFAULT;
        }
        return RenderDrawKind.SKY_BASIC;
    }

    private static void reportFirstPass() {
        FocalisLog.LOGGER.info("Render stages seen in the first world pass: {}", FIRST_PASS.seen());
        List<String> wrong = FIRST_PASS.mismatches();
        if (!wrong.isEmpty()) {
            FocalisLog.LOGGER.warn("Render stage hooks in the first world pass don't match vanilla's calls: {}."
                    + " Another mod probably changed those calls in EntityRenderer.renderWorldPass or"
                    + " RenderGlobal.renderSky. Rendering is unaffected, only those stage events are missing, extra or"
                    + " misclassified.", wrong);
        }
    }
}
