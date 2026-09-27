// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.render.lifecycle;

import io.github.silentcall2598.focalis.core.FocalisLog;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

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

    private static final Registration[] NONE = new Registration[0];

    // One array per stage, indexed by ordinal. Changes replace the arrays, so a dispatch walks its own snapshot
    // without allocating.
    private volatile Registration[][] listeners;
    private final Set<RenderStage> dispatchedStages = EnumSet.noneOf(RenderStage.class);
    private volatile FailureHandler failureHandler = (owner, stage, phase, error) -> {
    };

    public RenderLifecycle() {
        Registration[][] empty = new Registration[RenderStage.values().length][];
        Arrays.fill(empty, NONE);
        listeners = empty;
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

    public synchronized void register(RenderStage stage, String owner, RenderStageListener listener) {
        Objects.requireNonNull(stage, "stage");
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(listener, "listener");
        if (!isDispatched(stage)) {
            FocalisLog.LOGGER.warn("'{}' registered a listener for render stage {}, which no hook dispatches yet."
                    + " It will not be called.", owner, stage);
        }
        Registration[][] updated = listeners.clone();
        Registration[] current = updated[stage.ordinal()];
        Registration[] grown = Arrays.copyOf(current, current.length + 1);
        grown[current.length] = new Registration(owner, listener);
        updated[stage.ordinal()] = grown;
        listeners = updated;
    }

    public synchronized void removeOwner(String owner) {
        Registration[][] updated = listeners.clone();
        for (int i = 0; i < updated.length; i++) {
            List<Registration> kept = new ArrayList<>();
            for (Registration registration : updated[i]) {
                if (registration.owner.equals(owner)) {
                    registration.active = false;
                } else {
                    kept.add(registration);
                }
            }
            updated[i] = kept.toArray(NONE);
        }
        listeners = updated;
    }

    public void dispatch(RenderStage stage, RenderPhase phase, float partialTicks) {
        for (Registration registration : listeners[stage.ordinal()]) {
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
