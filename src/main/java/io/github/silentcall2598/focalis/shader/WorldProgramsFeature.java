// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.shader;

import io.github.silentcall2598.focalis.compat.CompatibilityReport;
import io.github.silentcall2598.focalis.config.ConfigSection;
import io.github.silentcall2598.focalis.feature.Availability;
import io.github.silentcall2598.focalis.feature.Feature;
import io.github.silentcall2598.focalis.feature.FeatureContext;
import io.github.silentcall2598.focalis.render.lifecycle.RenderCheckpoint;
import io.github.silentcall2598.focalis.render.lifecycle.RenderDrawKind;
import io.github.silentcall2598.focalis.render.lifecycle.RenderPhase;
import io.github.silentcall2598.focalis.render.lifecycle.RenderStage;
import io.github.silentcall2598.focalis.render.state.GlContextInfo;
import io.github.silentcall2598.focalis.shader.pack.CustomUniforms;
import io.github.silentcall2598.focalis.shader.pack.PackConfiguration;
import io.github.silentcall2598.focalis.shader.pack.PackIssue;
import io.github.silentcall2598.focalis.shader.pack.PackSettings;
import io.github.silentcall2598.focalis.shader.pack.ProgramDirectory;
import io.github.silentcall2598.focalis.shader.pack.ProgramSource;
import io.github.silentcall2598.focalis.shader.pack.ShaderPack;
import io.github.silentcall2598.focalis.shader.pack.ShaderPackException;
import io.github.silentcall2598.focalis.shader.pack.ShaderPackLoader;
import io.github.silentcall2598.focalis.shader.pack.StandardMacros;
import io.github.silentcall2598.focalis.shader.program.BuiltWorldPrograms;
import io.github.silentcall2598.focalis.shader.program.CameraInputs;
import io.github.silentcall2598.focalis.shader.program.CameraSnapshot;
import io.github.silentcall2598.focalis.shader.program.CameraStream;
import io.github.silentcall2598.focalis.shader.program.DirectoryPrograms;
import io.github.silentcall2598.focalis.shader.program.EnvironmentInputs;
import io.github.silentcall2598.focalis.shader.program.FogSource;
import io.github.silentcall2598.focalis.shader.program.FrameInputs;
import io.github.silentcall2598.focalis.shader.program.LiveWorldPrograms;
import io.github.silentcall2598.focalis.shader.program.PreparedWorldPrograms;
import io.github.silentcall2598.focalis.shader.program.ProgramFailure;
import io.github.silentcall2598.focalis.shader.program.ShaderCapabilities;
import io.github.silentcall2598.focalis.shader.program.ShaderProgram;
import io.github.silentcall2598.focalis.shader.program.WorldProgramBinding;
import io.github.silentcall2598.focalis.shader.program.WorldProgramInputs;
import io.github.silentcall2598.focalis.shader.routing.ResolutionState;
import io.github.silentcall2598.focalis.shader.routing.ShaderProgramRole;
import net.minecraft.block.material.Material;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.ActiveRenderInfo;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;
import org.apache.logging.log4j.Logger;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import javax.annotation.Nullable;
import java.nio.FloatBuffer;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Experimental. Binds the pack program of each world stage while vanilla draws it. Each dimension takes its programs
 * from the pack's {@code world<id>} folder for it when there is one and from the main shaders folder otherwise.
 * The legacy texture and lightmap samplers read the units Minecraft binds those textures on, the view size, frame
 * timing and world values are set every frame, the camera position, matrices and values in eye space every world pass,
 * and the fog whenever a stage binds its program. Custom uniforms from shaders.properties are computed once per world
 * pass from those same values. Nothing else a shaderpack expects is set up, so regular packs won't
 * look right. Problems with the pack or its programs leave rendering vanilla. Only a Focalis bug fails the feature.
 */
public final class WorldProgramsFeature extends Feature {

    public static final String ID = "world_programs";

    private final Supplier<GlContextInfo> glContext;
    private final WorldProgramMonitor monitor;
    private String packName = "";
    private String profileName = "";
    private String optionEntries = "";
    private Logger logger;
    private PackConfiguration configuration;
    @Nullable
    private LiveWorldPrograms live;
    // Shared by every program in every dimension, and never reset, since a turned off feature stays off.
    private final FrameInputs frame = new FrameInputs();
    // Also shared, but its history is dropped whenever there is no world, so it never keeps one alive.
    private final CameraInputs camera = new CameraInputs();
    // Only plain values, taken again every frame and pass, so nothing in it outlives a world.
    private final EnvironmentInputs environment = new EnvironmentInputs();
    private final FogSource fog = new MinecraftFog();
    private final FloatBuffer matrixBuffer = BufferUtils.createFloatBuffer(16);
    private final float[] modelView = new float[16];
    private final float[] projection = new float[16];
    private final float[] stageProjection = new float[16];
    // The camera read at CAMERA END, until the pass's first world stage turns it into a snapshot. Null otherwise, so
    // it never holds on to a world.
    @Nullable
    private CameraStream pendingStream;
    private RenderDrawKind pendingKind = RenderDrawKind.DEFAULT;
    private float pendingPartialTicks;
    private double pendingX;
    private double pendingY;
    private double pendingZ;
    private boolean loggedSingularCamera;
    private boolean stopped;
    // What the last world pass selected, so a change of dimension or folder is noticed once.
    @Nullable
    private DirectoryPrograms lastSelected;
    private int lastDimension;
    @Nullable
    private DirectoryPrograms justBuilt;
    private final Set<Integer> loggedDimensions = new HashSet<>();

    /**
     * @param glContext returns null until the first frame has captured the context
     * @param monitor {@link WorldProgramMonitor#NONE} unless a development probe is attached
     */
    public WorldProgramsFeature(Supplier<GlContextInfo> glContext, WorldProgramMonitor monitor) {
        super(ID, "Experimental. Binds the world programs of a shaderpack while the world draws. For testing only,"
                + " regular shaderpacks are not supported yet.", false);
        this.glContext = glContext;
        this.monitor = monitor;
    }

    @Override
    protected void loadConfig(ConfigSection config) {
        packName = config.getString("pack", "", "Name of a folder or zip in the shaderpacks folder. A world<id> folder"
                + " in its shaders folder replaces the main one in that dimension. Only the texture and lightmap"
                + " samplers, the view size and frame timing uniforms, the camera position and matrices, and the"
                + " time, sun and moon, rain, sky and fog, eye brightness and water or lava uniforms are set, along"
                + " with the pack's custom uniforms that only use those, and no composite passes run.");
        profileName = config.getString("profile", "", "A profile from the pack's shaders.properties, like HIGH. Empty"
                + " keeps the pack's own defaults.");
        optionEntries = config.getString("options", "", "Option values applied after the profile, separated by"
                + " spaces, like SHADOW_QUALITY=3 BLOOM !SSAO. NAME=value or NAME:value sets a value, NAME turns a"
                + " switch on and !NAME turns it off. Only values the pack allows are used.");
    }

    @Override
    protected Availability checkAvailability(CompatibilityReport compat) {
        if (compat.isOptiFinePresent()) {
            return Availability.unavailable("OptiFine is installed and has its own shader support");
        }
        return Availability.AVAILABLE;
    }

    // Loading the pack is plain file work, so it happens here. Building the programs has to wait for a world pass,
    // when the GL context is known to be current and the dimension is known.
    @Override
    protected void setup(FeatureContext context) {
        logger = context.logger();
        if (packName.trim().isEmpty()) {
            logger.warn("No shaderpack is selected, so the world programs stay off. Set features.world_programs.pack"
                    + " in focalis.cfg.");
            return;
        }
        Path shaderpacks = Minecraft.getMinecraft().gameDir.toPath().resolve("shaderpacks");
        ShaderPack pack;
        try {
            pack = ShaderPackLoader.load(PackSelection.resolve(shaderpacks, packName));
        } catch (ShaderPackException e) {
            logger.error("Shaderpack '{}' can't be used, so the world draws the normal way. {}", packName,
                    e.getMessage());
            return;
        }
        logger.info("Binding the world programs of shaderpack '{}', with dimension folders {}", packName,
                pack.dimensionDirectories().keySet());
        configure(pack);
        context.addRenderListener(RenderStage.WORLD, (stage, phase, drawKind, partialTicks) -> onWorld(phase, pack));
        context.addRenderListener(RenderStage.FRAME, this::onFrame);
        context.addRenderListener(RenderStage.CAMERA, this::onCamera);
        for (RenderStage stage : WorldProgramBinding.STAGES) {
            context.addRenderListener(stage, this::onStage);
        }
        for (RenderStage stage : WorldProgramBinding.VANILLA_PROGRAM_STAGES) {
            context.addRenderListener(stage, this::onVanillaPrograms);
        }
        context.addCheckpointListener(this::onRendererReturned);
    }

    // Settings that don't fit the pack are skipped and logged, so a typo never turns the programs off.
    private void configure(ShaderPack pack) {
        configuration = PackConfiguration.resolve(pack, PackSettings.of(profileName, optionEntries),
                StandardMacros.environment());
        for (PackIssue issue : configuration.issues()) {
            logger.warn("Shaderpack configuration: {}", issue);
        }
        logger.info("Shaderpack '{}' uses profile {} with {} of {} options changed {}, and these programs disabled {}",
                packName, configuration.profile() == null ? "<none>" : configuration.profile(),
                configuration.changed().size(), pack.options().all().size(), configuration.changed(),
                configuration.disabledPrograms());
        CustomUniforms custom = configuration.customUniforms();
        if (!custom.declarations().isEmpty()) {
            logger.info("Shaderpack '{}' has {} custom uniforms and variables, {} of them usable, with uniforms {}",
                    packName, custom.declarations().size(), custom.evaluatedCount(), custom.uniforms().keySet());
        }
        monitor.packConfigured(configuration);
    }

    private void onWorld(RenderPhase phase, ShaderPack pack) {
        if (phase == RenderPhase.START) {
            worldStart(pack);
            return;
        }
        LiveWorldPrograms current = live;
        if (current != null) {
            current.worldEnd();
        }
    }

    // The dimension is read once per world pass, here. Nothing during the pass looks it up again.
    private void worldStart(ShaderPack pack) {
        // Only when no FRAME START came first, which Minecraft doesn't do but a mod drawing the world could.
        if (!frame.captured()) {
            captureFrame();
        }
        camera.worldPassStarted();
        pendingStream = null;
        LiveWorldPrograms current = live;
        if (current == null && !stopped) {
            current = start();
        }
        if (current == null) {
            return;
        }
        WorldClient world = Minecraft.getMinecraft().world;
        if (world == null) {
            current.worldStartWithoutWorld();
            lastSelected = null;
            return;
        }
        int dimension = world.provider.getDimension();
        justBuilt = null;
        DirectoryPrograms selected = current.worldStart(dimension);
        if (selected != lastSelected || dimension != lastDimension) {
            selectionChanged(pack, dimension, selected, selected == justBuilt);
        }
    }

    @Nullable
    private LiveWorldPrograms start() {
        GlContextInfo gl = glContext.get();
        if (gl == null) {
            stop("OpenGL information isn't available.");
            return null;
        }
        // Owned right away, so cleanup deletes whatever it builds even if something throws later.
        live = LiveWorldPrograms.create(configuration, ShaderCapabilities.from(gl), this::folderBuilt, frame, camera,
                environment, fog);
        if (!fog.available()) {
            logger.warn("Minecraft's fog can't be read in this session, so programs that use the fog uniforms draw"
                    + " the normal way. Messages about mixins.focalis.json earlier in the log may say why.");
        }
        return live;
    }

    // Once per folder and session. A folder that fails stays failed until the pack loads again.
    private void folderBuilt(DirectoryPrograms programs) {
        justBuilt = programs;
        ProgramDirectory directory = programs.directory();
        logPreparationProblems(programs.prepared());
        logBuildResults(programs.programs());
        List<ShaderProgramRole> disabled = new ArrayList<>();
        for (PreparedWorldPrograms.Entry entry : programs.prepared().entries().values()) {
            if (entry.resolution().state() == ResolutionState.DISABLED) {
                disabled.add(entry.role());
            }
        }
        if (!disabled.isEmpty()) {
            logger.info("The pack configuration disabled every program {} could use in {}, so they draw the normal"
                    + " way.", disabled, directory.path());
        }
        if (directory.programs().isEmpty()) {
            logger.info("{} has no programs, so dimensions using it draw the normal way.", directory.path());
        } else if (!programs.usable() && !disabled.isEmpty() && programs.programs().builds().isEmpty()) {
            // Nothing failed here, the pack just turned everything off.
            logger.info("Nothing in {} is left to build under the pack configuration, so dimensions using it draw the"
                    + " normal way.", directory.path());
        } else if (!programs.usable()) {
            logger.error("None of the world programs in {} could be built, so dimensions using it draw the normal"
                    + " way for this session.", directory.path());
        } else {
            logger.info("World programs in {} are ready for {}", directory.path(), readyRoles(programs.programs()));
        }
        monitor.programsBuilt(directory, programs.programs());
    }

    private void selectionChanged(ShaderPack pack, int dimension, DirectoryPrograms selected, boolean built) {
        lastSelected = selected;
        lastDimension = dimension;
        ProgramDirectory directory = selected.directory();
        String source = directory == pack.root()
                ? directory.path() + " since the pack has no " + ProgramDirectory.worldFolder(dimension) + " folder"
                : directory.path() + " instead of " + pack.root().path();
        String state = (selected.usable() ? "" : ", which draws the normal way") + (built ? "" : ", built earlier");
        if (loggedDimensions.add(dimension)) {
            logger.info("Dimension {} uses the world programs in {}{}", dimension, source, state);
        } else {
            logger.debug("Dimension {} uses the world programs in {}{}", dimension, source, state);
        }
        monitor.programsSelected(dimension, directory, selected.programs());
    }

    // Roles that share a pack program share its problem, so each program is only logged once.
    private void logPreparationProblems(PreparedWorldPrograms programs) {
        Map<ProgramSource, List<ShaderProgramRole>> failed = new LinkedHashMap<>();
        for (PreparedWorldPrograms.Entry entry : programs.entries().values()) {
            ProgramSource source = entry.resolution().program();
            if (entry.problem() != null && source != null) {
                failed.computeIfAbsent(source, key -> new ArrayList<>()).add(entry.role());
            }
        }
        for (Map.Entry<ProgramSource, List<ShaderProgramRole>> program : failed.entrySet()) {
            ProgramSource source = program.getKey();
            logger.error("{} can't be prepared, so {} draw the normal way. {}", source.directory().isEmpty()
                    ? source.name() : source.directory() + "/" + source.name(), program.getValue(),
                    programs.forRole(program.getValue().get(0)).problem());
        }
    }

    private void logBuildResults(BuiltWorldPrograms programs) {
        for (BuiltWorldPrograms.Build build : programs.builds()) {
            ProgramFailure failure = build.failure();
            ShaderProgram program = build.program();
            if (failure != null) {
                logger.error("{} can't be built, so {} draw the normal way. {}", build.prepared().name(),
                        rolesOf(programs, build), failure.driverLog().isEmpty() ? failure
                                : failure + "\nFull driver log:\n" + failure.driverLog());
            } else if (program != null && !program.driverLog().isEmpty()) {
                logger.warn("{} was built, and the driver reported:\n{}", program.name(), program.driverLog());
            }
            WorldProgramInputs inputs = build.inputs();
            if (program != null && inputs != null && !inputs.unprovided().isEmpty()) {
                logger.info("{} uses uniforms Focalis doesn't set yet, so they read as zero: {}", program.name(),
                        inputs.unprovided());
            }
        }
    }

    private static List<ShaderProgramRole> rolesOf(BuiltWorldPrograms programs, BuiltWorldPrograms.Build build) {
        List<ShaderProgramRole> roles = new ArrayList<>();
        for (BuiltWorldPrograms.Entry entry : programs.entries().values()) {
            if (entry.build() == build) {
                roles.add(entry.role());
            }
        }
        return roles;
    }

    // HAND can have a program too, but it's never bound.
    private static List<ShaderProgramRole> readyRoles(BuiltWorldPrograms programs) {
        List<ShaderProgramRole> roles = new ArrayList<>();
        for (BuiltWorldPrograms.Entry entry : programs.entries().values()) {
            if (entry.ready() && entry.role() != ShaderProgramRole.HAND) {
                roles.add(entry.role());
            }
        }
        return roles;
    }

    private void onStage(RenderStage stage, RenderPhase phase, RenderDrawKind drawKind, float partialTicks) {
        LiveWorldPrograms current = live;
        if (current == null) {
            return;
        }
        if (phase == RenderPhase.END) {
            current.stageEnd(stage, drawKind);
            return;
        }
        if (pendingStream != null) {
            finishCamera();
        }
        if (current.stageStart(stage, drawKind)) {
            monitor.scopeStarted(stage, drawKind);
        }
    }

    // Vanilla's entity outlines bind their own shaders and draw the outlined entities expecting no program.
    private void onVanillaPrograms(RenderStage stage, RenderPhase phase, RenderDrawKind drawKind,
            float partialTicks) {
        LiveWorldPrograms current = live;
        if (current == null) {
            return;
        }
        if (phase == RenderPhase.START) {
            current.vanillaProgramsStart(stage, drawKind);
        } else {
            current.vanillaProgramsEnd(stage, drawKind);
        }
    }

    // Mod renderers can bind their own programs and return with another one bound, often 0. Each renderer may do
    // what it wants while it runs, and the next one gets the stage's program again.
    private void onRendererReturned(RenderCheckpoint checkpoint) {
        LiveWorldPrograms current = live;
        if (current != null) {
            current.rendererReturned();
        }
    }

    // At the END vanilla has just set up the pass's projection and model-view, before the sky and clouds swap in their
    // own projections and before anything draws. The only GL work is reading them back, once per pass. The snapshot
    // waits for the first world stage, see finishCamera.
    private void onCamera(RenderStage stage, RenderPhase phase, RenderDrawKind drawKind, float partialTicks) {
        if (phase != RenderPhase.END || live == null) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        WorldClient world = mc.world;
        Entity entity = mc.getRenderViewEntity();
        if (world == null || entity == null) {
            return;
        }
        readMatrix(GL11.GL_MODELVIEW_MATRIX, modelView);
        readMatrix(GL11.GL_PROJECTION_MATRIX, projection);
        // Where vanilla draws the world from, the same position it gives terrain, entities and particles.
        double x = entity.lastTickPosX + (entity.posX - entity.lastTickPosX) * partialTicks;
        double y = entity.lastTickPosY + (entity.posY - entity.lastTickPosY) * partialTicks;
        double z = entity.lastTickPosZ + (entity.posZ - entity.lastTickPosZ) * partialTicks;
        pendingStream = new CameraStream(world, world.provider.getDimension(), entity,
                mc.gameSettings.thirdPersonView, mc.displayWidth, mc.displayHeight);
        pendingKind = drawKind;
        pendingPartialTicks = partialTicks;
        pendingX = x;
        pendingY = y;
        pendingZ = z;
    }

    // Right before the pass's first world stage binds anything. The projection set up for that stage is what the world
    // draws with, which at 4 or more chunks of render distance has lost anaglyph's eye offset again. CameraInputs
    // takes the x and y rows from it. One more read, once per pass.
    private void finishCamera() {
        CameraStream stream = pendingStream;
        pendingStream = null;
        readMatrix(GL11.GL_PROJECTION_MATRIX, stageProjection);
        RenderDrawKind drawKind = pendingKind;
        CameraSnapshot snapshot = camera.capture(lane(drawKind), frame.sequence(), stream, pendingX, pendingY,
                pendingZ, modelView, projection, stageProjection);
        if (!(snapshot.modelViewInvertible() && snapshot.projectionInvertible()) && !loggedSingularCamera) {
            loggedSingularCamera = true;
            logger.warn("The camera's {} matrix has no inverse, so programs that use it draw the normal way until it"
                    + " has one again. This is only logged once.",
                    snapshot.modelViewInvertible() ? "projection" : "model-view");
        }
        monitor.cameraCaptured(drawKind, snapshot);
        captureEnvironment(snapshot, pendingPartialTicks);
    }

    // The world values once per frame, at the first pass's camera, and the rest for every pass. Vanilla has updated
    // where the camera is by now, which the medium needs. Without a world or view entity nothing is taken, and
    // programs that use these values draw the normal way in that pass.
    private void captureEnvironment(CameraSnapshot snapshot, float partialTicks) {
        Minecraft mc = Minecraft.getMinecraft();
        WorldClient world = mc.world;
        Entity entity = mc.getRenderViewEntity();
        if (world == null || entity == null) {
            return;
        }
        if (!environment.hasFrame(snapshot.frame())) {
            Vec3d sky = world.getSkyColor(entity, partialTicks);
            environment.captureFrame(snapshot.frame(), world.getWorldTime(), world.getMoonPhase(),
                    world.getCelestialAngle(partialTicks), world.getRainStrength(partialTicks), (float) sky.x,
                    (float) sky.y, (float) sky.z, entity.getBrightnessForRender());
        }
        environment.capturePass(snapshot, medium(world, entity, partialTicks));
        monitor.environmentCaptured(snapshot, environment);
    }

    // The block vanilla's setupFog checks for water and lava fog, at the camera and not at the player's eyes, so this
    // always agrees with the fog. Forge fluids count when their material is water or lava, like for the fog.
    private static int medium(WorldClient world, Entity entity, float partialTicks) {
        Material material = ActiveRenderInfo.getBlockStateAtEntityViewpoint(world, entity, partialTicks).getMaterial();
        if (material == Material.WATER) {
            return EnvironmentInputs.MEDIUM_WATER;
        }
        if (material == Material.LAVA) {
            return EnvironmentInputs.MEDIUM_LAVA;
        }
        return EnvironmentInputs.MEDIUM_AIR;
    }

    private void readMatrix(int matrix, float[] into) {
        FloatBuffer buffer = matrixBuffer;
        buffer.clear();
        GL11.glGetFloat(matrix, buffer);
        buffer.get(into, 0, 16);
    }

    private static int lane(RenderDrawKind drawKind) {
        switch (drawKind) {
            case CAMERA_ANAGLYPH_FIRST:
                return 0;
            case CAMERA_ANAGLYPH_SECOND:
                return 1;
            case CAMERA_SINGLE:
                return 2;
            default:
                return CameraInputs.NO_LANE;
        }
    }

    // Every displayed frame counts, also the ones without a world, like in menus.
    private void onFrame(RenderStage stage, RenderPhase phase, RenderDrawKind drawKind, float partialTicks) {
        if (phase == RenderPhase.START) {
            captureFrame();
            // Leaving a world lets go of it here, so no camera history can reach into the next one either.
            if (Minecraft.getMinecraft().world == null) {
                camera.clear();
                environment.clear();
                pendingStream = null;
            }
            return;
        }
        LiveWorldPrograms current = live;
        if (current != null) {
            current.frameEnd();
        }
    }

    // Minecraft only resizes its framebuffer after a frame or in a tick, never between FRAME START and the world
    // pass. The world target is always the same size as this framebuffer.
    private void captureFrame() {
        Framebuffer framebuffer = Minecraft.getMinecraft().getFramebuffer();
        frame.capture(System.nanoTime(), framebuffer.framebufferWidth, framebuffer.framebufferHeight);
    }

    // Without GL information nothing can be built in any dimension. That isn't a Focalis bug, so the feature stays
    // active and only the binding stops.
    private void stop(String problem) {
        stopped = true;
        monitor.stopped(problem);
        logger.error("The world programs stopped for this session and the world draws the normal way. {}",
                problem);
    }

    // GL objects only exist after a world pass. From then on the feature manager can only call this from a failed
    // render listener or a runtime disable, both on the client thread with the context current. Focalis never
    // cleans up at shutdown, when the context may already be gone.
    @Override
    protected void cleanup() {
        LiveWorldPrograms current = live;
        live = null;
        lastSelected = null;
        justBuilt = null;
        loggedDimensions.clear();
        camera.clear();
        environment.clear();
        pendingStream = null;
        if (current != null) {
            current.delete();
        }
    }
}
