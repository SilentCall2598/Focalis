// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.render;

import io.github.silentcall2598.focalis.core.FocalisLog;
import io.github.silentcall2598.focalis.render.lifecycle.ForgeRenderEventBridge;
import io.github.silentcall2598.focalis.render.lifecycle.RenderHooks;
import io.github.silentcall2598.focalis.render.lifecycle.RenderLifecycle;
import io.github.silentcall2598.focalis.render.lifecycle.RenderPhase;
import io.github.silentcall2598.focalis.render.lifecycle.RenderStage;
import io.github.silentcall2598.focalis.render.state.GlContextInfo;

import javax.annotation.Nullable;
import java.util.function.Consumer;

public final class RenderSubsystem {

    private static final String LISTENER_OWNER = "focalis:render";

    private final RenderLifecycle lifecycle = new RenderLifecycle();
    private final Consumer<GlContextInfo> contextReadyCallback;
    @Nullable
    private volatile GlContextInfo glContext;
    private boolean contextCaptureAttempted;

    // The callback runs once on the client thread after the GL context is captured.
    public RenderSubsystem(Consumer<GlContextInfo> contextReadyCallback) {
        this.contextReadyCallback = contextReadyCallback;
    }

    public RenderLifecycle lifecycle() {
        return lifecycle;
    }

    // Call during pre-init, before features register render listeners.
    public void install() {
        ForgeRenderEventBridge.install(lifecycle);
        RenderHooks.install(lifecycle);
        lifecycle.register(RenderStage.FRAME, LISTENER_OWNER, this::onFrame);
    }

    // Null until the first frame, or if capturing failed.
    @Nullable
    public GlContextInfo glContext() {
        return glContext;
    }

    private void onFrame(RenderStage stage, RenderPhase phase, float partialTicks) {
        if (phase != RenderPhase.START || contextCaptureAttempted) {
            return;
        }
        contextCaptureAttempted = true;

        // During mod loading Forge's splash screen thread holds the display context and the client thread only
        // has a shared one. The first frame is the earliest point where the client thread reliably owns it.
        GlContextInfo captured;
        try {
            captured = GlContextInfo.capture();
        } catch (RuntimeException e) {
            FocalisLog.LOGGER.warn("Could not query the OpenGL context. OpenGL information is unavailable.", e);
            return;
        }
        glContext = captured;
        contextReadyCallback.accept(captured);
    }
}
