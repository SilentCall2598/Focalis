// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.shader.session;

import io.github.silentcall2598.focalis.render.lifecycle.RenderPhase;
import io.github.silentcall2598.focalis.render.target.DepthFormat;
import io.github.silentcall2598.focalis.render.target.WorldRenderTarget;
import io.github.silentcall2598.focalis.render.target.WorldTargetMonitor;
import net.minecraft.client.shader.Framebuffer;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nullable;
import java.util.function.BiFunction;
import java.util.function.Consumer;

public final class SessionTarget {

    private final ClientView client;
    private final WorldTargetMonitor monitor;
    private final Logger logger;
    @Nullable
    private WorldRenderTarget world;
    private boolean glChecked;
    private boolean loggedFramebuffersOff;
    private boolean loggedOtherTarget;

    SessionTarget(ClientView client, WorldTargetMonitor monitor, Logger logger,
            BiFunction<WorldTargetMonitor, Consumer<String>, WorldRenderTarget> targets) {
        this.client = client;
        this.monitor = monitor;
        this.logger = logger;
        world = targets.apply(monitor, this::stopped);
    }

    public boolean drawing() {
        WorldRenderTarget current = world;
        return current != null && current.isActive();
    }

    public void world(RenderPhase phase) {
        WorldRenderTarget current = world;
        if (current == null) {
            return;
        }
        if (phase == RenderPhase.START) {
            start(current);
        } else if (current.isActive()) {
            current.end();
        }
    }

    private void start(WorldRenderTarget current) {
        if (current.stopReason() != null) {
            return;
        }
        if (!glChecked) {
            glChecked = true;
            if (!client.openGl30()) {
                current.stop("The world target needs OpenGL 3.0");
                return;
            }
        }
        if (!client.framebuffersEnabled()) {
            loggedFramebuffersOff = skip(loggedFramebuffersOff, "framebuffers-off", "Framebuffers are turned off");
            return;
        }
        if (!client.framebufferHookRan()) {
            current.stop("The Focalis hook in OpenGlHelper.glBindFramebuffer never ran, so the world would miss the"
                    + " target whenever vanilla rebinds its framebuffer. The Focalis Mixin probably didn't apply.");
            return;
        }
        Framebuffer framebuffer = client.framebuffer();
        DepthFormat depthFormat = DepthFormat.withStencil(framebuffer.isStencilEnabled());
        int depthRenderbuffer = framebuffer.useDepth ? framebuffer.depthBuffer : 0;
        switch (current.start(framebuffer.framebufferObject, depthRenderbuffer, framebuffer.framebufferWidth,
                framebuffer.framebufferHeight, depthFormat)) {
            case REDIRECTED:
                monitor.redirected();
                break;
            case OTHER_TARGET:
                loggedOtherTarget = skip(loggedOtherTarget, "other-target", "Something other than Minecraft's"
                        + " framebuffer is being drawn to");
                break;
            default:
                break;
        }
    }

    private boolean skip(boolean alreadyLogged, String key, String reason) {
        monitor.skipped(key);
        if (!alreadyLogged) {
            logger.info("{}, so the world target is skipped while that lasts", reason);
        }
        return true;
    }

    public void frameEnd() {
        WorldRenderTarget current = world;
        if (current != null && current.isActive()) {
            current.end();
            current.stop("A world pass ended without Forge's RenderWorldLastEvent");
        }
    }

    private void stopped(String reason) {
        monitor.stopped(reason);
        logger.error("The world target stopped for this session and the world renders the normal way. {}", reason);
    }

    void release() {
        WorldRenderTarget current = world;
        world = null;
        if (current != null) {
            current.delete();
        }
    }
}
