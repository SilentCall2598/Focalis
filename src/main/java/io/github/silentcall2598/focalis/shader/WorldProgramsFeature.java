// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.shader;

import io.github.silentcall2598.focalis.compat.CompatibilityReport;
import io.github.silentcall2598.focalis.config.ConfigSection;
import io.github.silentcall2598.focalis.feature.Availability;
import io.github.silentcall2598.focalis.feature.Feature;
import io.github.silentcall2598.focalis.feature.FeatureContext;
import io.github.silentcall2598.focalis.render.lifecycle.RenderDrawKind;
import io.github.silentcall2598.focalis.render.lifecycle.RenderPhase;
import io.github.silentcall2598.focalis.render.lifecycle.RenderStage;
import io.github.silentcall2598.focalis.render.state.GlContextInfo;
import io.github.silentcall2598.focalis.shader.pack.ProgramSource;
import io.github.silentcall2598.focalis.shader.pack.ShaderPackException;
import io.github.silentcall2598.focalis.shader.program.BuiltWorldPrograms;
import io.github.silentcall2598.focalis.shader.program.LiveWorldPrograms;
import io.github.silentcall2598.focalis.shader.program.PreparedWorldPrograms;
import io.github.silentcall2598.focalis.shader.program.ProgramFailure;
import io.github.silentcall2598.focalis.shader.program.ShaderCapabilities;
import io.github.silentcall2598.focalis.shader.program.ShaderProgram;
import io.github.silentcall2598.focalis.shader.program.WorldProgramBinding;
import io.github.silentcall2598.focalis.shader.routing.ShaderProgramRole;
import net.minecraft.client.Minecraft;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nullable;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Experimental. Binds the pack program of each world stage while vanilla draws it, taken from the pack's main
 * shaders folder. Nothing else a shaderpack expects is set up, so regular packs won't look right. Problems with the
 * pack or its programs leave rendering vanilla. Only a Focalis bug fails the feature.
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
        packName = config.getString("pack", "", "Name of a folder or zip in the shaderpacks folder. Only its main"
                + " shaders folder is used, and no uniforms, composite passes or dimension folders are set up.");
    }

    @Override
    protected Availability checkAvailability(CompatibilityReport compat) {
        if (compat.isOptiFinePresent()) {
            return Availability.unavailable("OptiFine is installed and has its own shader support");
        }
        return Availability.AVAILABLE;
    }

    // Loading and preparing the pack is plain file work, so it happens here. Building the programs has to wait for
    // the first world pass, when the GL context is known to be current.
    @Override
    protected void setup(FeatureContext context) {
        logger = context.logger();
        if (packName.trim().isEmpty()) {
            logger.warn("No shaderpack is selected, so the world programs stay off. Set features.world_programs.pack"
                    + " in focalis.cfg.");
            return;
        }
        Path shaderpacks = Minecraft.getMinecraft().gameDir.toPath().resolve("shaderpacks");
        PreparedWorldPrograms programs;
        try {
            programs = PackSelection.prepareWorldPrograms(PackSelection.resolve(shaderpacks, packName));
        } catch (ShaderPackException e) {
            logger.error("Shaderpack '{}' can't be used, so the world draws the normal way. {}", packName,
                    e.getMessage());
            return;
        }
        logPreparationProblems(programs);
        if (programs.uniquePrograms().isEmpty()) {
            logger.error("Shaderpack '{}' has no world program that can be used, so the world draws the normal way.",
                    packName);
            return;
        }
        logger.info("Binding the world programs of shaderpack '{}'", packName);
        context.addRenderListener(RenderStage.WORLD, (stage, phase, drawKind, partialTicks) -> onWorld(phase,
                programs));
        context.addRenderListener(RenderStage.FRAME, this::onFrame);
        for (RenderStage stage : WorldProgramBinding.STAGES) {
            context.addRenderListener(stage, this::onStage);
        }
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
            logger.error("{} can't be prepared, so {} draw the normal way. {}", program.getKey().name(),
                    program.getValue(), programs.forRole(program.getValue().get(0)).problem());
        }
    }

    private void onWorld(RenderPhase phase, PreparedWorldPrograms programs) {
        if (phase == RenderPhase.START) {
            worldStart(programs);
            return;
        }
        LiveWorldPrograms current = live;
        if (current != null) {
            current.worldEnd();
        }
    }

    private void worldStart(PreparedWorldPrograms programs) {
        LiveWorldPrograms current = live;
        if (current == null && !stopped) {
            current = build(programs);
        }
        if (current != null) {
            current.worldStart();
        }
    }

    // Built once per session. A world reload or dimension change keeps the same programs.
    @Nullable
    private LiveWorldPrograms build(PreparedWorldPrograms programs) {
        GlContextInfo gl = glContext.get();
        if (gl == null) {
            stop("OpenGL information isn't available.");
            return null;
        }
        LiveWorldPrograms built = LiveWorldPrograms.build(programs, ShaderCapabilities.from(gl));
        // Owned right away, so cleanup deletes them if anything below throws.
        live = built;
        logBuildResults(built.programs());
        if (!built.usable()) {
            live = null;
            built.delete();
            stop("None of the world programs could be built.");
            return null;
        }
        logger.info("World programs are ready for {}", readyRoles(built.programs()));
        monitor.programsBuilt(built.programs());
        return built;
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

    private void onFrame(RenderStage stage, RenderPhase phase, RenderDrawKind drawKind, float partialTicks) {
        LiveWorldPrograms current = live;
        if (phase == RenderPhase.END && current != null) {
            current.frameEnd();
        }
    }

    // Pack and driver problems end up here. Neither is a Focalis bug, so the feature stays active and only the
    // binding stops.
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
        if (current != null) {
            current.delete();
        }
    }
}
