// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.diagnostics.qa;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.github.silentcall2598.focalis.Focalis;
import io.github.silentcall2598.focalis.feature.FeatureState;
import io.github.silentcall2598.focalis.feature.FeatureStatus;
import io.github.silentcall2598.focalis.render.lifecycle.RenderCheckpoint;
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
import io.github.silentcall2598.focalis.shader.pack.ProgramDirectory;
import io.github.silentcall2598.focalis.shader.post.PostPassMonitor;
import io.github.silentcall2598.focalis.shader.post.ScenePostPass;
import io.github.silentcall2598.focalis.shader.program.BuiltWorldPrograms;
import io.github.silentcall2598.focalis.shader.program.ShaderProgram;
import io.github.silentcall2598.focalis.shader.program.WorldProgramInputs;
import io.github.silentcall2598.focalis.shader.program.WorldSampler;
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
import net.minecraft.client.renderer.texture.ITextureObject;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.entity.passive.EntityPig;
import net.minecraft.entity.passive.EntitySheep;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.ScreenShotHelper;
import net.minecraft.world.GameType;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.WorldType;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

import javax.annotation.Nullable;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.IntBuffer;
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
import java.util.Map;
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
    // EntityRenderer registers its lightmap as the first dynamic texture named lightMap, and the name is lowercased.
    private static final ResourceLocation LIGHTMAP = new ResourceLocation("dynamic/lightmap_1");
    private static final List<ResourceLocation> CHEST_TEXTURES = textures("textures/entity/chest/",
            "normal.png", "normal_double.png", "trapped.png", "trapped_double.png", "christmas.png",
            "christmas_double.png");
    private static final List<ResourceLocation> SHEEP_TEXTURES = textures("textures/entity/sheep/", "sheep.png",
            "sheep_fur.png");

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
    // The dimension of the programs selected last, as a report key.
    private String programDimension = "";
    // One entry per open precise stage, innermost last. The stage, the program before the features' START, and
    // whether a world program scope opened there.
    private final RenderStage[] stageOpen = new RenderStage[MAX_STAGE_DEPTH];
    private final int[] stageProgramBefore = new int[MAX_STAGE_DEPTH];
    private final boolean[] stageScopeOpened = new boolean[MAX_STAGE_DEPTH];
    // The active texture unit and its binding right before the features' START, and before the latest END.
    private final int[] stageActiveTextureBefore = new int[MAX_STAGE_DEPTH];
    private final int[] stageTextureBefore = new int[MAX_STAGE_DEPTH];
    private int endActiveTextureBefore;
    private int endTextureBefore;
    // Minecraft's lightmap texture, looked up once, -1 when it couldn't be found.
    private int lightmapTexture;
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
        lifecycle.registerCheckpoint(OWNER, this::beforeFocalisCheckpoint);
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
        lifecycle.registerCheckpoint(OWNER, this::afterFocalisCheckpoint);
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
            } else if (programsLive && stageDepth > 0) {
                try {
                    endActiveTextureBefore = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
                    endTextureBefore = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
                } catch (RuntimeException e) {
                    broken("before a stage end", e);
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
            stageActiveTextureBefore[stageDepth] = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
            stageTextureBefore[stageDepth] = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
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
        checkTextureState(stage + "/" + drawKind + " START", stageActiveTextureBefore[top], stageTextureBefore[top]);
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
        programs.scopesByDimension.merge(programDimension, 1, Integer::sum);
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
            programs.boundByDimension.merge(programDimension, 1, Integer::sum);
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
            checkLightmap("a chest");
        } catch (RuntimeException e) {
            broken("while a chest draws", e);
        }
    }

    // Wraps the chest renderer so the probe sees the program a real block entity draw gets, which comes after
    // vanilla's entity outlines inside the same ENTITIES stage. In scenarios that ask for it, each chest also leaves
    // another program bound when it returns, like a mod renderer with its own shader.
    private void sampleChests() {
        TileEntitySpecialRenderer<TileEntityChest> chests =
                TileEntityRendererDispatcher.instance.getRenderer(TileEntityChest.class);
        if (chests == null) {
            QaReport.addCapped(report.probeErrors, "no chest renderer to sample block entities with");
            return;
        }
        ClientRegistry.bindTileEntitySpecialRenderer(TileEntityChest.class,
                new SamplingRenderer<>(chests, this::sampleBlockEntity, this::afterChest));
    }

    // The ENTITIES program while a bound ENTITIES scope is the innermost open stage, 0 otherwise.
    private int ownedEntitiesProgram() {
        if (!programsLive || finished || brokenReason != null || stageDepth == 0) {
            return 0;
        }
        int top = stageDepth - 1;
        if (stageOpen[top] != RenderStage.ENTITIES || !stageScopeOpened[top]) {
            return 0;
        }
        return rolePrograms[ShaderProgramRole.ENTITIES.ordinal()];
    }

    // Checks a sheep drew with its own texture and the lightmap, and in scenarios that ask for it, leaves 0 bound
    // after a pig, like a mod that binds its own shader and releases it.
    @SubscribeEvent
    public void onRenderLivingPost(RenderLivingEvent.Post<?> event) {
        if (event.getEntity() instanceof EntitySheep && ownedEntitiesProgram() != 0) {
            try {
                checkOwnTexture("a sheep", SHEEP_TEXTURES);
                checkLightmap("a sheep");
            } catch (RuntimeException e) {
                broken("after a sheep drew", e);
            }
        }
        if (!settings.scenario.injectsRendererLeaks() || !(event.getEntity() instanceof EntityPig)
                || ownedEntitiesProgram() == 0) {
            return;
        }
        GL20.glUseProgram(0);
        report.worldPrograms.entityLeaksInjected++;
    }

    // Right after a chest drew, its own texture has to be on unit 0.
    private void afterChest() {
        if (ownedEntitiesProgram() != 0) {
            try {
                checkOwnTexture("a chest", CHEST_TEXTURES);
            } catch (RuntimeException e) {
                broken("after a chest drew", e);
            }
        }
        leakFromBlockEntity();
    }

    // Leaves another real program bound, here the terrain one.
    private void leakFromBlockEntity() {
        int entities = ownedEntitiesProgram();
        int other = rolePrograms[ShaderProgramRole.TERRAIN_SOLID.ordinal()];
        if (!settings.scenario.injectsRendererLeaks() || entities == 0 || other == 0 || other == entities) {
            return;
        }
        GL20.glUseProgram(other);
        report.worldPrograms.blockEntityLeaksInjected++;
    }

    // Before the features repair anything, so what a renderer left behind shows up here.
    private void beforeFocalisCheckpoint(RenderCheckpoint checkpoint) {
        int entities = ownedEntitiesProgram();
        if (entities == 0) {
            return;
        }
        try {
            if (GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM) != entities) {
                QaReport.WorldPrograms programs = report.worldPrograms;
                if (checkpoint == RenderCheckpoint.ENTITY_RENDERED) {
                    programs.entityRenderersLeftOther++;
                } else {
                    programs.blockEntityRenderersLeftOther++;
                }
            }
        } catch (RuntimeException e) {
            broken("before a renderer checkpoint", e);
        }
    }

    // Right after a renderer returned and the features had their turn, the ENTITIES program has to be current.
    private void afterFocalisCheckpoint(RenderCheckpoint checkpoint) {
        int entities = ownedEntitiesProgram();
        if (entities == 0) {
            return;
        }
        try {
            QaReport.WorldPrograms programs = report.worldPrograms;
            int current = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
            if (checkpoint == RenderCheckpoint.ENTITY_RENDERED) {
                programs.entityRendererChecks++;
            } else {
                programs.blockEntityRendererChecks++;
            }
            if (current != entities) {
                programs.rendererMismatches++;
                QaReport.addCapped(programs.problems, checkpoint + " left program " + current + " instead of "
                        + entities + " in world pass " + (worldFrames + 1));
            }
        } catch (RuntimeException e) {
            broken("after a renderer checkpoint", e);
        }
    }

    // Focalis only ever changes the program, so the active unit and what is bound there have to stay as they were.
    private void checkTextureState(String where, int activeBefore, int textureBefore) {
        QaReport.WorldPrograms programs = report.worldPrograms;
        int active = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        int texture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        programs.textureStateChecks++;
        if (active != activeBefore || texture != textureBefore) {
            programs.textureStateChanges++;
            QaReport.addCapped(programs.problems, where + " changed the active unit from " + activeBefore + " to "
                    + active + " or its texture from " + textureBefore + " to " + texture);
        }
    }

    // Switches the active unit with raw GL and back again, which leaves GlStateManager's record of it right.
    private static int textureOn(int unit) {
        int active = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        if (active == GL13.GL_TEXTURE0 + unit) {
            return GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        }
        GL13.glActiveTexture(GL13.GL_TEXTURE0 + unit);
        int texture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        GL13.glActiveTexture(active);
        return texture;
    }

    private void checkLightmap(String what) {
        QaReport.WorldPrograms programs = report.worldPrograms;
        if (lightmapTexture == 0) {
            ITextureObject texture = mc().getTextureManager().getTexture(LIGHTMAP);
            lightmapTexture = texture == null ? -1 : texture.getGlTextureId();
        }
        int bound = textureOn(1);
        programs.lightmapChecks++;
        if (bound != lightmapTexture) {
            programs.lightmapMismatches++;
            QaReport.addCapped(programs.problems, what + " drew with texture " + bound + " on unit 1 instead of the"
                    + " lightmap " + lightmapTexture + " in world pass " + (worldFrames + 1));
        }
    }

    private void checkOwnTexture(String what, List<ResourceLocation> textures) {
        QaReport.WorldPrograms programs = report.worldPrograms;
        int bound = textureOn(0);
        programs.ownTextureChecks++;
        for (ResourceLocation location : textures) {
            ITextureObject texture = mc().getTextureManager().getTexture(location);
            if (texture != null && texture.getGlTextureId() == bound) {
                return;
            }
        }
        programs.ownTextureMismatches++;
        QaReport.addCapped(programs.problems, what + " drew with texture " + bound + " on unit 0 instead of its own"
                + " in world pass " + (worldFrames + 1));
    }

    private void afterStageEnd(RenderStage stage, RenderDrawKind drawKind, int current) {
        QaReport.WorldPrograms programs = report.worldPrograms;
        int top = --stageDepth;
        int before = stageProgramBefore[top];
        checkTextureState(stage + "/" + drawKind + " END", endActiveTextureBefore, endTextureBefore);
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

    private static List<ResourceLocation> textures(String folder, String... names) {
        List<ResourceLocation> textures = new ArrayList<>();
        for (String name : names) {
            textures.add(new ResourceLocation(folder + name));
        }
        return textures;
    }

    // Everything below is used by the scenario steps, on the client tick.

    Minecraft mc() {
        return Minecraft.getMinecraft();
    }

    // The last frame's average color in a box at the middle of the screen, a twelfth of its width and height. With
    // the HUD hidden that's the world itself.
    void sampleCenter(String name) {
        Minecraft mc = mc();
        BufferedImage image = ScreenShotHelper.createScreenshot(mc.displayWidth, mc.displayHeight,
                mc.getFramebuffer());
        int boxWidth = Math.max(1, image.getWidth() / 12);
        int boxHeight = Math.max(1, image.getHeight() / 12);
        int left = (image.getWidth() - boxWidth) / 2;
        int top = (image.getHeight() - boxHeight) / 2;
        double[] sum = new double[3];
        for (int y = top; y < top + boxHeight; y++) {
            for (int x = left; x < left + boxWidth; x++) {
                int rgb = image.getRGB(x, y);
                sum[0] += (rgb >> 16) & 0xFF;
                sum[1] += (rgb >> 8) & 0xFF;
                sum[2] += rgb & 0xFF;
            }
        }
        double count = boxWidth * (double) boxHeight * 255.0;
        report.centerColors.put(name, new double[] {sum[0] / count, sum[1] / count, sum[2] / count});
    }

    boolean inWorld() {
        Minecraft mc = mc();
        return mc.world != null && mc.player != null && mc.currentScreen == null;
    }

    int ticksInWorld() {
        return ticksInWorld;
    }

    // The dimension of the client world, or null without one.
    @Nullable
    Integer dimension() {
        WorldClient world = mc().world;
        return world == null ? null : world.provider.getDimension();
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

    // Any scenario can run with the world programs on or off. A failed feature or a pack without usable programs is
    // judged by its own scenario.
    private void checkWorldPrograms() {
        QaReport.WorldPrograms programs = report.worldPrograms;
        String state = report.featureState(WorldProgramsFeature.ID);
        String problems = programs.problems.isEmpty() ? "" : ", like " + programs.problems.get(0);
        if (settings.scenario.expectsUnusableWorldPrograms()) {
            return;
        }
        if (FeatureState.ACTIVE.name().equals(state)) {
            boolean oncePerFolder = !programs.folderBuilds.isEmpty();
            for (int builds : programs.folderBuilds.values()) {
                oncePerFolder &= builds == 1;
            }
            boolean anyRoles = false;
            for (Map<String, QaReport.BuiltProgram> roles : programs.folderRoles.values()) {
                anyRoles |= !roles.isEmpty();
            }
            report.check("world-programs-built-once", oncePerFolder && anyRoles && programs.stopped == null,
                    "builds per folder " + programs.folderBuilds + (anyRoles ? "" : ", no roles with a program")
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
            report.check("world-programs-after-renderers", programs.rendererMismatches == 0,
                    programs.rendererMismatches + " of " + (programs.entityRendererChecks
                            + programs.blockEntityRendererChecks) + " renderers returned without the ENTITIES program"
                            + " bound again, " + (programs.entityRenderersLeftOther
                            + programs.blockEntityRenderersLeftOther) + " had left another one" + problems);
            // Only has something to check when something glows or a chest is in view.
            report.check("world-programs-outlines-left-to-vanilla", programs.outlineProblems == 0
                            && programs.outlineResumeProblems == 0 && programs.blockEntityMismatches == 0,
                    programs.outlineProblems + " of " + programs.outlineStarts + " outline starts kept a Focalis"
                            + " program, " + programs.outlineResumeProblems + " of " + programs.outlineResumes
                            + " outline ends didn't bind the entities program again, "
                            + programs.blockEntityMismatches + " of " + programs.blockEntitySamples
                            + " chests drew with another program" + problems);
            report.check("world-programs-samplers-set", programs.samplerMismatches == 0,
                    programs.samplerMismatches + " of " + programs.samplerChecks + " sampler units wrong, held "
                            + programs.samplers);
            report.check("world-programs-texture-state-kept", programs.textureStateChecks > 0
                            && programs.textureStateChanges == 0,
                    programs.textureStateChanges + " of " + programs.textureStateChecks + " stage starts and ends"
                            + " changed the active texture unit or its texture" + problems);
            // Only has something to check when a sheep or a chest is in view.
            report.check("world-programs-draw-textures", programs.lightmapMismatches == 0
                            && programs.ownTextureMismatches == 0,
                    programs.lightmapMismatches + " of " + programs.lightmapChecks + " draws without the lightmap on"
                            + " unit 1, " + programs.ownTextureMismatches + " of " + programs.ownTextureChecks
                            + " without their own texture on unit 0" + problems);
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
        public void programsBuilt(ProgramDirectory directory, BuiltWorldPrograms programs) {
            QaReport.WorldPrograms world = report.worldPrograms;
            world.builds++;
            world.folderBuilds.merge(directory.path(), 1, Integer::sum);
            Map<String, QaReport.BuiltProgram> roles = new TreeMap<>();
            for (BuiltWorldPrograms.Entry entry : programs.entries().values()) {
                ShaderProgram program = entry.program();
                if (entry.ready() && program != null) {
                    focalisPrograms.add(program.id());
                    roles.put(entry.role().name(), new QaReport.BuiltProgram(program.name(), program.id()));
                }
            }
            world.folderRoles.put(directory.path(), roles);
            for (BuiltWorldPrograms.Build build : programs.builds()) {
                ShaderProgram program = build.program();
                WorldProgramInputs inputs = build.inputs();
                if (program != null && inputs != null) {
                    readSamplers(program, inputs);
                }
            }
        }

        // What the program really holds, read back from GL rather than taken from what Focalis meant to set.
        private void readSamplers(ShaderProgram program, WorldProgramInputs inputs) {
            QaReport.WorldPrograms world = report.worldPrograms;
            StringBuilder held = new StringBuilder();
            for (WorldSampler sampler : WorldSampler.values()) {
                int location = inputs.location(sampler);
                held.append(held.length() == 0 ? "" : " ").append(sampler.uniformName()).append('=');
                if (location < 0) {
                    held.append('-');
                    continue;
                }
                IntBuffer value = BufferUtils.createIntBuffer(16);
                GL20.glGetUniform(program.id(), location, value);
                int unit = value.get(0);
                held.append(unit);
                world.samplerChecks++;
                if (unit != sampler.unit()) {
                    world.samplerMismatches++;
                    QaReport.addCapped(world.problems, program.name() + " holds " + unit + " in "
                            + sampler.uniformName() + " instead of " + sampler.unit());
                }
            }
            world.samplers.put(program.name(), held.toString());
        }

        // Every check during the world pass compares with the programs selected for it.
        @Override
        public void programsSelected(int dimension, ProgramDirectory directory, BuiltWorldPrograms programs) {
            QaReport.WorldPrograms world = report.worldPrograms;
            Arrays.fill(rolePrograms, 0);
            world.roles.clear();
            for (BuiltWorldPrograms.Entry entry : programs.entries().values()) {
                ShaderProgram program = entry.program();
                if (entry.ready() && program != null) {
                    rolePrograms[entry.role().ordinal()] = program.id();
                    world.roles.put(entry.role().name(), new QaReport.BuiltProgram(program.name(), program.id()));
                }
            }
            programDimension = String.valueOf(dimension);
            QaReport.addCapped(world.selections, dimension + " " + directory.path());
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
