// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.feature;

import io.github.silentcall2598.focalis.compat.CompatibilityReport;
import io.github.silentcall2598.focalis.config.ConfigSection;
import io.github.silentcall2598.focalis.config.FocalisConfig;
import io.github.silentcall2598.focalis.core.FocalisLog;
import io.github.silentcall2598.focalis.render.lifecycle.RenderLifecycle;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// Features are registered during mod construction and initialized once in pre-init.
public final class FeatureManager {

    private final Map<String, Entry> entries = new LinkedHashMap<>();
    private final RenderLifecycle renderLifecycle;
    private boolean initialized;

    public FeatureManager(RenderLifecycle renderLifecycle) {
        this.renderLifecycle = renderLifecycle;
        renderLifecycle.setFailureHandler(this::onRenderFailure);
    }

    public void register(Feature feature) {
        if (initialized) {
            throw new IllegalStateException("Feature '" + feature.id() + "' registered after initialization");
        }
        if (entries.containsKey(feature.id())) {
            throw new IllegalArgumentException("Duplicate feature id '" + feature.id() + "'");
        }
        entries.put(feature.id(), new Entry(feature));
    }

    public void initialize(FocalisConfig config, CompatibilityReport compat) {
        if (initialized) {
            throw new IllegalStateException("Features are already initialized");
        }
        initialized = true;
        for (Entry entry : entries.values()) {
            initialize(entry, config.featureSection(entry.feature.id(), entry.feature.description()), compat);
        }
        FocalisLog.LOGGER.info("Features: {}", statuses());
    }

    private void initialize(Entry entry, ConfigSection section, CompatibilityReport compat) {
        Feature feature = entry.feature;

        // LinkageError is caught along with exceptions because it usually means another mod is missing or has
        // changed, which should only fail this feature. VM errors like OutOfMemoryError still propagate.
        boolean enabled;
        try {
            enabled = section.getBoolean("enabled", feature.isEnabledByDefault(), "Turns this feature on or off.");
            feature.loadConfig(section);
        } catch (Exception | LinkageError e) {
            fail(entry, "loading its configuration", e);
            return;
        }
        if (!enabled) {
            entry.status = new FeatureStatus(feature.id(), FeatureState.DISABLED, null);
            return;
        }

        Availability availability;
        try {
            availability = feature.checkAvailability(compat);
        } catch (Exception | LinkageError e) {
            fail(entry, "its availability check", e);
            return;
        }
        if (!availability.isAvailable()) {
            entry.status = new FeatureStatus(feature.id(), FeatureState.UNAVAILABLE, availability.reason());
            FocalisLog.LOGGER.info("Feature '{}' is enabled but unavailable: {}", feature.id(), availability.reason());
            return;
        }

        entry.context = new FeatureContext(feature.id(), renderLifecycle, FocalisLog.forFeature(feature.id()));
        try {
            feature.setup(entry.context);
        } catch (Exception | LinkageError e) {
            fail(entry, "setup", e);
            return;
        }
        entry.status = new FeatureStatus(feature.id(), FeatureState.ACTIVE, null);
    }

    /** Turns an active feature off for the rest of the session. Call it on the client thread. */
    public void disable(String featureId) {
        Entry entry = entries.get(featureId);
        if (entry == null) {
            throw new IllegalArgumentException("Unknown feature '" + featureId + "'");
        }
        if (entry.status.state() != FeatureState.ACTIVE) {
            return;
        }
        Throwable cleanupError = deactivate(entry);
        if (cleanupError == null) {
            entry.status = new FeatureStatus(featureId, FeatureState.DISABLED, "turned off at runtime");
            FocalisLog.LOGGER.info("Feature '{}' was turned off", featureId);
        } else {
            entry.status = new FeatureStatus(featureId, FeatureState.FAILED, "failed during cleanup: " + cleanupError);
        }
    }

    private void fail(Entry entry, String during, Throwable error) {
        String featureId = entry.feature.id();
        entry.status = new FeatureStatus(featureId, FeatureState.FAILED, "failed during " + during + ": " + error);
        FocalisLog.LOGGER.error("Feature '{}' failed during {} and is disabled for this session."
                + " Other features are unaffected.", featureId, during, error);
        deactivate(entry);
    }

    private void onRenderFailure(String owner, String during, Throwable error) {
        // Internal owners like focalis:render aren't features, so there's nothing to update for them.
        Entry entry = entries.get(owner);
        if (entry == null || entry.status.state() != FeatureState.ACTIVE) {
            return;
        }
        // The lifecycle has already removed the listeners and logged the stack trace.
        entry.status = new FeatureStatus(owner, FeatureState.FAILED, "failed during " + during + ": " + error);
        FocalisLog.LOGGER.error("Feature '{}' is disabled for this session. Other features are unaffected.", owner);
        deactivate(entry);
    }

    // Detaches what the manager tracks, then lets the feature release the rest. Returns the cleanup error or null.
    @Nullable
    private Throwable deactivate(Entry entry) {
        // Skip features that never reached setup, and never clean up twice.
        if (entry.context == null || entry.deactivated) {
            return null;
        }
        entry.deactivated = true;
        entry.context.close();
        renderLifecycle.removeOwner(entry.feature.id());
        try {
            entry.feature.cleanup();
            return null;
        } catch (Exception | LinkageError e) {
            FocalisLog.LOGGER.error("Feature '{}' failed during cleanup, some of its resources may not have been"
                    + " released", entry.feature.id(), e);
            return e;
        }
    }

    @Nullable
    public FeatureStatus status(String featureId) {
        Entry entry = entries.get(featureId);
        return entry == null ? null : entry.status;
    }

    // Safe from any thread once registration is done, which the crash report relies on.
    public List<FeatureStatus> statuses() {
        List<FeatureStatus> statuses = new ArrayList<>(entries.size());
        for (Entry entry : entries.values()) {
            statuses.add(entry.status);
        }
        return statuses;
    }

    private static final class Entry {

        final Feature feature;
        volatile FeatureStatus status;
        @Nullable
        FeatureContext context;
        boolean deactivated;

        Entry(Feature feature) {
            this.feature = feature;
            this.status = new FeatureStatus(feature.id(), FeatureState.PENDING, null);
        }
    }
}
