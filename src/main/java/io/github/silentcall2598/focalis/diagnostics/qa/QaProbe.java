// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.diagnostics.qa;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.github.silentcall2598.focalis.Focalis;
import io.github.silentcall2598.focalis.feature.FeatureState;
import io.github.silentcall2598.focalis.feature.FeatureStatus;
import io.github.silentcall2598.focalis.render.lifecycle.RenderDrawKind;
import io.github.silentcall2598.focalis.render.lifecycle.RenderHooks;
import io.github.silentcall2598.focalis.render.lifecycle.RenderLifecycle;
import io.github.silentcall2598.focalis.render.lifecycle.RenderPhase;
import io.github.silentcall2598.focalis.render.lifecycle.RenderStage;
import io.github.silentcall2598.focalis.render.state.GlContextInfo;
import io.github.silentcall2598.focalis.render.target.WorldTargetFeature;
import io.github.silentcall2598.focalis.render.target.WorldTargetMonitor;
import io.github.silentcall2598.focalis.shader.WorldProgramMonitor;
import io.github.silentcall2598.focalis.shader.WorldProgramsFeature;
import io.github.silentcall2598.focalis.shader.post.PostPassMonitor;
import io.github.silentcall2598.focalis.shader.post.ScenePostPass;
import io.github.silentcall2598.focalis.shader.program.BuiltWorldPrograms;
import io.github.silentcall2598.focalis.shader.program.ShaderProgram;
import io.github.silentcall2598.focalis.shader.routing.ShaderProgramRole;
import io.github.silentcall2598.focalis.shader.routing.ShaderProgramRouter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.GuiIngameMenu;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.util.ScreenShotHelper;
import net.minecraft.world.GameType;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.WorldType;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Supplier;

/**
 * Development QA mode. It checks the GL state around Focalis's world-end work, the order of WORLD START and END and
 * the program bound around each precise stage, drives one scenario from client ticks and writes probe.json for
 * tools/qa. It only exists when
 * {@code focalis.qa.scenario} is set, so normal play never runs any of it.
 */
public final class QaProbe {

    static final String INJECTED_FAILURE = "Focalis QA injected failure";
    static final int HIGH_UNIT_CHECKS = 30;

    private static final String OWNER = "focalis:qa";
    private static final Logger LOGGER = LogManager.getLogger("Focalis/qa");
    // Nulls are written too, so readers always see the same set of fields.
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().serializeNulls()
            .create();
    private static final String WORLD_FOLDER = "focalis-qa";
    private static final long WORLD_SEED = 20260926L;
    private static final int[] TOUCHED_UNITS = {ScenePostPass.SCENE_COLOR_UNIT, ScenePostPass.SCENE_DEPTH_UNIT};
    private static final int WRITE_INTERVAL_TICKS = 20;
    private static final int MAX_STAGE_DEPTH = 16;

    // The ways the state-restore scenario changes GL state before the pass, one kind per frame in turn.
    private enum Perturbation {
        NONE, BLEND_ON, ALPHA_OFF, DEPTH_OFF, ACTIVE_UNIT_5, READ_FRAMEBUFFER_0, HALF_VIEWPORT, DRAW_FRAMEBUFFER_0,
        COMBINED;

        String key() {
            return name().toLowerCase(Locale.ROOT).replace('_', '-');
        }
    }

    static final int PERTURBATION_KINDS = Perturbation.values().length;
    private static final int PERTURBED_FRAMES = PERTURBATION_KINDS * 50;

    private final QaSettings settings;
    private final Supplier<List<FeatureStatus>> features;
    private final Supplier<GlContextInfo> glContext;
    private final QaReport report;
    private final List<QaStep> steps;
    private final Monitor monitor = new Monitor();
    private final ProgramMonitor programMonitor = new ProgramMonitor();
    private final WorldPhaseTracker worldPhases;
    private final RenderStageTracker renderStages;
    private final ShaderRouteTracker shaderRoutes;

    private int ticks;
    private int stepIndex;
    private int stepStartTick = 1;
    private boolean finished;
    @Nullable
    private String brokenReason;
    @Nullable
    private WorldClient currentWorld;
    private int ticksInWorld;
    private int requestId;

    private int worldFrames;
    private long lastFrameNanos;
    private long frameNanos;
    private int frameIntervals;
    private long focalisNanos;

    @Nullable
    private GlBoundary entry;
    private long focalisStartNanos;
    private boolean frameRendered;
    @Nullable
    private String frameSkip;
    private boolean captureCreatedThisFrame;
    @Nullable
    private QaReport.Capture previousCapture;
    @Nullable
    private QaReport.Capture currentCapture;
    private int currentProgram;

    private int perturbFramesLeft;
    @Nullable
    private Perturbation framePerturbation;
    @Nullable
    private GlBoundary perturbOriginal;

    private boolean highUnitRunning;
    private int[] highUnits = new int[0];
    private int highUnitIndex;
    private int highUnit = -1;
    @Nullable
    private GlBoundary highOriginal;
    private final int[] sentinels = new int[2];

    private boolean failureArmed;
    private boolean worldTargetFailureArmed;
    private boolean worldTargetFailureInjected;
    private boolean failureChecked;

    // Framebuffer bindings at WORLD START, before the features ran. A redirected world pass has to end with them.
    private int worldStartRead;
    private int worldStartDraw;
    private boolean passRedirected;
    private boolean worldTargetCreatedThisPass;
    @Nullable
    private QaReport.Capture previousWorldTarget;
    @Nullable
    private QaReport.Capture currentWorldTarget;

    // Program ids per role once the world programs are built, 0 for roles without one.
    private final int[] rolePrograms = new int[ShaderProgramRole.values().length];
    private final Set<Integer> focalisPrograms = new HashSet<>();
    private boolean programsLive;
    // One entry per open precise stage, innermost last. The stage, the program before the features' START, and
    // whether a world program scope opened there.
    private final RenderStage[] stageOpen = new RenderStage[MAX_STAGE_DEPTH];
    private final int[] stageProgramBefore = new int[MAX_STAGE_DEPTH];
    private final boolean[] stageScopeOpened = new boolean[MAX_STAGE_DEPTH];
    private int stageDepth;
    // Whether the entity outlines already ended inside the ENTITIES stage that is open now.
    private boolean outlinesEnded;
    private int openScopes;
    private boolean scopeJustStarted;
    private boolean programFailureArmed;
    private boolean programFailureInjected;
    private boolean programFailurePending;

    public QaProbe(QaSettings settings, Supplier<List<FeatureStatus>> features, Supplier<GlContextInfo> glContext) {
        this.settings = settings;
        this.features = features;
        this.glContext = glContext;
        this.report = new QaReport(settings.scenario);
        this.steps = settings.scenario.steps();
        this.worldPhases = new WorldPhaseTracker(report.worldPhases);
        this.renderStages = new RenderStageTracker(report.renderStages);
        this.shaderRoutes = new ShaderRouteTracker(report.shaderRoutes);
        report.environment.focalisVersion = Focalis.VERSION;
        report.environment.fullscreenAllowed = settings.fullscreenAllowed;
        report.environment.startedAt = Instant.now().toString();
    }

    public PostPassMonitor postPassMonitor() {
        return monitor;
    }

    public WorldTargetMonitor worldTargetMonitor() {
        return monitor;
    }

    public WorldProgramMonitor worldProgramMonitor() {
        return programMonitor;
    }

    /** Registers the listeners that have to run before the features' own. Call it before features register. */
    public void installBefore(RenderLifecycle lifecycle) {
        LOGGER.warn("Development QA mode is on, running scenario '{}'. It is not meant for normal play.",
                settings.scenario.id);
        lifecycle.register(RenderStage.FRAME, OWNER, this::onFrame);
        // WORLD START only feeds the phase bookkeeping. Counting world passes, GL checks, state changes and the
        // injected failure all belong to the end of the world pass.
        lifecycle.register(RenderStage.WORLD, OWNER, (stage, phase, drawKind, partialTicks) -> {
            if (finished || brokenReason != null) {
                return;
            }
            if (phase == RenderPhase.END) {
                beforeFocalisWorld();
            } else {
                beforeFocalisWorldStart();
            }
            worldPhases.dispatchStarted(phase, report.frames.total);
            renderStages.world(phase, report.frames.total);
            if (phase == RenderPhase.START) {
                shaderRoutes.worldStarted();
            }
        });
        for (RenderStage stage : RenderHooks.PRECISE_STAGES) {
            lifecycle.register(stage, OWNER, this::onStage);
        }
        MinecraftForge.EVENT_BUS.register(this);
        Runtime.getRuntime().addShutdownHook(new Thread(this::writeOnExit, "Focalis QA report"));
        write(false);
    }

    /** Registers the listeners that have to run after the features' own. */
    public void installAfter(RenderLifecycle lifecycle) {
        lifecycle.register(RenderStage.WORLD, OWNER, (stage, phase, drawKind, partialTicks) -> {
            worldPhases.dispatchFinished();
            if (phase == RenderPhase.END) {
                afterFocalisWorld();
            } else {
                afterFocalisWorldStart();
            }
        });
        for (RenderStage stage : RenderHooks.PRECISE_STAGES) {
            lifecycle.register(stage, OWNER, this::afterFocalisStage);
        }
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START || finished) {
            return;
        }
        ticks++;
        if (ticks == 1) {
            sampleChests();
            report.environment.initialWidth = mc().displayWidth;
            report.environment.initialHeight = mc().displayHeight;
            report.status = "running";
        }
        observeWorld();
        if (mc().world != null) {
            ticksInWorld++;
        }
        report.inWorld = inWorld();
        if (brokenReason != null) {
            fail("The QA probe failed: " + brokenReason);
            return;
        }
        try {
            runStep();
        } catch (RuntimeException e) {
            LOGGER.error("QA step failed", e);
            QaReport.addCapped(report.probeErrors, "step '" + report.step + "': " + e);
            fail("Step '" + report.step + "' threw " + e);
            return;
        }
        if (!finished && ticks % WRITE_INTERVAL_TICKS == 0) {
            write(false);
        }
    }

    private void runStep() {
        if (stepIndex >= steps.size()) {
            finish();
            return;
        }
        QaStep step = steps.get(stepIndex);
        int inStep = ticks - stepStartTick;
        if (inStep == 0) {
            report.step = step.name;
            report.steps.add(new QaReport.StepRecord(step.name, ticks, worldFrames));
        }
        if (step.tick(this, inStep)) {
            stepIndex++;
            stepStartTick = ticks + 1;
            if (stepIndex >= steps.size()) {
                finish();
            }
        } else if (inStep >= step.timeoutTicks) {
            fail("Step '" + step.name + "' timed out after " + inStep + " ticks");
        }
    }

    private void onFrame(RenderStage stage, RenderPhase phase, RenderDrawKind drawKind, float partialTicks) {
        if (phase != RenderPhase.START) {
            if (!finished && brokenReason == null) {
                renderStages.frameEnded(report.frames.total);
                shaderRoutes.frameEnded();
            }
            return;
        }
        if (!finished && brokenReason == null) {
            renderStages.frameStarted();
        }
        long now = System.nanoTime();
        if (lastFrameNanos != 0) {
            long interval = now - lastFrameNanos;
            frameNanos += interval;
            frameIntervals++;
            report.frames.maxFrameMs = Math.max(report.frames.maxFrameMs, interval / 1e6);
        }
        lastFrameNanos = now;
        report.frames.total++;
    }

    private void onStage(RenderStage stage, RenderPhase phase, RenderDrawKind drawKind, float partialTicks) {
        if (!finished && brokenReason == null) {
            renderStages.stage(stage, phase, drawKind, report.frames.total);
            if (phase == RenderPhase.START) {
                shaderRoutes.stageStarted(stage, drawKind);
                if (stage == RenderStage.TRANSLUCENT) {
                    sampleWorldDrawFramebuffer();
                }
                if (programsLive) {
                    beforeFocalisStageStart(stage);
                }
            }
        }
    }

    private void beforeFocalisStageStart(RenderStage stage) {
        if (stageDepth == MAX_STAGE_DEPTH) {
            broken("before a stage start", new IllegalStateException("Precise stages nest deeper than "
                    + MAX_STAGE_DEPTH));
            return;
        }
        try {
            stageOpen[stageDepth] = stage;
            stageProgramBefore[stageDepth] = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
            stageScopeOpened[stageDepth] = false;
            stageDepth++;
            scopeJustStarted = false;
            if (stage == RenderStage.ENTITIES) {
                outlinesEnded = false;
            }
        } catch (RuntimeException e) {
            broken("before a stage start", e);
        }
    }

    // Stages balance with their START, which render-stages-balanced checks, so the entries line up.
    private void afterFocalisStage(RenderStage stage, RenderPhase phase, RenderDrawKind drawKind,
            float partialTicks) {
        if (!programsLive || finished || brokenReason != null || stageDepth == 0) {
            return;
        }
        try {
            int current = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
            if (phase == RenderPhase.START) {
                afterStageStart(stage, drawKind, current);
            } else {
                afterStageEnd(stage, drawKind, current);
            }
        } catch (RuntimeException e) {
            broken("after a stage " + phase, e);
        }
    }

    private void afterStageStart(RenderStage stage, RenderDrawKind drawKind, int current) {
        QaReport.WorldPrograms programs = report.worldPrograms;
        int top = stageDepth - 1;
        boolean opened = scopeJustStarted;
        scopeJustStarted = false;
        stageScopeOpened[top] = opened;
        if (programFailurePending) {
            programFailurePending = false;
            recordProgramFailure(current);
            return;
        }
        String where = stage + "/" + drawKind;
        if (stage == RenderStage.HAND) {
            programs.handChecks++;
            if (opened || openScopes != 0 || focalisPrograms.contains(current)) {
                programs.handProblems++;
                QaReport.addCapped(programs.problems, "HAND started in world pass " + worldFrames + " with program "
                        + current + " and " + openScopes + " open scopes");
            }
            return;
        }
        if (stage == RenderStage.ENTITY_OUTLINES) {
            checkOutlinesStart(where, current);
            return;
        }
        if (!opened) {
            if (focalisPrograms.contains(current)) {
                programs.unboundLeaks++;
                QaReport.addCapped(programs.problems, where + " had Focalis program " + current
                        + " current without a scope in world pass " + (worldFrames + 1));
            }
            return;
        }
        openScopes++;
        programs.scopes++;
        int role = rolePrograms[ShaderProgramRouter.route(stage, drawKind).ordinal()];
        int expected = role != 0 ? role : stageProgramBefore[top];
        if (current != expected) {
            programs.bindMismatches++;
            QaReport.addCapped(programs.problems, where + " had program " + current + " current instead of "
                    + expected + " in world pass " + (worldFrames + 1));
            return;
        }
        if (role != 0) {
            programs.bound.merge(where, 1, Integer::sum);
            sampleProgramDrawFramebuffer(where);
        }
    }

    private void sampleProgramDrawFramebuffer(String where) {
        QaReport.WorldPrograms programs = report.worldPrograms;
        QaReport.Capture target = currentWorldTarget;
        int expected = passRedirected && target != null ? target.framebuffer
                : mc().getFramebuffer().framebufferObject;
        int draw = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        programs.drawSamples++;
        if (draw != expected) {
            programs.drawMismatches++;
            QaReport.addCapped(programs.problems, where + " drew into " + draw + " instead of " + expected
                    + " in world pass " + (worldFrames + 1));
        }
    }

    // Vanilla draws the outlined entities expecting no program and then runs its own shaders, so the program from
    // before the outermost Focalis scope has to be back here.
    private void checkOutlinesStart(String where, int current) {
        QaReport.WorldPrograms programs = report.worldPrograms;
        int expected = stageProgramBefore[stageDepth - 1];
        for (int i = 0; i < stageDepth - 1; i++) {
            if (stageScopeOpened[i]) {
                expected = stageProgramBefore[i];
                break;
            }
        }
        programs.outlineStarts++;
        if (current != expected || focalisPrograms.contains(current)) {
            programs.outlineProblems++;
            QaReport.addCapped(programs.problems, where + " started with program " + current + " instead of "
                    + expected + " in world pass " + (worldFrames + 1));
        }
    }

    // Still inside ENTITIES, so its program has to be back once vanilla is done with the outlines.
    private void checkOutlinesEnd(String where, int current) {
        QaReport.WorldPrograms programs = report.worldPrograms;
        int parent = stageDepth - 1;
        outlinesEnded = true;
        if (parent < 0 || stageOpen[parent] != RenderStage.ENTITIES || !stageScopeOpened[parent]) {
            return;
        }
        int entities = rolePrograms[ShaderProgramRole.ENTITIES.ordinal()];
        int expected = entities != 0 ? entities : stageProgramBefore[parent];
        programs.outlineResumes++;
        if (current != expected) {
            programs.outlineResumeProblems++;
            QaReport.addCapped(programs.problems, where + " ended with program " + current + " instead of "
                    + expected + " in world pass " + (worldFrames + 1));
        }
    }

    // Called by the chest renderer right before a chest draws.
    private void sampleBlockEntity() {
        if (!programsLive || finished || brokenReason != null || stageDepth == 0) {
            return;
        }
        int top = stageDepth - 1;
        int entities = rolePrograms[ShaderProgramRole.ENTITIES.ordinal()];
        if (stageOpen[top] != RenderStage.ENTITIES || !stageScopeOpened[top] || entities == 0) {
            return;
        }
        try {
            QaReport.WorldPrograms programs = report.worldPrograms;
            int current = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
            programs.blockEntitySamples++;
            if (outlinesEnded) {
                programs.blockEntitySamplesAfterOutlines++;
            }
            if (current != entities) {
                programs.blockEntityMismatches++;
                QaReport.addCapped(programs.problems, "a chest drew with program " + current + " instead of "
                        + entities + (outlinesEnded ? " after the entity outlines" : "") + " in world pass "
                        + (worldFrames + 1));
            }
        } catch (RuntimeException e) {
            broken("while a chest draws", e);
        }
    }

    // Wraps the chest renderer so the probe sees the program a real block entity draw gets, which comes after
    // vanilla's entity outlines inside the same ENTITIES stage.
    private void sampleChests() {
        TileEntitySpecialRenderer<TileEntityChest> chests =
                TileEntityRendererDispatcher.instance.getRenderer(TileEntityChest.class);
        if (chests == null) {
            QaReport.addCapped(report.probeErrors, "no chest renderer to sample block entities with");
            return;
        }
        ClientRegistry.bindTileEntitySpecialRenderer(TileEntityChest.class,
                new SamplingRenderer<>(chests, this::sampleBlockEntity));
    }

    private void afterStageEnd(RenderStage stage, RenderDrawKind drawKind, int current) {
        QaReport.WorldPrograms programs = report.worldPrograms;
        int top = --stageDepth;
        int before = stageProgramBefore[top];
        if (stageScopeOpened[top]) {
            openScopes--;
        }
        if (stage == RenderStage.ENTITY_OUTLINES) {
            checkOutlinesEnd(stage + "/" + drawKind, current);
        }
        programs.restores++;
        if (current != before) {
            programs.restoreMismatches++;
            QaReport.addCapped(programs.problems, stage + "/" + drawKind + " ended with program " + current
                    + " instead of " + before + " in world pass " + (worldFrames + 1));
        } else if (stageScopeOpened[top] && drawKind == RenderDrawKind.SKY_TEXTURED && before != 0
                && before == rolePrograms[ShaderProgramRole.SKY_BASIC.ordinal()]) {
            programs.nestedSkyRestores++;
        }
    }

    // The failing START already unwound every open scope, so each of them has to find the program from before the
    // outermost one, and later ENDs expect what is current now.
    private void recordProgramFailure(int current) {
        QaReport.InjectedFailure failure = report.injectedFailure;
        failureChecked = true;
        int expected = current;
        for (int i = 0; i < stageDepth; i++) {
            if (stageScopeOpened[i]) {
                expected = stageProgramBefore[i];
                break;
            }
        }
        for (int i = 0; i < stageDepth; i++) {
            stageProgramBefore[i] = current;
            stageScopeOpened[i] = false;
        }
        openScopes = 0;
        if (failure == null) {
            return;
        }
        failure.stateRestored = current == expected && !focalisPrograms.contains(current);
        FeatureStatus feature = featureStatus(WorldProgramsFeature.ID);
        failure.featureFailed = feature != null && feature.state() == FeatureState.FAILED;
        failure.featureDetail = feature == null ? null : feature.detail();
        boolean released = !focalisPrograms.isEmpty();
        for (int program : focalisPrograms) {
            released &= !GL20.glIsProgram(program);
        }
        failure.resourcesReleased = released;
    }

    private void beforeFocalisWorldStart() {
        if (finished || brokenReason != null) {
            return;
        }
        try {
            passRedirected = false;
            worldTargetCreatedThisPass = false;
            worldStartRead = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
            worldStartDraw = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        } catch (RuntimeException e) {
            broken("before world-start work", e);
        }
    }

    // Right after the replacement, since vanilla can load textures during the world pass and get the freed names.
    private void afterFocalisWorldStart() {
        if (finished || brokenReason != null || !worldTargetCreatedThisPass) {
            return;
        }
        try {
            checkReleased(previousWorldTarget, report.worldTarget.replaced);
        } catch (RuntimeException e) {
            broken("after world-start work", e);
        }
    }

    private void sampleWorldDrawFramebuffer() {
        QaReport.WorldTarget world = report.worldTarget;
        QaReport.Capture target = currentWorldTarget;
        int expected = passRedirected && target != null ? target.framebuffer
                : mc().getFramebuffer().framebufferObject;
        int draw = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        world.drawSamples++;
        if (draw != expected) {
            world.drawMismatches++;
            QaReport.addCapped(world.drawMismatchSamples, "world pass " + (worldFrames + 1) + " drew into "
                    + draw + " instead of " + expected + (passRedirected ? " (the target)" : " (Minecraft's)"));
        }
    }

    private void beforeFocalisWorld() {
        if (finished || brokenReason != null) {
            return;
        }
        try {
            worldFrames++;
            observeWorld();
            drainErrors("before-focalis");
            frameRendered = false;
            frameSkip = null;
            captureCreatedThisFrame = false;
            framePerturbation = null;
            highUnit = -1;
            if (highUnitRunning && worldFrames % 3 == 0) {
                arrangeHighUnit();
            } else if (perturbFramesLeft > 0) {
                perturb();
            }
            entry = GlBoundary.capture(TOUCHED_UNITS);
            focalisStartNanos = System.nanoTime();
        } catch (RuntimeException e) {
            broken("before world-end work", e);
        }
    }

    private void afterFocalisWorld() {
        GlBoundary before = entry;
        entry = null;
        if (finished || brokenReason != null || before == null) {
            return;
        }
        try {
            long nanos = System.nanoTime() - focalisStartNanos;
            focalisNanos += nanos;
            report.frames.maxFocalisWorldEndMs = Math.max(report.frames.maxFocalisWorldEndMs, nanos / 1e6);
            GlBoundary exit = GlBoundary.capture(TOUCHED_UNITS);
            drainErrors("during-focalis");
            report.boundary.framesChecked++;
            List<String> differences = afterFocalis(before).differences(exit);
            if (!differences.isEmpty()) {
                report.boundary.mismatchFrames++;
                QaReport.addCapped(report.boundary.mismatches,
                        new QaReport.Mismatch(worldFrames, frameContext(), differences));
            }
            countPassActivity();
            if (captureCreatedThisFrame) {
                checkReleased(previousCapture, report.resources);
            }
            QaReport.InjectedFailure failure = report.injectedFailure;
            if (failure != null && failure.frame == worldFrames && !failureChecked) {
                recordFailure(failure, differences.isEmpty());
            }
            if (highUnit >= 0) {
                verifyHighUnit(exit);
            }
            if (framePerturbation != null) {
                undoPerturbation();
            }
        } catch (RuntimeException e) {
            broken("after world-end work", e);
        }
    }

    // What a boundary taken before the features at WORLD END has to look like after them. Ending a redirected pass
    // puts back the framebuffers the world pass started with.
    private GlBoundary afterFocalis(GlBoundary before) {
        return passRedirected ? before.withFramebuffers(worldStartRead, worldStartDraw) : before;
    }

    private String frameContext() {
        if (framePerturbation != null) {
            return "state change " + framePerturbation.key();
        }
        return highUnit >= 0 ? "texture unit " + highUnit + " active before the pass" : "normal frame";
    }

    private void countPassActivity() {
        QaReport.PostPass pass = report.postPass;
        if (frameRendered) {
            pass.renderedFrames++;
            if (pass.renderedPerSession.isEmpty()) {
                pass.renderedPerSession.add(0);
            }
            int last = pass.renderedPerSession.size() - 1;
            pass.renderedPerSession.set(last, pass.renderedPerSession.get(last) + 1);
            if (failureChecked && report.injectedFailure != null) {
                report.injectedFailure.renderedAfterFailure++;
            }
        } else if (frameSkip != null) {
            pass.skips.merge(frameSkip, 1, Integer::sum);
        } else {
            pass.idleFrames++;
        }
    }

    private void drainErrors(String where) {
        for (int i = 0; i < 16; i++) {
            int error = GL11.glGetError();
            if (error == GL11.GL_NO_ERROR) {
                return;
            }
            if (where.equals("before-focalis")) {
                report.glErrors.beforeFocalis++;
            } else {
                report.glErrors.duringFocalis++;
            }
            QaReport.addCapped(report.glErrors.samples, new QaReport.GlError(worldFrames, where, error));
        }
    }

    // A new WorldClient means a new session, whether the player rejoined or changed dimension.
    private void observeWorld() {
        WorldClient world = mc().world;
        if (world != null && world != currentWorld) {
            report.worldSessions++;
            report.postPass.renderedPerSession.add(0);
            worldPhases.newSession();
            ticksInWorld = 0;
        }
        currentWorld = world;
    }

    private void perturb() {
        Perturbation kind = Perturbation.values()[perturbFramesLeft % PERTURBATION_KINDS];
        perturbFramesLeft--;
        GlBoundary original = GlBoundary.capture(TOUCHED_UNITS);
        perturbOriginal = original;
        boolean all = kind == Perturbation.COMBINED;
        if (kind == Perturbation.BLEND_ON || all) {
            GlStateManager.enableBlend();
        }
        if (kind == Perturbation.ALPHA_OFF || all) {
            GlStateManager.disableAlpha();
        }
        if (kind == Perturbation.DEPTH_OFF || all) {
            GlStateManager.disableDepth();
        }
        if (kind == Perturbation.ACTIVE_UNIT_5 || all) {
            GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit + 5);
        }
        if (kind == Perturbation.READ_FRAMEBUFFER_0 || all) {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, 0);
        }
        if (kind == Perturbation.HALF_VIEWPORT || all) {
            GlStateManager.viewport(original.viewport[0], original.viewport[1], original.viewport[2] / 2,
                    original.viewport[3] / 2);
        }
        if (kind == Perturbation.DRAW_FRAMEBUFFER_0) {
            // The pass has to notice it isn't drawing to Minecraft's framebuffer and leave everything alone.
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, 0);
        }
        framePerturbation = kind;
        if (report.perturbations != null) {
            report.perturbations.merge(kind.key(), 1, Integer::sum);
        }
    }

    private void undoPerturbation() {
        if (perturbOriginal == null) {
            return;
        }
        GlBoundary original = afterFocalis(perturbOriginal);
        setCapability(original.depthTest, GlStateManager::enableDepth, GlStateManager::disableDepth);
        setCapability(original.blend, GlStateManager::enableBlend, GlStateManager::disableBlend);
        setCapability(original.alphaTest, GlStateManager::enableAlpha, GlStateManager::disableAlpha);
        setActiveUnit(original.activeUnit);
        GlStateManager.viewport(original.viewport[0], original.viewport[1], original.viewport[2], original.viewport[3]);
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, original.readFramebuffer);
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, original.drawFramebuffer);
        checkUndone("state change " + (framePerturbation == null ? "?" : framePerturbation.key()), original);
    }

    private static void setCapability(boolean enabled, Runnable enable, Runnable disable) {
        (enabled ? enable : disable).run();
    }

    // Units 8 and up are outside what GlStateManager tracks, so those only ever go through raw GL.
    private static void setActiveUnit(int unit) {
        if (unit >= 0 && unit < 8) {
            GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit + unit);
        } else {
            GL13.glActiveTexture(GL13.GL_TEXTURE0 + unit);
        }
    }

    // The probe's own changes have to be gone before vanilla carries on, or the run proves nothing.
    private void checkUndone(String what, GlBoundary original) {
        List<String> left = original.differences(GlBoundary.capture(TOUCHED_UNITS));
        if (!left.isEmpty()) {
            QaReport.addCapped(report.probeErrors, "undoing " + what + " left " + left);
        }
    }

    private void arrangeHighUnit() {
        int unit = highUnits[highUnitIndex % highUnits.length];
        highUnitIndex++;
        highOriginal = GlBoundary.capture(TOUCHED_UNITS);
        if (sentinels[0] == 0) {
            sentinels[0] = GlStateManager.generateTexture();
            sentinels[1] = GlStateManager.generateTexture();
        }
        GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit + TOUCHED_UNITS[0]);
        GlStateManager.bindTexture(sentinels[0]);
        GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit + TOUCHED_UNITS[1]);
        GlStateManager.bindTexture(sentinels[1]);
        if (unit < 8) {
            GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit + unit);
        } else {
            // Like a mod that switched with raw GL. GlStateManager still believes unit 0 is active.
            GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);
            GL13.glActiveTexture(GL13.GL_TEXTURE0 + unit);
        }
        highUnit = unit;
    }

    private void verifyHighUnit(GlBoundary exit) {
        GlBoundary original = highOriginal;
        QaReport.HighTextureUnit results = report.highTextureUnit;
        if (original == null || results == null) {
            return;
        }
        List<String> problems = new ArrayList<>();
        if (exit.activeUnit != highUnit) {
            problems.add("active unit " + exit.activeUnit + " instead of " + highUnit);
        }
        if (exit.bindings[0] != sentinels[0] || exit.bindings[1] != sentinels[1]) {
            problems.add("bindings " + Arrays.toString(exit.bindings) + " instead of " + Arrays.toString(sentinels));
        }
        // A stale GlStateManager cache would skip one of these switches and leave GL on the old unit.
        for (int unit : new int[] {TOUCHED_UNITS[0], TOUCHED_UNITS[1], 0}) {
            GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit + unit);
            int real = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE) - GL13.GL_TEXTURE0;
            if (real != unit) {
                problems.add("GlStateManager switch to unit " + unit + " left GL on unit " + real);
            }
        }
        int error = GL11.glGetError();
        if (error != GL11.GL_NO_ERROR) {
            problems.add("GL error 0x" + Integer.toHexString(error));
        }
        GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit + TOUCHED_UNITS[0]);
        GlStateManager.bindTexture(original.bindings[0]);
        GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit + TOUCHED_UNITS[1]);
        GlStateManager.bindTexture(original.bindings[1]);
        setActiveUnit(original.activeUnit);
        checkUndone("texture unit arrangement", afterFocalis(original));

        results.checks++;
        results.highestUnitChecked = Math.max(results.highestUnitChecked, highUnit);
        if (!problems.isEmpty()) {
            results.failures++;
        }
        QaReport.addCapped(results.results, new QaReport.UnitCheck(worldFrames, highUnit, problems.isEmpty(),
                problems.isEmpty() ? "restored, GlStateManager in step" : String.join("; ", problems)));
        if (results.checks >= HIGH_UNIT_CHECKS) {
            highUnitRunning = false;
            releaseSentinels();
        }
    }

    private void releaseSentinels() {
        if (sentinels[0] != 0) {
            GlStateManager.deleteTexture(sentinels[0]);
            GlStateManager.deleteTexture(sentinels[1]);
            sentinels[0] = 0;
            sentinels[1] = 0;
        }
    }

    // A name that one of the current Focalis objects got again was free when it was handed out, which already proves
    // the old object is gone. The world target and the scene capture can take each other's freed names in the same
    // frame. Every other old name must no longer exist.
    private void checkReleased(@Nullable QaReport.Capture old, QaReport.Resources resources) {
        if (old == null) {
            return;
        }
        QaReport.Capture[] current = {currentCapture, currentWorldTarget};
        resources.namesChecked++;
        if (holdsFramebuffer(current, old.framebuffer)) {
            resources.reusedNames++;
        } else if (GL30.glIsFramebuffer(old.framebuffer)) {
            QaReport.addCapped(resources.failures, "framebuffer " + old.framebuffer + " still exists");
        }
        for (int texture : new int[] {old.colorTexture, old.depthTexture}) {
            resources.namesChecked++;
            if (holdsTexture(current, texture)) {
                resources.reusedNames++;
            } else if (GL11.glIsTexture(texture)) {
                QaReport.addCapped(resources.failures, "texture " + texture + " still exists");
            }
        }
    }

    private static boolean holdsFramebuffer(QaReport.Capture[] objects, int framebuffer) {
        for (QaReport.Capture object : objects) {
            if (object != null && object.framebuffer == framebuffer) {
                return true;
            }
        }
        return false;
    }

    private static boolean holdsTexture(QaReport.Capture[] objects, int texture) {
        for (QaReport.Capture object : objects) {
            if (object != null && (object.colorTexture == texture || object.depthTexture == texture)) {
                return true;
            }
        }
        return false;
    }

    private void recordFailure(QaReport.InjectedFailure failure, boolean stateRestored) {
        failureChecked = true;
        failure.stateRestored = stateRestored;
        boolean worldTarget = worldTargetFailureInjected;
        FeatureStatus feature = featureStatus(worldTarget ? WorldTargetFeature.ID : QaScenario.SHADERS);
        failure.featureFailed = feature != null && feature.state() == FeatureState.FAILED;
        failure.featureDetail = feature == null ? null : feature.detail();
        if (worldTarget) {
            QaReport.Capture target = currentWorldTarget;
            failure.resourcesReleased = target != null && !GL30.glIsFramebuffer(target.framebuffer)
                    && !GL11.glIsTexture(target.colorTexture) && !GL11.glIsTexture(target.depthTexture);
            return;
        }
        QaReport.Capture capture = currentCapture;
        failure.resourcesReleased = capture != null && !GL30.glIsFramebuffer(capture.framebuffer)
                && !GL11.glIsTexture(capture.colorTexture) && !GL11.glIsTexture(capture.depthTexture)
                && currentProgram != 0 && !GL20.glIsProgram(currentProgram);
    }

    // Everything below is used by the scenario steps, on the client tick.

    Minecraft mc() {
        return Minecraft.getMinecraft();
    }

    boolean inWorld() {
        Minecraft mc = mc();
        return mc.world != null && mc.player != null && mc.currentScreen == null;
    }

    int ticksInWorld() {
        return ticksInWorld;
    }

    int worldFrames() {
        return worldFrames;
    }

    int initialWidth() {
        return report.environment.initialWidth;
    }

    int initialHeight() {
        return report.environment.initialHeight;
    }

    boolean fullscreenAllowed() {
        return settings.fullscreenAllowed;
    }

    boolean displayIs(int width, int height) {
        return mc().displayWidth == width && mc().displayHeight == height;
    }

    void launchWorld() {
        WorldSettings world = new WorldSettings(WORLD_SEED, GameType.CREATIVE, true, false, WorldType.DEFAULT)
                .enableCommands();
        mc().launchIntegratedServer(WORLD_FOLDER, "Focalis QA", world);
    }

    // What the pause menu's quit button does in singleplayer.
    void leaveWorld() {
        Minecraft mc = mc();
        if (mc.world != null) {
            mc.world.sendQuittingDisconnectingPacket();
        }
        mc.loadWorld(null);
        mc.displayGuiScreen(new GuiMainMenu());
    }

    void command(String command) {
        EntityPlayerSP player = mc().player;
        if (player != null) {
            player.sendChatMessage(command);
        }
    }

    void clearChat() {
        mc().ingameGUI.getChatGUI().clearChatMessages(true);
    }

    // Taken between frames, so the framebuffer holds the last finished frame with its HUD, just like F2.
    void screenshot(String name) {
        Minecraft mc = mc();
        String file = name + ".png";
        ScreenShotHelper.saveScreenshot(settings.output.toFile(), file, mc.displayWidth, mc.displayHeight,
                mc.getFramebuffer());
        if (Files.exists(settings.output.resolve("screenshots").resolve(file))) {
            report.screenshots.add(new QaReport.Screenshot(name, "screenshots/" + file, worldFrames, mc.displayWidth,
                    mc.displayHeight));
        } else {
            QaReport.addCapped(report.probeErrors, "screenshot " + file + " wasn't saved");
        }
    }

    void setHud(boolean hideGui, boolean debugScreen) {
        mc().gameSettings.hideGUI = hideGui;
        mc().gameSettings.showDebugInfo = debugScreen;
    }

    void openScreen(String screen) {
        Minecraft mc = mc();
        switch (screen) {
            case "inventory":
                mc.displayGuiScreen(new GuiInventory(mc.player));
                break;
            case "pause":
                mc.displayGuiScreen(new GuiIngameMenu());
                break;
            case "chat":
                mc.displayGuiScreen(new GuiChat("focalis qa"));
                break;
            default:
                mc.displayGuiScreen(null);
                break;
        }
    }

    void turn(float degrees) {
        EntityPlayerSP player = mc().player;
        if (player != null) {
            player.rotationYaw += degrees;
        }
    }

    // Creative flight, so the player stays put above cloud height.
    void fly() {
        EntityPlayerSP player = mc().player;
        if (player != null && player.capabilities.allowFlying) {
            player.capabilities.isFlying = true;
            player.sendPlayerAbilities();
        }
    }

    void requestResize(int width, int height) {
        report.request = new QaReport.Request(++requestId, "resize", width, height);
        write(false);
    }

    void clearRequest() {
        report.request = null;
        write(false);
    }

    void expectCaptureSize(int width, int height) {
        List<String> expected = report.postPass.expectedCaptureSizes;
        String size = width + "x" + height;
        if (expected.isEmpty() || !expected.get(expected.size() - 1).equals(size)) {
            expected.add(size);
        }
    }

    void toggleFullscreen() {
        mc().toggleFullscreen();
    }

    void note(String note) {
        QaReport.addCapped(report.notes, note);
    }

    void startPerturbation() {
        report.perturbations = new TreeMap<>();
        perturbFramesLeft = PERTURBED_FRAMES;
    }

    boolean perturbationDone() {
        return report.perturbations != null && perturbFramesLeft == 0;
    }

    void startHighUnitChecks() {
        int combined = GL11.glGetInteger(GL20.GL_MAX_COMBINED_TEXTURE_IMAGE_UNITS);
        highUnits = new int[] {8, 12, Math.min(combined, 32) - 1, 5, 1, 0};
        report.highTextureUnit = new QaReport.HighTextureUnit();
        highUnitRunning = true;
    }

    boolean highUnitChecksDone() {
        return report.highTextureUnit != null && !highUnitRunning;
    }

    void armFailure() {
        report.injectedFailure = new QaReport.InjectedFailure();
        failureArmed = true;
    }

    void armWorldTargetFailure() {
        report.injectedFailure = new QaReport.InjectedFailure();
        worldTargetFailureArmed = true;
        worldTargetFailureInjected = true;
    }

    void armWorldProgramFailure() {
        report.injectedFailure = new QaReport.InjectedFailure();
        programFailureArmed = true;
        programFailureInjected = true;
    }

    boolean failureObserved() {
        return failureChecked;
    }

    private void broken(String where, RuntimeException e) {
        LOGGER.error("QA probe failed {}", where, e);
        QaReport.addCapped(report.probeErrors, where + ": " + e);
        brokenReason = where + ": " + e;
    }

    private void fail(String reason) {
        if (report.failure == null) {
            report.failure = reason;
        }
        LOGGER.error("QA scenario '{}' failed: {}", settings.scenario.id, reason);
        finish();
    }

    private void finish() {
        if (finished) {
            return;
        }
        finished = true;
        report.status = "finished";
        report.request = null;
        report.completed = report.failure == null;
        releaseSentinels();
        evaluate();
        report.environment.finishedAt = Instant.now().toString();
        write(true);
        LOGGER.info("QA scenario '{}' finished with result {}", settings.scenario.id, report.result);
        mc().shutdown();
    }

    private void evaluate() {
        refresh();
        report.check("completed", report.failure == null,
                report.failure == null ? "every step finished" : report.failure);
        report.check("no-gl-errors-in-focalis", report.glErrors.duringFocalis == 0,
                report.glErrors.duringFocalis + " during Focalis world-end work, " + report.glErrors.beforeFocalis
                        + " already pending before it");
        report.check("gl-state-restored", report.boundary.framesChecked > 0 && report.boundary.mismatchFrames == 0,
                report.boundary.mismatchFrames + " of " + report.boundary.framesChecked
                        + " frames changed promised state");
        report.check("probe-healthy", report.probeErrors.isEmpty(),
                report.probeErrors.isEmpty() ? "no probe errors" : report.probeErrors.get(0));
        // Every scenario renders the world, so the Mixin hook has to have fired.
        QaReport.WorldPhases phases = report.worldPhases;
        report.check("world-start-hook", phases.starts > 0 && RenderHooks.worldStart().isAvailable(),
                phases.starts + " WORLD START from the Mixin hook, hook " + phases.startHook);
        report.check("world-phases-paired", phases.pairs > 0 && phases.pairs == phases.starts
                        && phases.pairs == phases.ends && !worldPhases.waitingForEnd(),
                phases.starts + " starts, " + phases.ends + " ends, " + phases.pairs + " pairs, "
                        + phases.repeatedStarts + " repeated starts, " + phases.endsWithoutStart
                        + " ends without a start");
        report.check("post-pass-only-at-world-end", phases.passesOutsideEnd == 0 && phases.repeatedPasses == 0,
                phases.passesOutsideEnd + " outside WORLD END, " + phases.repeatedPasses + " repeated in one END");
        checkRenderStages();
        checkWorldTarget();
        checkWorldPrograms();
        if (!settings.scenario.expectsFeatureFailure()) {
            List<String> failed = new ArrayList<>();
            for (QaReport.FeatureEntry feature : report.features) {
                if (FeatureState.FAILED.name().equals(feature.state)) {
                    failed.add(feature.id + ": " + feature.detail);
                }
            }
            report.check("no-feature-failures", failed.isEmpty(), failed.isEmpty() ? "none" : failed.toString());
        }
        settings.scenario.evaluate(report);
        boolean passed = true;
        for (QaReport.Check check : report.checks) {
            passed &= check.passed;
        }
        report.result = passed ? "pass" : "fail";
    }

    // Every scenario needs balanced, well placed stages. Which stages have to show up is up to each scenario.
    private void checkRenderStages() {
        QaReport.RenderStages stages = report.renderStages;
        boolean balanced = !renderStages.stageOpen();
        for (QaReport.StageCounts counts : stages.stages.values()) {
            balanced &= counts.starts == counts.ends && counts.pairs == counts.ends;
        }
        for (QaReport.StageCounts counts : stages.kinds.values()) {
            balanced &= counts.starts == counts.ends && counts.pairs == counts.ends;
        }
        report.check("render-stages-balanced", balanced && stages.problemCount() == 0,
                stages.unmatchedStarts + " unmatched starts, " + stages.endsWithoutStart + " ends without a start, "
                        + stages.badNesting + " out of order, " + stages.repeatedStarts + " repeated starts, "
                        + stages.outsideWorld + " outside a world pass, " + stages.outsideFrame + " outside a frame, "
                        + stages.handBeforeWorldEnd + " HAND before WORLD END, " + stages.kindMismatches
                        + " END with another draw kind");
    }

    // Any scenario can run with the world target on or off. A failed target is judged by its own scenario.
    private void checkWorldTarget() {
        QaReport.WorldTarget world = report.worldTarget;
        String state = report.featureState(WorldTargetFeature.ID);
        String draws = world.drawMismatches + " of " + world.drawSamples + " sampled world passes drew elsewhere"
                + (world.drawMismatchSamples.isEmpty() ? "" : ", like " + world.drawMismatchSamples.get(0));
        if (FeatureState.ACTIVE.name().equals(state)) {
            report.check("world-target-every-pass", world.stopped == null && world.redirectedPasses == worldFrames,
                    world.redirectedPasses + " of " + worldFrames + " world passes redirected, skips " + world.skips
                            + (world.stopped == null ? "" : ", stopped: " + world.stopped));
            report.check("world-target-drawn-into", world.drawSamples > 0 && world.drawMismatches == 0, draws);
            report.check("world-target-old-released", world.replaced.failures.isEmpty(),
                    world.targets.size() + " targets, " + world.replaced.namesChecked + " old names checked, "
                            + world.replaced.reusedNames + " handed out again, failures " + world.replaced.failures);
        } else if (FeatureState.DISABLED.name().equals(state)) {
            report.check("world-target-idle", world.redirectedPasses == 0 && world.targets.isEmpty()
                    && world.drawSamples > 0 && world.drawMismatches == 0, draws);
        }
    }

    // Any scenario can run with the world programs on or off. A failed or stopped feature is judged by its own
    // scenario.
    private void checkWorldPrograms() {
        QaReport.WorldPrograms programs = report.worldPrograms;
        String state = report.featureState(WorldProgramsFeature.ID);
        String problems = programs.problems.isEmpty() ? "" : ", like " + programs.problems.get(0);
        if (settings.scenario.expectsWorldProgramsStopped()) {
            return;
        }
        if (FeatureState.ACTIVE.name().equals(state)) {
            report.check("world-programs-built-once", programs.builds == 1 && programs.stopped == null
                            && !programs.roles.isEmpty(),
                    programs.builds + " builds, " + programs.roles.size() + " roles with a program"
                            + (programs.stopped == null ? "" : ", stopped: " + programs.stopped));
            report.check("world-programs-bound", programs.scopes > 0 && programs.bindMismatches == 0
                            && programs.unboundLeaks == 0,
                    programs.scopes + " scopes, " + programs.bindMismatches + " with the wrong program, "
                            + programs.unboundLeaks + " Focalis programs without a scope" + problems);
            report.check("world-programs-restored", programs.restores > 0 && programs.restoreMismatches == 0,
                    programs.restoreMismatches + " of " + programs.restores + " stage ends didn't restore"
                            + problems);
            report.check("world-programs-drawn-into-world", programs.drawSamples > 0 && programs.drawMismatches == 0,
                    programs.drawMismatches + " of " + programs.drawSamples + " bound stages drew elsewhere"
                            + problems);
            report.check("world-programs-hand-untouched", programs.handChecks > 0 && programs.handProblems == 0,
                    programs.handProblems + " of " + programs.handChecks + " HAND starts had a scope or program"
                            + problems);
            // Only has something to check when something glows or a chest is in view.
            report.check("world-programs-outlines-left-to-vanilla", programs.outlineProblems == 0
                            && programs.outlineResumeProblems == 0 && programs.blockEntityMismatches == 0,
                    programs.outlineProblems + " of " + programs.outlineStarts + " outline starts kept a Focalis"
                            + " program, " + programs.outlineResumeProblems + " of " + programs.outlineResumes
                            + " outline ends didn't bind the entities program again, "
                            + programs.blockEntityMismatches + " of " + programs.blockEntitySamples
                            + " chests drew with another program" + problems);
        } else if (FeatureState.DISABLED.name().equals(state)) {
            report.check("world-programs-idle", programs.builds == 0 && programs.scopes == 0,
                    programs.builds + " builds, " + programs.scopes + " scopes");
        }
    }

    @Nullable
    private FeatureStatus featureStatus(String id) {
        for (FeatureStatus status : features.get()) {
            if (status.featureId().equals(id)) {
                return status;
            }
        }
        return null;
    }

    private void refresh() {
        report.features.clear();
        for (FeatureStatus status : features.get()) {
            report.features.add(new QaReport.FeatureEntry(status.featureId(), status.state().name(), status.detail()));
        }
        report.worldPhases.startHook = RenderHooks.worldStart().state().name();
        QaReport.Frames frames = report.frames;
        frames.world = worldFrames;
        frames.averageFrameMs = frameIntervals == 0 ? 0 : frameNanos / 1e6 / frameIntervals;
        frames.averageFocalisWorldEndMs = report.boundary.framesChecked == 0 ? 0
                : focalisNanos / 1e6 / report.boundary.framesChecked;
        GlContextInfo gl = glContext.get();
        if (gl != null) {
            report.environment.glRenderer = gl.renderer();
            report.environment.glVersion = gl.version();
        }
    }

    // The runner may be reading the file at the same moment, so a failed replace is simply retried later.
    private synchronized void write(boolean mustSucceed) {
        refresh();
        Path output = settings.output;
        Path temp = output.resolve("probe.json.tmp");
        for (int attempt = 0; attempt < (mustSucceed ? 20 : 1); attempt++) {
            try {
                Files.createDirectories(output);
                Files.write(temp, GSON.toJson(report).getBytes(StandardCharsets.UTF_8));
                Files.move(temp, output.resolve("probe.json"), StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
                return;
            } catch (IOException e) {
                if (!mustSucceed) {
                    return;
                }
                LOGGER.debug("Retrying the QA report write after {}", e.toString());
                try {
                    Thread.sleep(100);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
        LOGGER.error("Couldn't write the QA report to {}", output);
    }

    private void writeOnExit() {
        if (finished) {
            return;
        }
        report.status = "exited";
        if (report.failure == null) {
            report.failure = "The client exited before the scenario finished";
        }
        try {
            write(true);
        } catch (RuntimeException e) {
            LOGGER.error("Couldn't write the QA report on exit", e);
        }
    }

    private final class Monitor implements PostPassMonitor, WorldTargetMonitor {

        @Override
        public void programBuilt(int program) {
            currentProgram = program;
            QaReport.addCapped(report.postPass.programsBuilt, program);
        }

        @Override
        public void captureCreated(int framebuffer, int colorTexture, int depthTexture, int width, int height,
                String depthFormat) {
            if (report.postPass.expectedCaptureSizes.isEmpty()) {
                expectCaptureSize(mc().displayWidth, mc().displayHeight);
            }
            previousCapture = currentCapture;
            currentCapture = new QaReport.Capture(worldFrames, framebuffer, colorTexture, depthTexture, width,
                    height, depthFormat);
            QaReport.addCapped(report.postPass.captures, currentCapture);
            captureCreatedThisFrame = true;
        }

        @Override
        public void beforeDraw() {
            QaReport.InjectedFailure failure = report.injectedFailure;
            if (failureArmed && failure != null) {
                failureArmed = false;
                failure.frame = worldFrames;
                throw new IllegalStateException(INJECTED_FAILURE);
            }
        }

        @Override
        public void passRendered() {
            frameRendered = true;
            worldPhases.passRendered(report.frames.total);
        }

        @Override
        public void passSkipped(String reason) {
            frameSkip = reason;
        }

        @Override
        public void passStopped(String problem) {
            String[] lines = problem.split("\n");
            report.postPass.stopped = String.join(" | ", Arrays.asList(lines).subList(0, Math.min(3, lines.length)));
        }

        // Targets are made at WORLD START, which belongs to the world pass counted at its END.
        @Override
        public void targetCreated(int framebuffer, int colorTexture, int depthTexture, int width, int height,
                String depthFormat) {
            previousWorldTarget = currentWorldTarget;
            currentWorldTarget = new QaReport.Capture(worldFrames + 1, framebuffer, colorTexture, depthTexture, width,
                    height, depthFormat);
            QaReport.addCapped(report.worldTarget.targets, currentWorldTarget);
            worldTargetCreatedThisPass = true;
        }

        @Override
        public void redirected() {
            passRedirected = true;
            report.worldTarget.redirectedPasses++;
            QaReport.InjectedFailure failure = report.injectedFailure;
            if (worldTargetFailureInjected && failureChecked && failure != null) {
                failure.renderedAfterFailure++;
            }
        }

        @Override
        public void beforeCopy() {
            QaReport.InjectedFailure failure = report.injectedFailure;
            if (worldTargetFailureArmed && failure != null) {
                worldTargetFailureArmed = false;
                failure.frame = worldFrames;
                throw new IllegalStateException(INJECTED_FAILURE);
            }
        }

        @Override
        public void skipped(String reason) {
            report.worldTarget.skips.merge(reason, 1, Integer::sum);
        }

        @Override
        public void stopped(String reason) {
            report.worldTarget.stopped = reason;
        }
    }

    private final class ProgramMonitor implements WorldProgramMonitor {

        @Override
        public void programsBuilt(BuiltWorldPrograms programs) {
            QaReport.WorldPrograms world = report.worldPrograms;
            world.builds++;
            for (BuiltWorldPrograms.Entry entry : programs.entries().values()) {
                ShaderProgram program = entry.program();
                if (entry.ready() && program != null) {
                    rolePrograms[entry.role().ordinal()] = program.id();
                    focalisPrograms.add(program.id());
                    world.roles.put(entry.role().name(), new QaReport.BuiltProgram(program.name(), program.id()));
                }
            }
            programsLive = true;
        }

        // The failure goes into the sun or moon, so the sky's scope is open too and both have to unwind.
        @Override
        public void scopeStarted(RenderStage stage, RenderDrawKind drawKind) {
            scopeJustStarted = true;
            QaReport.InjectedFailure failure = report.injectedFailure;
            if (failure == null || !programFailureInjected) {
                return;
            }
            if (failureChecked) {
                failure.renderedAfterFailure++;
            } else if (programFailureArmed && drawKind == RenderDrawKind.SKY_TEXTURED) {
                programFailureArmed = false;
                programFailurePending = true;
                failure.frame = worldFrames + 1;
                throw new IllegalStateException(INJECTED_FAILURE);
            }
        }

        @Override
        public void stopped(String reason) {
            report.worldPrograms.stopped = reason;
        }
    }
}
