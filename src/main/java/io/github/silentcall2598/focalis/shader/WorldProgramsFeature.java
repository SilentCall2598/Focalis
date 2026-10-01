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
import io.github.silentcall2598.focalis.shader.pack.ProgramDirectory;
import io.github.silentcall2598.focalis.shader.pack.ProgramSource;
import io.github.silentcall2598.focalis.shader.pack.ShaderPack;
import io.github.silentcall2598.focalis.shader.pack.ShaderPackException;
import io.github.silentcall2598.focalis.shader.pack.ShaderPackLoader;
import io.github.silentcall2598.focalis.shader.program.BuiltWorldPrograms;
import io.github.silentcall2598.focalis.shader.program.DirectoryPrograms;
import io.github.silentcall2598.focalis.shader.program.LiveWorldPrograms;
import io.github.silentcall2598.focalis.shader.program.PreparedWorldPrograms;
import io.github.silentcall2598.focalis.shader.program.ProgramFailure;
import io.github.silentcall2598.focalis.shader.program.ShaderCapabilities;
import io.github.silentcall2598.focalis.shader.program.ShaderProgram;
import io.github.silentcall2598.focalis.shader.program.WorldProgramBinding;
import io.github.silentcall2598.focalis.shader.routing.ShaderProgramRole;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nullable;
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
 * Nothing else a shaderpack expects is set up, so regular packs won't look right. Problems with the pack or its
 * programs leave rendering vanilla. Only a Focalis bug fails the feature.
 */
public final class WorldProgramsFeature extends Feature {

    public static final String ID = "world_programs";

    private final Supplier<GlContextInfo> glContext;
    private final WorldProgramMonitor monitor;
    private String packName = "";
    private Logger logger;
    @Nullable
    private LiveWorldPrograms live;
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
     * @param monitor {@link WorldProgramMonitor#NONE} except in development QA runs
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
                + " in its shaders folder replaces the main one in that dimension. No uniforms or composite passes"
                + " are set up.");
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
        context.addRenderListener(RenderStage.WORLD, (stage, phase, drawKind, partialTicks) -> onWorld(phase, pack));
        context.addRenderListener(RenderStage.FRAME, this::onFrame);
        for (RenderStage stage : WorldProgramBinding.STAGES) {
            context.addRenderListener(stage, this::onStage);
        }
        for (RenderStage stage : WorldProgramBinding.VANILLA_PROGRAM_STAGES) {
            context.addRenderListener(stage, this::onVanillaPrograms);
        }
        context.addCheckpointListener(this::onRendererReturned);
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
        LiveWorldPrograms current = live;
        if (current == null && !stopped) {
            current = start(pack);
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
    private LiveWorldPrograms start(ShaderPack pack) {
        GlContextInfo gl = glContext.get();
        if (gl == null) {
            stop("OpenGL information isn't available.");
            return null;
        }
        // Owned right away, so cleanup deletes whatever it builds even if something throws later.
        live = LiveWorldPrograms.create(pack, ShaderCapabilities.from(gl), this::folderBuilt);
        return live;
    }

    // Once per folder and session. A folder that fails stays failed until the pack loads again.
    private void folderBuilt(DirectoryPrograms programs) {
        justBuilt = programs;
        ProgramDirectory directory = programs.directory();
        logPreparationProblems(programs.prepared());
        logBuildResults(programs.programs());
        if (directory.programs().isEmpty()) {
            logger.info("{} has no programs, so dimensions using it draw the normal way.", directory.path());
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
        } else if (current.stageStart(stage, drawKind)) {
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

    private void onFrame(RenderStage stage, RenderPhase phase, RenderDrawKind drawKind, float partialTicks) {
        LiveWorldPrograms current = live;
        if (phase == RenderPhase.END && current != null) {
            current.frameEnd();
        }
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
        if (current != null) {
            current.delete();
        }
    }
}
