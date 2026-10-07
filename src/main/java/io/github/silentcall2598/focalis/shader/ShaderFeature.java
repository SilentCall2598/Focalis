// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.shader;

import io.github.silentcall2598.focalis.compat.CompatibilityReport;
import io.github.silentcall2598.focalis.config.ConfigSection;
import io.github.silentcall2598.focalis.feature.Availability;
import io.github.silentcall2598.focalis.feature.Feature;
import io.github.silentcall2598.focalis.feature.FeatureContext;
import io.github.silentcall2598.focalis.render.lifecycle.RenderPhase;
import io.github.silentcall2598.focalis.render.lifecycle.RenderStage;
import io.github.silentcall2598.focalis.render.state.GlContextInfo;
import io.github.silentcall2598.focalis.shader.pack.ShaderPackException;
import io.github.silentcall2598.focalis.shader.post.PostPassException;
import io.github.silentcall2598.focalis.shader.post.PostPassMonitor;
import io.github.silentcall2598.focalis.shader.post.ScenePostPass;
import io.github.silentcall2598.focalis.shader.program.PreparedProgram;
import io.github.silentcall2598.focalis.shader.program.ProgramBuildException;
import io.github.silentcall2598.focalis.shader.program.ProgramBuilder;
import io.github.silentcall2598.focalis.shader.program.ProgramFailure;
import io.github.silentcall2598.focalis.shader.program.ShaderCapabilities;
import io.github.silentcall2598.focalis.shader.program.ShaderProgram;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.OpenGlHelper;
import org.apache.logging.log4j.Logger;
import org.lwjgl.opengl.GLContext;

import javax.annotation.Nullable;
import java.nio.file.Path;
import java.util.function.Supplier;

/**
 * Experimental. Runs one Focalis test program from the selected pack over the world image at the end of the world
 * pass. Problems with the pack, its program or this setup are logged and leave rendering vanilla. Only a Focalis
 * bug fails the feature.
 */
public final class ShaderFeature extends Feature {

    public static final String ID = "shaders";

    // Regular packs don't have a program by this name, so they can't be run by accident while nothing else works.
    static final String PROGRAM = "focalis_post";

    private final Supplier<GlContextInfo> glContext;
    private final PostPassMonitor monitor;
    private String packName = "";
    private Logger logger;
    @Nullable
    private ScenePostPass pass;
    private boolean stopped;
    private boolean loggedFramebuffersOff;
    private boolean loggedAnaglyph;
    private boolean loggedOtherTarget;

    /**
     * @param glContext returns null until the first frame has captured the context
     * @param monitor {@link PostPassMonitor#NONE} unless a development probe is attached
     */
    public ShaderFeature(Supplier<GlContextInfo> glContext, PostPassMonitor monitor) {
        super(ID, "Experimental. Runs a Focalis test program from a shaderpack over the world image."
                + " Regular shaderpacks are not supported yet.", false);
        this.glContext = glContext;
        this.monitor = monitor;
    }

    @Override
    protected void loadConfig(ConfigSection config) {
        packName = config.getString("pack", "", "Name of a folder or zip in the shaderpacks folder. So far it has"
                + " to contain the Focalis test program shaders/" + PROGRAM + ".vsh and .fsh.");
    }

    @Override
    protected Availability checkAvailability(CompatibilityReport compat) {
        if (compat.isOptiFinePresent()) {
            return Availability.unavailable("OptiFine is installed and has its own shader support");
        }
        return Availability.AVAILABLE;
    }

    // Loading and preparing the pack is plain file work, so it happens here. Building the program has to wait for
    // the first world frame, when the GL context is known to be current.
    @Override
    protected void setup(FeatureContext context) {
        logger = context.logger();
        if (packName.trim().isEmpty()) {
            logger.warn("No shaderpack is selected, so rendering stays vanilla. Set features.shaders.pack in"
                    + " focalis.cfg.");
            return;
        }
        Path shaderpacks = Minecraft.getMinecraft().gameDir.toPath().resolve("shaderpacks");
        PreparedProgram program;
        try {
            program = PackSelection.prepare(PackSelection.resolve(shaderpacks, packName), PROGRAM);
        } catch (ShaderPackException e) {
            logger.error("Shaderpack '{}' can't be used, so rendering stays vanilla. {}", packName, e.getMessage());
            return;
        }
        logger.info("Running the experimental post pass from shaderpack '{}'", packName);
        context.addRenderListener(RenderStage.WORLD, (stage, phase, drawKind, partialTicks) -> {
            // The pass needs the finished world image, so it only runs at the end of the world pass.
            if (phase == RenderPhase.END) {
                onWorldEnd(program);
            }
        });
    }

    private void onWorldEnd(PreparedProgram source) {
        if (stopped) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        // Without framebuffers the world draws straight to the window and there's no depth buffer to copy.
        if (!OpenGlHelper.isFramebufferEnabled()) {
            loggedFramebuffersOff = skip(loggedFramebuffersOff, "framebuffers-off", "Framebuffers are turned off");
            return;
        }
        // Anaglyph draws the world twice through color masks, which the pass doesn't handle.
        if (mc.gameSettings.anaglyph) {
            loggedAnaglyph = skip(loggedAnaglyph, "anaglyph", "3D anaglyph is on");
            return;
        }
        ScenePostPass current = pass;
        if (current == null) {
            current = createPass(source);
            if (current == null) {
                return;
            }
            pass = current;
        }
        try {
            if (current.render(mc.getFramebuffer())) {
                monitor.passRendered();
            } else {
                loggedOtherTarget = skip(loggedOtherTarget, "other-target", "Something other than Minecraft's"
                        + " framebuffer is being drawn to");
            }
        } catch (PostPassException e) {
            stop(e.getMessage());
        }
    }

    // Each reason tends to hold for many frames in a row, so it's only logged the first time.
    private boolean skip(boolean alreadyLogged, String key, String reason) {
        monitor.passSkipped(key);
        if (!alreadyLogged) {
            logger.info("{}, so the post pass is skipped while that lasts", reason);
        }
        return true;
    }

    @Nullable
    private ScenePostPass createPass(PreparedProgram source) {
        GlContextInfo gl = glContext.get();
        if (gl == null) {
            stop("OpenGL information isn't available.");
            return null;
        }
        // The copy needs separate read and draw framebuffers and a blit, and the pass calls them through GL30.
        if (!GLContext.getCapabilities().OpenGL30) {
            stop("The post pass needs OpenGL 3.0, and this context is " + gl.version() + ".");
            return null;
        }
        ShaderProgram program;
        try {
            program = new ProgramBuilder(ShaderCapabilities.from(gl)).build(source);
        } catch (ProgramBuildException e) {
            ProgramFailure failure = e.failure();
            stop(failure.driverLog().isEmpty() ? failure.toString()
                    : failure + "\nFull driver log:\n" + failure.driverLog());
            return null;
        }
        if (!program.driverLog().isEmpty()) {
            logger.warn("{} was built, and the driver reported:\n{}", program.name(), program.driverLog());
        }
        monitor.programBuilt(program.id());
        ScenePostPass created = null;
        try {
            created = ScenePostPass.create(program, logger, monitor);
            return created;
        } catch (PostPassException e) {
            stop(e.getMessage());
            return null;
        } finally {
            if (created == null) {
                program.delete();
            }
        }
    }

    // Pack, program and setup problems all end up here. None of them is a Focalis bug, so the feature stays active
    // and only the pass stops.
    private void stop(String problem) {
        stopped = true;
        monitor.passStopped(problem);
        logger.error("The post pass stopped for this session and rendering stays vanilla. {}", problem);
        releaseGl();
    }

    private void releaseGl() {
        ScenePostPass current = pass;
        pass = null;
        if (current != null) {
            current.delete();
        }
    }

    // GL objects only exist after a world frame. From then on the feature manager can only call this from a failed
    // render listener or a runtime disable, both on the client thread with the context current. Focalis never
    // cleans up at shutdown, when the context may already be gone.
    @Override
    protected void cleanup() {
        releaseGl();
    }
}
