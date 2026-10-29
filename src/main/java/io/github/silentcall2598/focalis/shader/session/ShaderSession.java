// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.shader.session;

import io.github.silentcall2598.focalis.config.ConfigSection;
import io.github.silentcall2598.focalis.config.FocalisConfig;
import io.github.silentcall2598.focalis.core.FocalisLog;
import io.github.silentcall2598.focalis.render.state.GlContextInfo;
import io.github.silentcall2598.focalis.render.target.WorldRenderTarget;
import io.github.silentcall2598.focalis.render.target.WorldTargetMonitor;
import io.github.silentcall2598.focalis.shader.WorldProgramMonitor;
import io.github.silentcall2598.focalis.shader.pack.CustomUniforms;
import io.github.silentcall2598.focalis.shader.pack.PackConfiguration;
import io.github.silentcall2598.focalis.shader.pack.PackIssue;
import io.github.silentcall2598.focalis.shader.pack.PackSettings;
import io.github.silentcall2598.focalis.shader.pack.ProgramSource;
import io.github.silentcall2598.focalis.shader.pack.ProgramStates;
import io.github.silentcall2598.focalis.shader.pack.ShaderMacros;
import io.github.silentcall2598.focalis.shader.pack.ShaderPack;
import io.github.silentcall2598.focalis.shader.pack.ShaderPackException;
import io.github.silentcall2598.focalis.shader.pack.ShaderPackLoader;
import io.github.silentcall2598.focalis.shader.pack.StandardMacros;
import io.github.silentcall2598.focalis.shader.post.PostPassMonitor;
import io.github.silentcall2598.focalis.shader.program.CameraInputs;
import io.github.silentcall2598.focalis.shader.program.EnvironmentInputs;
import io.github.silentcall2598.focalis.shader.program.FogSource;
import io.github.silentcall2598.focalis.shader.program.FrameInputs;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nullable;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Never torn down at shutdown, when the GL context may already be gone. */
public final class ShaderSession {

    public enum State {
        UNLOADED,
        NO_PACK,
        PREPARED,
        CONFIGURED,
        DESTROYED
    }

    static final String CATEGORY = "shaderpack";

    private final ClientView client;
    private final Supplier<GlContextInfo> glContext;
    private final StreamControl streams;
    private final ProgramsFactory programsFactory;
    private final BiFunction<WorldTargetMonitor, Consumer<String>, WorldRenderTarget> targets;
    private final Logger logger;
    private String packName = "";
    private String profileName = "";
    private String optionEntries = "";
    private State state = State.UNLOADED;
    @Nullable
    private ShaderPack pack;
    @Nullable
    private String packProblem;
    @Nullable
    private PackConfiguration configuration;
    private final FrameInputs frame = new FrameInputs();
    private final CameraInputs camera = new CameraInputs();
    private final EnvironmentInputs environment = new EnvironmentInputs();
    private final FogSource fog;
    @Nullable
    private SessionTarget target;
    @Nullable
    private SessionPrograms programs;
    @Nullable
    private SessionPost post;
    private int created;
    private int released;
    private boolean sealed;

    public ShaderSession(Supplier<GlContextInfo> glContext) {
        this(new MinecraftClientView(), glContext, StreamControl.TERRAIN_STREAMS, ProgramsFactory.driver(glContext),
                WorldRenderTarget::new, new MinecraftFog());
    }

    ShaderSession(ClientView client, Supplier<GlContextInfo> glContext, StreamControl streams,
            ProgramsFactory programsFactory,
            BiFunction<WorldTargetMonitor, Consumer<String>, WorldRenderTarget> targets, FogSource fog) {
        this.client = Objects.requireNonNull(client, "client");
        this.glContext = Objects.requireNonNull(glContext, "glContext");
        this.streams = Objects.requireNonNull(streams, "streams");
        this.programsFactory = Objects.requireNonNull(programsFactory, "programsFactory");
        this.targets = Objects.requireNonNull(targets, "targets");
        this.fog = Objects.requireNonNull(fog, "fog");
        this.logger = FocalisLog.forSystem("shader_session");
    }

    public void loadConfig(FocalisConfig config) {
        ConfigSection section = config.section(CATEGORY, "The shaderpack Focalis uses. Shaderpack rendering is"
                + " experimental and only happens while one of the features that use it is turned on.");
        boolean fresh = !section.has("pack");
        String oldPrograms = config.removeFeatureOption("world_programs", "pack");
        String oldPost = config.removeFeatureOption("shaders", "pack");
        String oldProfile = config.removeFeatureOption("world_programs", "profile");
        String oldOptions = config.removeFeatureOption("world_programs", "options");
        String migratedPack = "";
        if (fresh) {
            migratedPack = oldPrograms != null && !oldPrograms.trim().isEmpty() ? oldPrograms
                    : oldPost == null ? "" : oldPost;
            if (oldPrograms != null && oldPost != null && !oldPrograms.trim().isEmpty() && !oldPost.trim().isEmpty()
                    && !oldPrograms.trim().equals(oldPost.trim())) {
                logger.warn("The world programs and the post pass named different shaderpacks, '{}' and '{}'. Focalis"
                        + " now uses one shaderpack for both, '{}'.", oldPrograms, oldPost, oldPrograms);
            }
        }
        packName = section.getString("pack", migratedPack, "Name of a folder or zip in the shaderpacks folder. A"
                + " world<id> folder in its shaders folder replaces the main one in that dimension.");
        profileName = section.getString("profile", fresh && oldProfile != null ? oldProfile : "", "A profile from the"
                + " pack's shaders.properties, like HIGH. Empty keeps the pack's own defaults.");
        optionEntries = section.getString("options", fresh && oldOptions != null ? oldOptions : "", "Option values"
                + " applied after the profile, separated by spaces, like SHADOW_QUALITY=3 BLOOM !SSAO. NAME=value or"
                + " NAME:value sets a value, NAME turns a switch on and !NAME turns it off. Only values the pack"
                + " allows are used.");
        if (fresh && (oldPrograms != null || oldPost != null)) {
            logger.info("Moved the shaderpack settings from the world_programs and shaders features to the shaderpack"
                    + " section of focalis.cfg");
        }
    }

    public State state() {
        return state;
    }

    public String packName() {
        return packName;
    }

    @Nullable
    public ShaderPack pack() {
        return pack;
    }

    public SessionTarget createTarget(WorldTargetMonitor monitor, Logger logger) {
        checkCreatable();
        if (target != null) {
            throw new IllegalStateException("The session already has a world target");
        }
        target = new SessionTarget(client, monitor, logger, targets);
        created++;
        return target;
    }

    @Nullable
    public SessionPrograms createPrograms(WorldProgramMonitor monitor, Logger logger) {
        checkCreatable();
        if (programs != null) {
            throw new IllegalStateException("The session already has world programs");
        }
        if (!loadPack(logger, "the world draws the normal way")) {
            return null;
        }
        logger.info("Binding the world programs of shaderpack '{}', with dimension folders {}", pack.name(),
                pack.dimensionDirectories().keySet());
        programs = new SessionPrograms(this, client, streams, programsFactory, monitor, logger);
        created++;
        return programs;
    }

    @Nullable
    public SessionPost createPost(PostPassMonitor monitor, Logger logger) {
        checkCreatable();
        if (post != null) {
            throw new IllegalStateException("The session already has a post pass");
        }
        if (!loadPack(logger, "rendering stays vanilla")) {
            return null;
        }
        ProgramSource program = pack.root().find(SessionPost.PROGRAM);
        if (program == null) {
            logger.error("Shaderpack '{}' has no shaders/{} program, so rendering stays vanilla. Focalis can only run"
                    + " its own test program so far, not regular shaderpacks.", pack.name(), SessionPost.PROGRAM);
            return null;
        }
        logger.info("Running the experimental post pass from shaderpack '{}'", pack.name());
        post = new SessionPost(this, client, program, monitor, logger);
        created++;
        return post;
    }

    private boolean loadPack(Logger component, String consequence) {
        if (state == State.UNLOADED) {
            if (packName.trim().isEmpty()) {
                packProblem = "No shaderpack is selected. Set shaderpack.pack in focalis.cfg.";
                state = State.NO_PACK;
            } else {
                try {
                    pack = ShaderPackLoader.load(PackSelection.resolve(client.shaderpacks(), packName));
                    state = State.PREPARED;
                } catch (ShaderPackException e) {
                    packProblem = "Shaderpack '" + packName + "' can't be used. " + e.getMessage();
                    state = State.NO_PACK;
                }
            }
        }
        if (state != State.NO_PACK) {
            return true;
        }
        if (pack == null && packName.trim().isEmpty()) {
            component.warn("{} So {}.", packProblem, consequence);
        } else {
            component.error("{} So {}.", packProblem, consequence);
        }
        return false;
    }

    public void seal() {
        sealed = true;
        if (created > 0 && released == created) {
            state = State.DESTROYED;
        }
    }

    @Nullable
    public PackConfiguration configuration() {
        if (configuration != null || state != State.PREPARED) {
            return configuration;
        }
        if (glContext.get() == null) {
            return null;
        }
        ShaderMacros environment = StandardMacros.environment(System.getProperty("os.name", ""), glContext.get(),
                pack.mentions("MC_GL_"));
        PackConfiguration resolved = PackConfiguration.resolve(pack, PackSettings.of(profileName, optionEntries),
                environment);
        configuration = resolved;
        state = State.CONFIGURED;
        logger.info("Shaderpack '{}' gets the standard macros {}", pack.name(), resolved.standardMacros().asMap());
        logConfiguration(resolved);
        SessionPrograms current = programs;
        if (current != null) {
            current.configured(resolved);
        }
        return resolved;
    }

    public String unconfiguredReason() {
        if (state == State.NO_PACK) {
            return packProblem;
        }
        return state == State.DESTROYED ? "The shader session was released." : "OpenGL information isn't available.";
    }

    private void logConfiguration(PackConfiguration resolved) {
        for (PackIssue issue : resolved.issues()) {
            logger.warn("Shaderpack configuration: {}", issue);
        }
        logger.info("Shaderpack '{}' uses profile {} with {} of {} options changed {}, and these programs disabled {}",
                pack.name(), resolved.profile() == null ? "<none>" : resolved.profile(), resolved.changed().size(),
                pack.options().all().size(), resolved.changed(), resolved.disabledPrograms());
        CustomUniforms custom = resolved.customUniforms();
        if (!custom.declarations().isEmpty()) {
            logger.info("Shaderpack '{}' has {} custom uniforms and variables, {} of them usable, with uniforms {}",
                    pack.name(), custom.declarations().size(), custom.evaluatedCount(), custom.uniforms().keySet());
        }
        ProgramStates states = resolved.programStates();
        if (!states.alphaTests().isEmpty() || !states.blends().isEmpty() || !states.bufferBlends().isEmpty()) {
            logger.info("Shaderpack '{}' sets the alpha test of {} and the blend mode of {}, and {} per-buffer blend"
                    + " modes wait for later support", pack.name(), states.alphaTests().keySet(),
                    states.blends().keySet(), states.bufferBlends().size());
        }
    }

    GlContextInfo glContext() {
        GlContextInfo gl = glContext.get();
        if (gl == null) {
            throw new IllegalStateException("OpenGL information isn't available");
        }
        return gl;
    }

    public FrameInputs frame() {
        return frame;
    }

    public CameraInputs camera() {
        return camera;
    }

    public EnvironmentInputs environment() {
        return environment;
    }

    public FogSource fog() {
        return fog;
    }

    void captureFrame() {
        frame.capture(System.nanoTime(), client.framebufferWidth(), client.framebufferHeight());
    }

    void leftWorld() {
        camera.clear();
        environment.clear();
    }

    public boolean targetDrawing() {
        SessionTarget current = target;
        return current != null && current.drawing();
    }

    @Nullable
    public SessionTarget target() {
        return target;
    }

    @Nullable
    public SessionPrograms programs() {
        return programs;
    }

    @Nullable
    public SessionPost post() {
        return post;
    }

    public void releaseTarget() {
        SessionTarget current = target;
        if (current == null) {
            return;
        }
        target = null;
        try {
            current.release();
        } finally {
            componentReleased();
        }
    }

    public void releasePrograms() {
        SessionPrograms current = programs;
        if (current == null) {
            return;
        }
        programs = null;
        try {
            current.release();
        } finally {
            camera.clear();
            environment.clear();
            componentReleased();
        }
    }

    public void releasePost() {
        SessionPost current = post;
        if (current == null) {
            return;
        }
        post = null;
        try {
            current.release();
        } finally {
            componentReleased();
        }
    }

    private void componentReleased() {
        released++;
        if (sealed && released == created) {
            state = State.DESTROYED;
        }
    }

    private void checkCreatable() {
        if (sealed) {
            throw new IllegalStateException("Shader session components can only be created during feature setup");
        }
    }
}
