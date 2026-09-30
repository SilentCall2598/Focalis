// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.render.target;

import io.github.silentcall2598.focalis.compat.CompatibilityReport;
import io.github.silentcall2598.focalis.feature.Availability;
import io.github.silentcall2598.focalis.feature.Feature;
import io.github.silentcall2598.focalis.feature.FeatureContext;
import io.github.silentcall2598.focalis.render.lifecycle.RenderDrawKind;
import io.github.silentcall2598.focalis.render.lifecycle.RenderPhase;
import io.github.silentcall2598.focalis.render.lifecycle.RenderStage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.shader.Framebuffer;
import org.apache.logging.log4j.Logger;
import org.lwjgl.opengl.GLContext;

import javax.annotation.Nullable;

/**
 * Experimental. Draws each world pass into a Focalis-owned framebuffer and copies it back to Minecraft's at the end,
 * which looks exactly like vanilla. Problems with this setup stop it for the session and leave rendering vanilla.
 * Only a Focalis bug fails the feature.
 */
public final class WorldTargetFeature extends Feature {

    public static final String ID = "world_target";

    private final WorldTargetMonitor monitor;
    private Logger logger;
    @Nullable
    private WorldRenderTarget world;
    private boolean glChecked;
    private boolean loggedFramebuffersOff;
    private boolean loggedOtherTarget;

    /** @param monitor {@link WorldTargetMonitor#NONE} except in development QA runs */
    public WorldTargetFeature(WorldTargetMonitor monitor) {
        super(ID, "Experimental. Draws the world into a Focalis framebuffer and copies it back, which looks the"
                + " same as vanilla. Groundwork for shaderpack rendering.", false);
        this.monitor = monitor;
    }

    @Override
    protected Availability checkAvailability(CompatibilityReport compat) {
        if (compat.isOptiFinePresent()) {
            return Availability.unavailable("OptiFine is installed and changes how the world is rendered");
        }
        return Availability.AVAILABLE;
    }

    @Override
    protected void setup(FeatureContext context) {
        logger = context.logger();
        world = new WorldRenderTarget(monitor, this::stopped);
        context.addRenderListener(RenderStage.WORLD, this::onWorld);
        context.addRenderListener(RenderStage.FRAME, this::onFrame);
    }

    private void onWorld(RenderStage stage, RenderPhase phase, RenderDrawKind drawKind, float partialTicks) {
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
            // The target and the copy back use GL30 framebuffers directly.
            if (!GLContext.getCapabilities().OpenGL30) {
                current.stop("The world target needs OpenGL 3.0");
                return;
            }
        }
        // Without framebuffers the world draws straight to the window.
        if (!OpenGlHelper.isFramebufferEnabled()) {
            loggedFramebuffersOff = skip(loggedFramebuffersOff, "framebuffers-off", "Framebuffers are turned off");
            return;
        }
        if (!MainFramebufferRedirect.hookCalled()) {
            current.stop("The Focalis hook in OpenGlHelper.glBindFramebuffer never ran, so the world would miss the"
                    + " target whenever vanilla rebinds its framebuffer. The Focalis Mixin probably didn't apply.");
            return;
        }
        Framebuffer framebuffer = Minecraft.getMinecraft().getFramebuffer();
        // The copy back only works between matching depth formats, so the target uses the one Minecraft's
        // framebuffer has. That's 24 bit depth, with 8 bits of stencil once Forge's enableStencil was called.
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

    // Each reason tends to hold for many passes in a row, so it's only logged the first time.
    private boolean skip(boolean alreadyLogged, String key, String reason) {
        monitor.skipped(key);
        if (!alreadyLogged) {
            logger.info("{}, so the world target is skipped while that lasts", reason);
        }
        return true;
    }

    // The world pass ends with Forge's RenderWorldLastEvent. If something skipped it, the frame is copied back here
    // so it still shows, and the target stops since its end can't be trusted.
    private void onFrame(RenderStage stage, RenderPhase phase, RenderDrawKind drawKind, float partialTicks) {
        WorldRenderTarget current = world;
        if (phase == RenderPhase.END && current != null && current.isActive()) {
            current.end();
            current.stop("A world pass ended without Forge's RenderWorldLastEvent");
        }
    }

    private void stopped(String reason) {
        monitor.stopped(reason);
        logger.error("The world target stopped for this session and the world renders the normal way. {}", reason);
    }

    // GL objects only exist after a world pass. From then on the feature manager can only call this from a failed
    // render listener or a runtime disable, both on the client thread with the context current. Focalis never
    // cleans up at shutdown, when the context may already be gone.
    @Override
    protected void cleanup() {
        WorldRenderTarget current = world;
        world = null;
        if (current != null) {
            current.delete();
        }
    }
}
