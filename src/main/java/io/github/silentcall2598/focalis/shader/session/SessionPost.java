// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.shader.session;

import io.github.silentcall2598.focalis.render.state.GlContextInfo;
import io.github.silentcall2598.focalis.shader.pack.IncludeException;
import io.github.silentcall2598.focalis.shader.pack.PackConfiguration;
import io.github.silentcall2598.focalis.shader.pack.ProgramSource;
import io.github.silentcall2598.focalis.shader.post.PostPassException;
import io.github.silentcall2598.focalis.shader.post.PostPassMonitor;
import io.github.silentcall2598.focalis.shader.post.ScenePostPass;
import io.github.silentcall2598.focalis.shader.program.PreparedProgram;
import io.github.silentcall2598.focalis.shader.program.ProgramBuildException;
import io.github.silentcall2598.focalis.shader.program.ProgramBuilder;
import io.github.silentcall2598.focalis.shader.program.ProgramFailure;
import io.github.silentcall2598.focalis.shader.program.ShaderCapabilities;
import io.github.silentcall2598.focalis.shader.program.ShaderProgram;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nullable;

public final class SessionPost {

    public static final String PROGRAM = "focalis_post";

    private final ShaderSession session;
    private final ClientView client;
    private final ProgramSource source;
    private final PostPassMonitor monitor;
    private final Logger logger;
    @Nullable
    private ScenePostPass pass;
    private boolean stopped;
    private boolean released;
    private boolean loggedFramebuffersOff;
    private boolean loggedAnaglyph;
    private boolean loggedOtherTarget;
    private boolean loggedTargetOpen;

    SessionPost(ShaderSession session, ClientView client, ProgramSource source, PostPassMonitor monitor,
            Logger logger) {
        this.session = session;
        this.client = client;
        this.source = source;
        this.monitor = monitor;
        this.logger = logger;
    }

    public void worldEnd() {
        if (stopped || released) {
            return;
        }
        if (!client.framebuffersEnabled()) {
            loggedFramebuffersOff = skip(loggedFramebuffersOff, "framebuffers-off", "Framebuffers are turned off");
            return;
        }
        if (client.anaglyph()) {
            loggedAnaglyph = skip(loggedAnaglyph, "anaglyph", "3D anaglyph is on");
            return;
        }
        if (session.targetDrawing()) {
            loggedTargetOpen = skip(loggedTargetOpen, "target-open", "The world target hasn't copied the world back"
                    + " to Minecraft's framebuffer yet");
            return;
        }
        ScenePostPass current = pass;
        if (current == null) {
            current = createPass();
            if (current == null) {
                return;
            }
            pass = current;
        }
        try {
            if (current.render(client.framebuffer())) {
                monitor.passRendered();
            } else {
                loggedOtherTarget = skip(loggedOtherTarget, "other-target", "Something other than Minecraft's"
                        + " framebuffer is being drawn to");
            }
        } catch (PostPassException e) {
            stop(e.getMessage());
        }
    }

    private boolean skip(boolean alreadyLogged, String key, String reason) {
        monitor.passSkipped(key);
        if (!alreadyLogged) {
            logger.info("{}, so the post pass is skipped while that lasts", reason);
        }
        return true;
    }

    @Nullable
    private ScenePostPass createPass() {
        PackConfiguration configuration = session.configuration();
        if (configuration == null) {
            stop(session.unconfiguredReason());
            return null;
        }
        GlContextInfo gl = session.glContext();
        if (!client.openGl30()) {
            stop("The post pass needs OpenGL 3.0, and this context is " + gl.version() + ".");
            return null;
        }
        PreparedProgram prepared;
        try {
            prepared = PreparedProgram.prepare(configuration.pack(), source, configuration);
        } catch (IncludeException e) {
            stop(e.getMessage());
            return null;
        }
        ShaderProgram program;
        try {
            program = new ProgramBuilder(ShaderCapabilities.from(gl)).build(prepared);
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

    private void stop(String problem) {
        stopped = true;
        monitor.passStopped(problem);
        logger.error("The post pass stopped for this session and rendering stays vanilla. {}", problem);
        deletePass();
    }

    private void deletePass() {
        ScenePostPass current = pass;
        pass = null;
        if (current != null) {
            current.delete();
        }
    }

    void release() {
        released = true;
        deletePass();
    }
}
