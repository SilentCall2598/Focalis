// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.render.lifecycle;

import io.github.silentcall2598.focalis.core.FocalisLog;
import net.minecraft.entity.Entity;
import net.minecraft.tileentity.TileEntity;

import javax.annotation.Nullable;
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

        /** @param during where it failed, like "render stage SKY START (SKY_TEXTURED)" */
        void onListenerFailure(String owner, String during, Throwable error);
    }

    private static final Registration[] NONE = new Registration[0];
    private static final CheckpointRegistration[] NO_CHECKPOINTS = new CheckpointRegistration[0];
    private static final ObjectRegistration[] NO_OBJECTS = new ObjectRegistration[0];

    // One array per stage, indexed by ordinal. Changes replace the arrays, so a dispatch walks its own snapshot
    // without allocating.
    private volatile Registration[][] listeners;
    private volatile CheckpointRegistration[] checkpointListeners = NO_CHECKPOINTS;
    private volatile ObjectRegistration[] objectListeners = NO_OBJECTS;
    private final Set<RenderStage> dispatchedStages = EnumSet.noneOf(RenderStage.class);
    private volatile FailureHandler failureHandler = (owner, during, error) -> {
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

    /** Checkpoints aren't stages, so every listener gets every checkpoint in registration order. */
    public synchronized void registerCheckpoint(String owner, RenderCheckpointListener listener) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(listener, "listener");
        CheckpointRegistration[] current = checkpointListeners;
        CheckpointRegistration[] grown = Arrays.copyOf(current, current.length + 1);
        grown[current.length] = new CheckpointRegistration(owner, listener);
        checkpointListeners = grown;
    }

    /** Every object listener gets every object in registration order. */
    public synchronized void registerObject(String owner, RenderObjectListener listener) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(listener, "listener");
        ObjectRegistration[] current = objectListeners;
        ObjectRegistration[] grown = Arrays.copyOf(current, current.length + 1);
        grown[current.length] = new ObjectRegistration(owner, listener);
        objectListeners = grown;
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
        List<CheckpointRegistration> keptCheckpoints = new ArrayList<>();
        for (CheckpointRegistration registration : checkpointListeners) {
            if (registration.owner.equals(owner)) {
                registration.active = false;
            } else {
                keptCheckpoints.add(registration);
            }
        }
        checkpointListeners = keptCheckpoints.toArray(NO_CHECKPOINTS);
        List<ObjectRegistration> keptObjects = new ArrayList<>();
        for (ObjectRegistration registration : objectListeners) {
            if (registration.owner.equals(owner)) {
                registration.active = false;
            } else {
                keptObjects.add(registration);
            }
        }
        objectListeners = keptObjects.toArray(NO_OBJECTS);
    }

    // Same failure handling as a checkpoint. The switch keeps one loop for every kind of object event. kind is the
    // special draw's, or for a block entity start DAMAGED_BLOCK_ENTITY when it draws its breaking overlay.
    void dispatchObject(int event, @Nullable Entity entity, @Nullable TileEntity blockEntity, RenderDrawKind kind,
            float red, float green, float blue, float alpha) {
        for (ObjectRegistration registration : objectListeners) {
            if (!registration.active) {
                continue;
            }
            try {
                switch (event) {
                    case ENTITY_STARTED:
                        registration.listener.entityStarted(entity);
                        break;
                    case BLOCK_ENTITY_STARTED:
                        registration.listener.blockEntityStarted(blockEntity,
                                kind == RenderDrawKind.DAMAGED_BLOCK_ENTITY);
                        break;
                    case OBJECT_ENDED:
                        registration.listener.objectEnded();
                        break;
                    case SPECIAL_STARTED:
                        registration.listener.specialDrawStarted(kind);
                        break;
                    case SPECIAL_ENDED:
                        registration.listener.specialDrawEnded(kind);
                        break;
                    case SPECIALS_ABANDONED:
                        registration.listener.specialDrawsAbandoned();
                        break;
                    default:
                        registration.listener.objectColor(red, green, blue, alpha);
                        break;
                }
            } catch (Exception | LinkageError e) { // VM errors such as OutOfMemoryError deliberately propagate
                removeOwner(registration.owner);
                String during = event == OBJECT_ENDED ? "an entity or block entity end"
                        : event == OBJECT_COLOR ? "an entity color"
                        : event >= SPECIAL_STARTED ? "a special draw" : "an entity or block entity start";
                FocalisLog.LOGGER.error("Render listener of '{}' failed at {}. All of its render listeners have been"
                        + " removed.", registration.owner, during, e);
                failureHandler.onListenerFailure(registration.owner, during, e);
            }
        }
    }

    static final int ENTITY_STARTED = 0;
    static final int BLOCK_ENTITY_STARTED = 1;
    static final int OBJECT_ENDED = 2;
    static final int OBJECT_COLOR = 3;
    static final int SPECIAL_STARTED = 4;
    static final int SPECIAL_ENDED = 5;
    static final int SPECIALS_ABANDONED = 6;

    private static final class ObjectRegistration {

        final String owner;
        final RenderObjectListener listener;
        volatile boolean active = true;

        ObjectRegistration(String owner, RenderObjectListener listener) {
            this.owner = owner;
            this.listener = listener;
        }
    }

    public void dispatch(RenderStage stage, RenderPhase phase, float partialTicks) {
        dispatch(stage, phase, RenderDrawKind.DEFAULT, partialTicks);
    }

    public void dispatch(RenderStage stage, RenderPhase phase, RenderDrawKind drawKind, float partialTicks) {
        for (Registration registration : listeners[stage.ordinal()]) {
            // The iteration snapshot may still hold listeners removed earlier in this dispatch.
            if (!registration.active) {
                continue;
            }
            try {
                registration.listener.onRenderStage(stage, phase, drawKind, partialTicks);
            } catch (Exception | LinkageError e) { // VM errors such as OutOfMemoryError deliberately propagate
                // Any GL state the listener left behind stays as it is.
                removeOwner(registration.owner);
                FocalisLog.LOGGER.error("Render listener of '{}' failed during {} {} ({}). All of its render"
                        + " listeners have been removed.", registration.owner, stage, phase, drawKind, e);
                failureHandler.onListenerFailure(registration.owner,
                        "render stage " + stage + " " + phase + " (" + drawKind + ")", e);
            }
        }
    }

    // Same failure handling as a stage, without anything that allocates on the way through.
    public void dispatchCheckpoint(RenderCheckpoint checkpoint) {
        for (CheckpointRegistration registration : checkpointListeners) {
            if (!registration.active) {
                continue;
            }
            try {
                registration.listener.onCheckpoint(checkpoint);
            } catch (Exception | LinkageError e) { // VM errors such as OutOfMemoryError deliberately propagate
                removeOwner(registration.owner);
                FocalisLog.LOGGER.error("Render listener of '{}' failed at renderer checkpoint {}. All of its render"
                        + " listeners have been removed.", registration.owner, checkpoint, e);
                failureHandler.onListenerFailure(registration.owner, "renderer checkpoint " + checkpoint, e);
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

    private static final class CheckpointRegistration {

        final String owner;
        final RenderCheckpointListener listener;
        volatile boolean active = true;

        CheckpointRegistration(String owner, RenderCheckpointListener listener) {
            this.owner = owner;
            this.listener = listener;
        }
    }
}
