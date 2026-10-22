// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.render.lifecycle;

import io.github.silentcall2598.focalis.core.FocalisLog;
import net.minecraft.entity.Entity;
import net.minecraft.tileentity.TileEntity;

import javax.annotation.Nullable;
import java.util.Arrays;
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
    public static final Set<RenderStage> PRECISE_STAGES = Collections.unmodifiableSet(EnumSet.of(RenderStage.CAMERA,
            RenderStage.SKY, RenderStage.TERRAIN, RenderStage.ENTITIES, RenderStage.ENTITY_OUTLINES,
            RenderStage.BLOCK_DAMAGE, RenderStage.PARTICLES, RenderStage.TRANSLUCENT, RenderStage.WEATHER,
            RenderStage.CLOUDS, RenderStage.HAND));

    private static final HookAvailability WORLD_START = new HookAvailability();
    private static final FirstPassStages FIRST_PASS = new FirstPassStages();
    private static final RendererNesting RENDERERS = new RendererNesting();
    // The entities of renderEntity bodies whose object started and hasn't ended yet, innermost last. Client thread
    // only, like RENDERERS. It only grows when entities nest deeper than ever before.
    private static Entity[] entityBodies = new Entity[8];
    private static int entityBodyCount;

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
        RENDERERS.reset();
        Arrays.fill(entityBodies, 0, entityBodyCount, null);
        entityBodyCount = 0;
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

    // Called by the Mixins as an entity or block entity renderer starts and returns, whoever called it. Mods can
    // replace the loops in RenderGlobal.renderEntities, so the renderers themselves are the reliable boundary. These
    // run for every renderer, so they only count and hand the checkpoint on.
    public static void rendererStarted() {
        if (lifecycle != null) {
            RENDERERS.entered();
        }
    }

    public static void rendererReturned(RenderCheckpoint checkpoint) {
        RenderLifecycle target = lifecycle;
        if (target != null && RENDERERS.returned()) {
            target.dispatchCheckpoint(checkpoint);
        }
    }

    // Like rendererStarted and rendererReturned, and the object listeners hear about the entity or block entity too.
    // The object end comes before the checkpoint, so the checkpoint sees the state the object left. A start returns
    // whether it counted, and the Mixins send the end for exactly those, also when the renderer throws.
    public static boolean entityStarted(Entity entity) {
        RenderLifecycle target = lifecycle;
        if (target == null) {
            return false;
        }
        RENDERERS.entered();
        target.dispatchObject(RenderLifecycle.ENTITY_STARTED, entity, null, RenderDrawKind.DEFAULT, 0f, 0f, 0f, 0f);
        return true;
    }

    public static void entityReturned() {
        objectReturned(RenderCheckpoint.ENTITY_RENDERED);
    }

    // renderEntity's injections, which have no local for what the start returned. An end only closes the innermost
    // open body, and only if that body is its own entity's, so an end whose start never ran closes nothing it doesn't
    // own. That happens when another mod's change keeps the start injection from applying, or when a VM error hits
    // the start handler before it ran. The body is recorded before the start dispatches, so its end still balances the
    // renderer nesting if a VM error interrupts the listeners, and what they had done by then is left to the world pass
    // safety nets like any VM error. Only the same entity rendered inside its own body can't be told apart.
    public static void entityBodyStarted(Entity entity) {
        if (lifecycle == null) {
            return;
        }
        if (entityBodyCount == entityBodies.length) {
            entityBodies = Arrays.copyOf(entityBodies, entityBodyCount * 2);
        }
        entityBodies[entityBodyCount++] = entity;
        entityStarted(entity);
    }

    public static void entityBodyEnded(Entity entity) {
        if (entityBodyCount == 0 || entityBodies[entityBodyCount - 1] != entity) {
            return;
        }
        entityBodies[--entityBodyCount] = null;
        entityReturned();
    }

    // destroyStage is the dispatcher's argument, 0 to 9 while the renderer draws the block entity's breaking overlay.
    public static boolean blockEntityStarted(TileEntity blockEntity, int destroyStage) {
        RenderLifecycle target = lifecycle;
        if (target == null) {
            return false;
        }
        RENDERERS.entered();
        target.dispatchObject(RenderLifecycle.BLOCK_ENTITY_STARTED, null, blockEntity,
                destroyStage >= 0 ? RenderDrawKind.DAMAGED_BLOCK_ENTITY : RenderDrawKind.BLOCK_ENTITY, 0f, 0f, 0f, 0f);
        return true;
    }

    public static void blockEntityReturned() {
        objectReturned(RenderCheckpoint.BLOCK_ENTITY_RENDERED);
    }

    private static void objectReturned(RenderCheckpoint checkpoint) {
        RenderLifecycle target = lifecycle;
        if (target != null) {
            target.dispatchObject(RenderLifecycle.OBJECT_ENDED, null, null, RenderDrawKind.DEFAULT, 0f, 0f, 0f, 0f);
            if (RENDERERS.returned()) {
                target.dispatchCheckpoint(checkpoint);
            }
        }
    }

    // Forge's fast block entity renderers only fill a batch, which vanilla code draws afterwards. It's no renderer of
    // a mod, so it doesn't count for nesting and has no checkpoint.
    public static boolean blockEntityBatchStarted() {
        RenderLifecycle target = lifecycle;
        if (target == null) {
            return false;
        }
        target.dispatchObject(RenderLifecycle.BLOCK_ENTITY_STARTED, null, null, RenderDrawKind.BLOCK_ENTITY, 0f, 0f,
                0f, 0f);
        return true;
    }

    public static void blockEntityBatchEnded() {
        RenderLifecycle target = lifecycle;
        if (target != null) {
            target.dispatchObject(RenderLifecycle.OBJECT_ENDED, null, null, RenderDrawKind.DEFAULT, 0f, 0f, 0f, 0f);
        }
    }

    // Called by the Mixins at the start and the return of vanilla's glint, eye and beacon beam draws, which happen
    // inside an entity or block entity renderer and also in screens. They aren't renderers, so they don't count for
    // nesting and have no checkpoint. A draw that throws never returns, see specialDrawsAbandoned.
    public static void specialDrawStarted(RenderDrawKind kind) {
        RenderLifecycle target = lifecycle;
        if (target != null) {
            target.dispatchObject(RenderLifecycle.SPECIAL_STARTED, null, null, kind, 0f, 0f, 0f, 0f);
        }
    }

    public static void specialDrawEnded(RenderDrawKind kind) {
        RenderLifecycle target = lifecycle;
        if (target != null) {
            target.dispatchObject(RenderLifecycle.SPECIAL_ENDED, null, null, kind, 0f, 0f, 0f, 0f);
        }
    }

    // RenderLivingBase.doRender caught a failure of its model or layers and draws on.
    public static void specialDrawsAbandoned() {
        RenderLifecycle target = lifecycle;
        if (target != null) {
            target.dispatchObject(RenderLifecycle.SPECIALS_ABANDONED, null, null, RenderDrawKind.DEFAULT, 0f, 0f, 0f,
                    0f);
        }
    }

    // The constant color a living entity renderer just set up for blending, or all zero when it stopped.
    public static void entityColor(float red, float green, float blue, float alpha) {
        RenderLifecycle target = lifecycle;
        if (target != null) {
            target.dispatchObject(RenderLifecycle.OBJECT_COLOR, null, null, RenderDrawKind.DEFAULT, red, green, blue,
                    alpha);
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

    // renderWorld numbers its passes 0 and 1 for anaglyph 3D and 2 otherwise. Anything else comes from a mod calling
    // renderWorldPass itself.
    public static RenderDrawKind cameraKind(int pass) {
        switch (pass) {
            case 0:
                return RenderDrawKind.CAMERA_ANAGLYPH_FIRST;
            case 1:
                return RenderDrawKind.CAMERA_ANAGLYPH_SECOND;
            case 2:
                return RenderDrawKind.CAMERA_SINGLE;
            default:
                return RenderDrawKind.DEFAULT;
        }
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
