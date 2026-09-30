// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.render.target;

import javax.annotation.Nullable;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Draws a world pass into a Focalis-owned {@link RenderTarget} and copies the result back to Minecraft's framebuffer
 * at its end, so everything after the world pass works on the finished image as usual. Owns the target and replaces
 * it when Minecraft's framebuffer changes size or depth format. Client thread only, with the GL context current.
 */
public final class WorldRenderTarget {

    public enum Start {
        /** The world pass now draws into the target. */
        REDIRECTED,
        /** Something other than Minecraft's framebuffer is being drawn to, so this pass is left alone. */
        OTHER_TARGET,
        /** The target stopped for this session and the world renders the normal way. */
        STOPPED
    }

    private final RenderTargetGl targetGl;
    private final WorldTargetGl gl;
    private final WorldTargetMonitor monitor;
    private final Consumer<String> onStop;
    @Nullable
    private RenderTarget target;
    @Nullable
    private String stopReason;
    private boolean active;
    private int source;
    private int savedRead;
    private int savedDraw;
    // The copy back needs matching depth formats, which is only known for the renderbuffer Minecraft created.
    private int checkedSource;
    private int checkedDepth;

    /** @param onStop called once, with the reason, when the target stops for the session */
    public WorldRenderTarget(WorldTargetMonitor monitor, Consumer<String> onStop) {
        this(LwjglRenderTargetGl.INSTANCE, LwjglWorldTargetGl.INSTANCE, monitor, onStop);
    }

    WorldRenderTarget(RenderTargetGl targetGl, WorldTargetGl gl, WorldTargetMonitor monitor,
            Consumer<String> onStop) {
        this.targetGl = targetGl;
        this.gl = gl;
        this.monitor = Objects.requireNonNull(monitor, "monitor");
        this.onStop = Objects.requireNonNull(onStop, "onStop");
    }

    /**
     * Starts drawing the world pass into the target, creating or replacing it to match Minecraft's framebuffer. Only
     * the read and draw framebuffer bindings change, and {@link #end()} puts them back.
     *
     * @param framebuffer Minecraft's framebuffer, which has to be the one bound for drawing
     * @param depthRenderbuffer the depth renderbuffer Minecraft created for it
     * @throws IllegalStateException if a world pass is already being redirected
     */
    public Start start(int framebuffer, int depthRenderbuffer, int width, int height, DepthFormat depthFormat) {
        Objects.requireNonNull(depthFormat, "depthFormat");
        if (stopReason != null) {
            return Start.STOPPED;
        }
        if (active) {
            throw new IllegalStateException("A world pass started while the previous one still draws into the"
                    + " world target");
        }
        int draw = gl.drawFramebuffer();
        if (draw != framebuffer) {
            return Start.OTHER_TARGET;
        }
        if (framebuffer != checkedSource || depthRenderbuffer != checkedDepth) {
            if (depthRenderbuffer == 0 || gl.drawDepthRenderbuffer() != depthRenderbuffer) {
                stop("Minecraft's depth buffer was replaced by something other than the renderbuffer Minecraft"
                        + " created, so its format is unknown and the world can't be copied back safely");
                return Start.STOPPED;
            }
            checkedSource = framebuffer;
            checkedDepth = depthRenderbuffer;
        }
        RenderTarget current = target;
        if (current == null || !current.matches(width, height, depthFormat)) {
            RenderTarget replacement;
            try {
                replacement = RenderTarget.create(targetGl, width, height, depthFormat);
            } catch (RenderTargetException e) {
                stop(e.getMessage());
                return Start.STOPPED;
            }
            target = replacement;
            // Nothing of the old target is bound outside a redirected pass, so it can go right away.
            if (current != null) {
                current.delete();
            }
            current = replacement;
            monitor.targetCreated(current.framebuffer(), current.colorTexture(), current.depthTexture(), width,
                    height, depthFormat.name());
        }
        savedRead = gl.readFramebuffer();
        savedDraw = draw;
        source = framebuffer;
        // Active before binding, so a failure while binding still puts Minecraft's framebuffer back.
        active = true;
        try {
            gl.bindFramebuffer(current.framebuffer());
            MainFramebufferRedirect.redirect(framebuffer, current.framebuffer());
        } catch (RuntimeException | LinkageError e) {
            unwind(e);
            throw e;
        }
        return Start.REDIRECTED;
    }

    /** Copies color and depth back to Minecraft's framebuffer and puts back the bindings {@link #start} found. */
    public void end() {
        if (!active) {
            throw new IllegalStateException("The world target isn't drawing a world pass");
        }
        RenderTarget current = Objects.requireNonNull(target);
        try {
            MainFramebufferRedirect.clear();
            gl.bindReadFramebuffer(current.framebuffer());
            gl.bindDrawFramebuffer(source);
            monitor.beforeCopy();
            gl.copyColorAndDepth(current.width(), current.height());
            gl.bindReadFramebuffer(savedRead);
            gl.bindDrawFramebuffer(savedDraw);
            active = false;
        } catch (RuntimeException | LinkageError e) {
            unwind(e);
            throw e;
        }
    }

    public boolean isActive() {
        return active;
    }

    @Nullable
    public String stopReason() {
        return stopReason;
    }

    /**
     * Stops redirecting for the rest of the session and frees the target. Only valid between world passes. Calling
     * it again does nothing.
     */
    public void stop(String reason) {
        if (active) {
            throw new IllegalStateException("The world target can't stop in the middle of a world pass");
        }
        if (stopReason != null) {
            return;
        }
        stopReason = Objects.requireNonNull(reason, "reason");
        RenderTarget current = target;
        target = null;
        if (current != null) {
            current.delete();
        }
        onStop.accept(reason);
    }

    /**
     * Frees the target. If a world pass is still being redirected, Minecraft's framebuffer is put back first, so the
     * target is never deleted while bound. Calling it again does nothing.
     */
    public void delete() {
        Throwable failure = active ? unwind(null) : null;
        MainFramebufferRedirect.clear();
        RenderTarget current = target;
        target = null;
        if (current != null) {
            try {
                current.delete();
            } catch (RuntimeException | LinkageError e) {
                failure = collect(failure, e);
            }
        }
        if (failure instanceof RuntimeException) {
            throw (RuntimeException) failure;
        }
        if (failure instanceof LinkageError) {
            throw (LinkageError) failure;
        }
    }

    // Leaves the redirected pass and puts back the bindings start found. Returns the first failure, or the one
    // passed in, with later ones suppressed onto it.
    @Nullable
    private Throwable unwind(@Nullable Throwable failure) {
        active = false;
        MainFramebufferRedirect.clear();
        try {
            gl.bindReadFramebuffer(savedRead);
        } catch (RuntimeException | LinkageError e) {
            failure = collect(failure, e);
        }
        try {
            gl.bindDrawFramebuffer(savedDraw);
        } catch (RuntimeException | LinkageError e) {
            failure = collect(failure, e);
        }
        return failure;
    }

    private static Throwable collect(@Nullable Throwable first, Throwable next) {
        if (first == null) {
            return next;
        }
        if (first != next) {
            first.addSuppressed(next);
        }
        return first;
    }
}
