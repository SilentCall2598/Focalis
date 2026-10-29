// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.shader.session;

import io.github.silentcall2598.focalis.render.lifecycle.RenderDrawKind;
import io.github.silentcall2598.focalis.render.lifecycle.RenderObjectListener;
import io.github.silentcall2598.focalis.render.lifecycle.RenderPhase;
import io.github.silentcall2598.focalis.render.lifecycle.RenderStage;
import io.github.silentcall2598.focalis.render.state.FragmentCache;
import io.github.silentcall2598.focalis.render.state.FragmentHold;
import io.github.silentcall2598.focalis.shader.WorldProgramMonitor;
import io.github.silentcall2598.focalis.shader.material.MaterialIds;
import io.github.silentcall2598.focalis.shader.material.MaterialReport;
import io.github.silentcall2598.focalis.shader.pack.MaterialMappings;
import io.github.silentcall2598.focalis.shader.pack.LegacyLighting;
import io.github.silentcall2598.focalis.shader.pack.PackConfiguration;
import io.github.silentcall2598.focalis.shader.pack.PackTextures;
import io.github.silentcall2598.focalis.shader.pack.ProgramDirectory;
import io.github.silentcall2598.focalis.shader.pack.ProgramSource;
import io.github.silentcall2598.focalis.shader.pack.ShaderPack;
import io.github.silentcall2598.focalis.shader.program.BuiltWorldPrograms;
import io.github.silentcall2598.focalis.shader.program.CameraInputs;
import io.github.silentcall2598.focalis.shader.program.CameraSnapshot;
import io.github.silentcall2598.focalis.shader.program.CameraStream;
import io.github.silentcall2598.focalis.shader.program.DirectoryPrograms;
import io.github.silentcall2598.focalis.shader.program.EnvironmentInputs;
import io.github.silentcall2598.focalis.shader.program.FrameInputs;
import io.github.silentcall2598.focalis.shader.program.LiveWorldPrograms;
import io.github.silentcall2598.focalis.shader.program.PreparedWorldPrograms;
import io.github.silentcall2598.focalis.shader.program.ProgramFailure;
import io.github.silentcall2598.focalis.shader.program.ProgramTextures;
import io.github.silentcall2598.focalis.shader.program.ShaderProgram;
import io.github.silentcall2598.focalis.shader.program.VertexInputs;
import io.github.silentcall2598.focalis.shader.program.WorldProgramBinding;
import io.github.silentcall2598.focalis.shader.program.WorldProgramInputs;
import io.github.silentcall2598.focalis.shader.routing.ResolutionState;
import io.github.silentcall2598.focalis.shader.routing.ShaderProgramRole;
import io.github.silentcall2598.focalis.shader.terrain.StreamLayout;
import net.minecraft.entity.Entity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import org.apache.logging.log4j.Logger;
import org.lwjgl.opengl.GL11;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class SessionPrograms {

    private final ShaderSession session;
    private final ClientView client;
    private final StreamControl streams;
    private final ProgramsFactory factory;
    private final WorldProgramMonitor monitor;
    private final Logger logger;
    @Nullable
    private LiveWorldPrograms live;
    @Nullable
    private MaterialIds materialIds;
    @Nullable
    private MaterialIds loggedUnfitIds;
    private final float[] modelView = new float[16];
    private final float[] projection = new float[16];
    private final float[] stageProjection = new float[16];
    private final double[] position = new double[3];
    @Nullable
    private CameraStream pendingStream;
    private RenderDrawKind pendingKind = RenderDrawKind.DEFAULT;
    private float pendingPartialTicks;
    private double pendingX;
    private double pendingY;
    private double pendingZ;
    private boolean loggedSingularCamera;
    private boolean checkedStateHooks;
    private boolean stopped;
    private boolean released;
    @Nullable
    private DirectoryPrograms lastSelected;
    private int lastDimension;
    @Nullable
    private DirectoryPrograms justBuilt;
    private final Set<Integer> loggedDimensions = new HashSet<>();
    private final ObjectContext objects = new ObjectContext();

    SessionPrograms(ShaderSession session, ClientView client, StreamControl streams, ProgramsFactory factory,
            WorldProgramMonitor monitor, Logger logger) {
        this.session = session;
        this.client = client;
        this.streams = streams;
        this.factory = factory;
        this.monitor = monitor;
        this.logger = logger;
    }

    @Nullable
    public LiveWorldPrograms live() {
        return live;
    }

    @Nullable
    public MaterialIds materialIds() {
        return materialIds;
    }

    public boolean stopped() {
        return stopped;
    }

    void configured(PackConfiguration configuration) {
        monitor.packConfigured(configuration);
    }

    public RenderObjectListener objects() {
        return objects;
    }

    private final class ObjectContext implements RenderObjectListener {

        @Override
        public void entityStarted(Entity entity) {
            LiveWorldPrograms current = live;
            if (current == null) {
                return;
            }
            MaterialIds ids = materialIds;
            int id = ids == null ? MaterialIds.UNMAPPED : ids.entity(entity);
            current.objectStart(RenderDrawKind.ENTITY, id);
            monitor.objectStarted(RenderDrawKind.ENTITY, entity, id);
        }

        @Override
        public void blockEntityStarted(@Nullable TileEntity blockEntity, boolean damaged) {
            LiveWorldPrograms current = live;
            if (current == null) {
                return;
            }
            int id = blockEntityId(materialIds, blockEntity);
            RenderDrawKind kind = damaged ? RenderDrawKind.DAMAGED_BLOCK_ENTITY : RenderDrawKind.BLOCK_ENTITY;
            current.objectStart(kind, id);
            monitor.objectStarted(kind, blockEntity, id);
        }

        @Override
        public void objectEnded() {
            LiveWorldPrograms current = live;
            if (current != null) {
                current.objectEnd();
                monitor.objectEnded();
            }
        }

        @Override
        public void objectColor(float red, float green, float blue, float alpha) {
            LiveWorldPrograms current = live;
            if (current != null) {
                current.objectColor(red, green, blue, alpha);
            }
        }

        @Override
        public void specialDrawStarted(RenderDrawKind kind) {
            LiveWorldPrograms current = live;
            if (current != null) {
                current.specialStart(kind);
                monitor.specialDrawStarted(kind);
            }
        }

        @Override
        public void specialDrawEnded(RenderDrawKind kind) {
            LiveWorldPrograms current = live;
            if (current != null) {
                current.specialEnd(kind);
                monitor.specialDrawEnded(kind);
            }
        }

        @Override
        public void specialDrawsAbandoned() {
            LiveWorldPrograms current = live;
            if (current != null) {
                current.specialsAbandoned();
                monitor.specialDrawEnded(RenderDrawKind.DEFAULT);
            }
        }
    }

    static int blockEntityId(@Nullable MaterialIds ids, @Nullable TileEntity blockEntity) {
        if (ids == null || blockEntity == null) {
            return MaterialIds.UNMAPPED;
        }
        World world = blockEntity.getWorld();
        if (world == null) {
            return MaterialIds.UNMAPPED;
        }
        return ids.block(world.getBlockState(blockEntity.getPos()));
    }

    public void world(RenderPhase phase) {
        if (phase == RenderPhase.START) {
            worldStart();
            return;
        }
        LiveWorldPrograms current = live;
        if (current != null) {
            current.worldEnd();
            checkStateHooks();
        }
    }

    private void checkStateHooks() {
        if (checkedStateHooks) {
            return;
        }
        checkedStateHooks = true;
        if (!FragmentHold.hooked()) {
            logger.warn("The GlStateManager alpha and blend hooks never ran, so vanilla's own alpha and blend calls in"
                    + " the sky, weather, clouds, breaking overlay and special draws replace the pack's directives"
                    + " there. The Focalis Mixin probably didn't apply. Mixin messages about mixins.focalis.json"
                    + " earlier in the log say why.");
        }
        if (!FragmentCache.available()) {
            logger.warn("GlStateManager's alpha and blend cache wasn't found, so Focalis only compares the real GL"
                    + " state when it applies or puts back directives.");
        }
    }

    private void worldStart() {
        checkTerrainStreams();
        FrameInputs frame = session.frame();
        if (!frame.captured()) {
            session.captureFrame();
        }
        session.camera().worldPassStarted();
        pendingStream = null;
        LiveWorldPrograms current = live;
        if (current == null && !stopped && !released) {
            current = start();
        }
        if (current == null) {
            return;
        }
        PackConfiguration configuration = current.configuration();
        MaterialIds ids = materialIds;
        if (ids == null || ids.mappings() != configuration.materialMappings()) {
            resolveMaterials(configuration);
        }
        if (client.world() == null) {
            current.worldStartWithoutWorld();
            lastSelected = null;
            return;
        }
        int dimension = client.dimension();
        justBuilt = null;
        DirectoryPrograms selected = current.worldStart(dimension);
        if (selected != lastSelected || dimension != lastDimension) {
            selectionChanged(configuration.pack(), dimension, selected, selected == justBuilt);
        }
        updateTerrainStreams(selected.programs(), dimension, configuration.lighting());
    }

    private void updateTerrainStreams(BuiltWorldPrograms programs, int dimension, LegacyLighting lighting) {
        boolean needed = false;
        for (ShaderProgramRole role : WorldProgramBinding.TERRAIN_ROLES) {
            BuiltWorldPrograms.Entry entry = programs.forRole(role);
            if (entry.ready()) {
                VertexInputs inputs = entry.build().program().vertexInputs();
                needed |= (inputs.needsStream() || lighting.changesTerrainColors()) && inputs.problem() == null;
            }
        }
        MaterialIds ids = materialIds;
        if (needed && ids != null) {
            if (streams.publish(ids, lighting)) {
                logger.info("Chunk terrain in dimension {} gets gl_Normal, mc_Entity, mc_midTexCoord and at_tangent"
                        + " from Focalis's attribute stream, so the renderers reload once.", dimension);
                if (lighting.changesTerrainColors()) {
                    logger.info("The pack's lighting settings ({}) change the colors of chunk terrain, so the stream"
                            + " carries them as gl_Color: {}. Chunk quads whose colors can't be taken from the"
                            + " renderer that made them keep vanilla's.", lighting, lightingSummary(lighting));
                }
                logUnfitIds(ids);
            }
        } else if (streams.active()) {
            streams.withdraw(false);
            logger.info("No terrain program in dimension {} reads the attribute stream, so chunks are built"
                    + " without it.", dimension);
        }
    }

    private static String lightingSummary(LegacyLighting lighting) {
        return (lighting.faceShading() ? "face shading in rgb" : "no face shading")
                + (lighting.separateAo() ? ", ambient occlusion in alpha" : ", ambient occlusion in rgb");
    }

    private void logUnfitIds(MaterialIds ids) {
        if (ids == loggedUnfitIds) {
            return;
        }
        loggedUnfitIds = ids;
        List<MaterialMappings.Declaration> unfit = new ArrayList<>();
        for (MaterialMappings.Declaration declaration : ids.mappings().declarations(MaterialMappings.Kind.BLOCK)) {
            if (declaration.id() > StreamLayout.MAX_ENTITY_ID) {
                unfit.add(declaration);
            }
        }
        if (!unfit.isEmpty()) {
            logger.warn("{} block IDs are above {}, the largest mc_Entity can hold, so blocks mapped to them read as"
                    + " unmapped, -1, in terrain programs: {}", unfit.size(), StreamLayout.MAX_ENTITY_ID,
                    unfit.subList(0, Math.min(unfit.size(), 5)));
        }
    }

    private void checkTerrainStreams() {
        Throwable failure = streams.failure();
        if (failure != null) {
            throw new IllegalStateException("The chunk attribute stream failed", failure);
        }
    }

    @Nullable
    private LiveWorldPrograms start() {
        PackConfiguration configuration = session.configuration();
        if (configuration == null) {
            stop(session.unconfiguredReason());
            return null;
        }
        LiveWorldPrograms.BuildListener listener = new LiveWorldPrograms.BuildListener() {
            @Override
            public void built(DirectoryPrograms programs) {
                folderBuilt(programs);
            }

            @Override
            public void texturesMade(ProgramTextures textures) {
                logTextures(textures);
            }
        };
        live = factory.create(configuration, session.macros(), listener, session.frame(), session.camera(),
                session.environment(), session.fog());
        live.terrainStreams(streams.gate());
        if (!session.fog().available()) {
            logger.warn("Minecraft's fog can't be read in this session, so programs that use the fog uniforms draw"
                    + " the normal way. Messages about mixins.focalis.json earlier in the log may say why.");
        }
        return live;
    }

    private void resolveMaterials(PackConfiguration configuration) {
        if (!client.registriesComplete()) {
            return;
        }
        MaterialIds ids = client.resolveMaterials(configuration.materialMappings());
        materialIds = ids;
        logMaterials(configuration.pack().name(), ids.report());
        monitor.materialsResolved(ids);
    }

    private void logMaterials(String packName, MaterialReport report) {
        if (report.notes().isEmpty() && report.mapped(MaterialMappings.Kind.BLOCK) == 0
                && report.mapped(MaterialMappings.Kind.ITEM) == 0 && report.mapped(MaterialMappings.Kind.ENTITY) == 0) {
            return;
        }
        logger.info("Shaderpack '{}' maps {} block states of {} blocks, {} items and {} entity types to material IDs,"
                + " resolved in {} ms. Chunk terrain receives block IDs, entities and block entities in the entity"
                + " pass receive theirs as uniforms, and item IDs aren't used yet.", packName,
                report.mapped(MaterialMappings.Kind.BLOCK), report.blocks(), report.mapped(MaterialMappings.Kind.ITEM),
                report.mapped(MaterialMappings.Kind.ENTITY),
                String.format(Locale.ROOT, "%.1f", report.nanos() / 1_000_000.0));
        logNotes(report, MaterialReport.Problem.UNKNOWN_NAME, "name things this game doesn't have");
        logNotes(report, MaterialReport.Problem.UNKNOWN_PROPERTY, "name block properties that don't exist, which are"
                + " left out");
        logNotes(report, MaterialReport.Problem.UNKNOWN_VALUE, "name property values that don't exist, which match"
                + " nothing");
        logNotes(report, MaterialReport.Problem.CONFLICT, "match things an earlier line already maps, which keep the"
                + " earlier ID");
        List<MaterialReport.Note> failed = report.notes(MaterialReport.Problem.FAILED);
        if (!failed.isEmpty()) {
            logger.warn("{} material mapping entries couldn't be read and map nothing: {}", failed.size(),
                    failed.subList(0, Math.min(failed.size(), 5)));
        }
    }

    private void logNotes(MaterialReport report, MaterialReport.Problem problem, String what) {
        List<MaterialReport.Note> notes = report.notes(problem);
        if (!notes.isEmpty()) {
            logger.info("{} material mapping entries {}, like {}", notes.size(), what,
                    notes.subList(0, Math.min(notes.size(), 3)));
        }
    }

    private void logTextures(ProgramTextures textures) {
        for (ProgramTextures.Outcome outcome : textures.outcomes()) {
            PackTextures.Declaration declaration = outcome.declaration();
            if (outcome.failure() == null) {
                logger.info("Custom texture {} on unit {} from {} is ready, {}x{} with {} filtering, {}",
                        declaration.sampler(), outcome.unit(), declaration.file(), outcome.width(), outcome.height(),
                        outcome.linear() ? "linear" : "nearest", outcome.clamp() ? "clamped" : "repeating");
            } else {
                logger.error("Custom texture {} on unit {} can't be used, so programs that sample it draw the normal"
                        + " way. {}", declaration.sampler(), outcome.unit(), outcome.failure());
            }
        }
        monitor.texturesMade(textures);
    }

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
            if (program != null) {
                logVertexInputs(programs, build, program);
            }
        }
    }

    private void logVertexInputs(BuiltWorldPrograms programs, BuiltWorldPrograms.Build build, ShaderProgram program) {
        VertexInputs vertex = program.vertexInputs();
        if (!vertex.needsStream()) {
            return;
        }
        List<ShaderProgramRole> terrain = new ArrayList<>();
        List<ShaderProgramRole> other = new ArrayList<>();
        for (ShaderProgramRole role : rolesOf(programs, build)) {
            (WorldProgramBinding.TERRAIN_ROLES.contains(role) ? terrain : other).add(role);
        }
        if (!terrain.isEmpty() && vertex.problem() != null) {
            logger.warn("{} reads {} from the chunk attribute stream, but {}, so {} draw the normal way.",
                    program.name(), vertex, vertex.problem(), terrain);
        }
        if (!other.isEmpty() && vertex.readsStreamAttributes()) {
            logger.info("{} reads {}, which only chunk terrain gets so far, so {} draw the normal way.",
                    program.name(), vertex, other);
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

    private static List<ShaderProgramRole> readyRoles(BuiltWorldPrograms programs) {
        List<ShaderProgramRole> roles = new ArrayList<>();
        for (BuiltWorldPrograms.Entry entry : programs.entries().values()) {
            if (entry.ready() && entry.role() != ShaderProgramRole.HAND) {
                roles.add(entry.role());
            }
        }
        return roles;
    }

    public void stage(RenderStage stage, RenderPhase phase, RenderDrawKind drawKind) {
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
        if (current.stageStart(stage, drawKind, client.vanillaDraws(stage))) {
            monitor.scopeStarted(stage, drawKind);
        }
    }

    public void vanillaPrograms(RenderStage stage, RenderPhase phase, RenderDrawKind drawKind) {
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

    public void rendererReturned() {
        LiveWorldPrograms current = live;
        if (current != null) {
            current.rendererReturned();
        }
    }

    public void camera(RenderPhase phase, RenderDrawKind drawKind, float partialTicks) {
        if (phase != RenderPhase.END || live == null) {
            return;
        }
        Object world = client.world();
        Object entity = client.viewEntity();
        if (world == null || entity == null) {
            return;
        }
        client.readMatrix(GL11.GL_MODELVIEW_MATRIX, modelView);
        client.readMatrix(GL11.GL_PROJECTION_MATRIX, projection);
        client.viewPosition(partialTicks, position);
        pendingStream = new CameraStream(world, client.dimension(), entity, client.perspective(),
                client.displayWidth(), client.displayHeight());
        pendingKind = drawKind;
        pendingPartialTicks = partialTicks;
        pendingX = position[0];
        pendingY = position[1];
        pendingZ = position[2];
    }

    // Right before the pass's first world stage binds anything. The projection set up for that stage is what the world
    // draws with, which at 4 or more chunks of render distance has lost anaglyph's eye offset again. CameraInputs
    // takes the x and y rows from it.
    private void finishCamera() {
        CameraStream stream = pendingStream;
        pendingStream = null;
        client.readMatrix(GL11.GL_PROJECTION_MATRIX, stageProjection);
        RenderDrawKind drawKind = pendingKind;
        CameraSnapshot snapshot = session.camera().capture(lane(drawKind), session.frame().sequence(), stream,
                pendingX, pendingY, pendingZ, modelView, projection, stageProjection);
        if (!(snapshot.modelViewInvertible() && snapshot.projectionInvertible()) && !loggedSingularCamera) {
            loggedSingularCamera = true;
            logger.warn("The camera's {} matrix has no inverse, so programs that use it draw the normal way until it"
                    + " has one again. This is only logged once.",
                    snapshot.modelViewInvertible() ? "projection" : "model-view");
        }
        monitor.cameraCaptured(drawKind, snapshot);
        captureEnvironment(snapshot, pendingPartialTicks);
    }

    private void captureEnvironment(CameraSnapshot snapshot, float partialTicks) {
        if (client.world() == null || client.viewEntity() == null) {
            return;
        }
        EnvironmentInputs environment = session.environment();
        if (!environment.hasFrame(snapshot.frame())
                && !client.captureFrameValues(environment, snapshot.frame(), partialTicks)) {
            return;
        }
        environment.capturePass(snapshot, client.medium(partialTicks),
                client.precipitation(snapshot.x(), snapshot.y(), snapshot.z()));
        monitor.environmentCaptured(snapshot, environment);
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

    public void frame(RenderPhase phase) {
        if (phase == RenderPhase.START) {
            checkTerrainStreams();
            streams.prepare(logger);
            session.captureFrame();
            if (client.world() == null) {
                session.leftWorld();
                pendingStream = null;
            }
            return;
        }
        LiveWorldPrograms current = live;
        if (current != null) {
            current.frameEnd();
        }
    }

    private void stop(String problem) {
        stopped = true;
        monitor.stopped(problem);
        logger.error("The world programs stopped for this session and the world draws the normal way. {}",
                problem);
    }

    void release() {
        if (released) {
            return;
        }
        released = true;
        streams.withdraw(true);
        LiveWorldPrograms current = live;
        live = null;
        materialIds = null;
        lastSelected = null;
        justBuilt = null;
        loggedDimensions.clear();
        pendingStream = null;
        if (current != null) {
            current.delete();
        }
    }
}
