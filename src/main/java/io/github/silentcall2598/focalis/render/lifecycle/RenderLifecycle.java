// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.render.lifecycle;

import io.github.silentcall2598.focalis.core.FocalisLog;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Every listener belongs to an owner, which is a feature id or an internal name containing a colon. If a listener
 * throws, all listeners of that owner are removed so a broken system stops instead of failing every frame.
 */
public final class RenderLifecycle {

    /** Notified after a listener failed and its owner's listeners were removed. */
    @FunctionalInterface
    public interface FailureHandler {

        void onListenerFailure(String owner, RenderStage stage, RenderPhase phase, Throwable error);
    }

    private final Map<RenderStage, List<Registration>> listeners = new EnumMap<>(RenderStage.class);
    private final Set<RenderStage> dispatchedStages = EnumSet.noneOf(RenderStage.class);
    private volatile FailureHandler failureHandler = (owner, stage, phase, error) -> {
    };

    public RenderLifecycle() {
        for (RenderStage stage : RenderStage.values()) {
            listeners.put(stage, new CopyOnWriteArrayList<>());
        }
    }

    public void setFailureHandler(FailureHandler failureHandler) {
        this.failureHandler = Objects.requireNonNull(failureHandler, "failureHandler");
    }

    /** Called by hooks to declare which stages they actually deliver. */
    public void markDispatched(RenderStage stage) {
        dispatchedStages.add(stage);
    }

    public boolean isDispatched(RenderStage stage) {
        return dispatchedStages.contains(stage);
    }

    public void register(RenderStage stage, String owner, RenderStageListener listener) {
        Objects.requireNonNull(stage, "stage");
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(listener, "listener");
        if (!isDispatched(stage)) {
            FocalisLog.LOGGER.warn("'{}' registered a listener for render stage {}, which no hook dispatches yet."
                    + " It will not be called.", owner, stage);
        }
        listeners.get(stage).add(new Registration(owner, listener));
    }

    public void removeOwner(String owner) {
        for (List<Registration> stageListeners : listeners.values()) {
            stageListeners.removeIf(registration -> {
                if (registration.owner.equals(owner)) {
                    registration.active = false;
                    return true;
                }
                return false;
            });
        }
    }

    public void dispatch(RenderStage stage, RenderPhase phase, float partialTicks) {
        for (Registration registration : listeners.get(stage)) {
            // The iteration snapshot may still hold listeners removed earlier in this dispatch.
            if (!registration.active) {
                continue;
            }
            try {
                registration.listener.onRenderStage(stage, phase, partialTicks);
            } catch (Exception | LinkageError e) { // VM errors such as OutOfMemoryError deliberately propagate
                // Any GL state the listener left behind stays as it is.
                removeOwner(registration.owner);
                FocalisLog.LOGGER.error("Render listener of '{}' failed during {} {}. All of its render listeners"
                        + " have been removed.", registration.owner, stage, phase, e);
                failureHandler.onListenerFailure(registration.owner, stage, phase, e);
            }
        }
    }

    private static final class Registration {

        final String owner;
        final RenderStageListener listener;
        volatile boolean active = true;

        Registration(String owner, RenderStageListener listener) {
            this.owner = owner;
            this.listener = listener;
        }
    }
}
