// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.feature;

import io.github.silentcall2598.focalis.compat.CompatibilityReport;
import io.github.silentcall2598.focalis.config.ConfigSection;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * A major Focalis system that can be turned on or off by itself. If any of its steps or render listeners throw,
 * only this feature fails and the rest of Focalis keeps running.
 */
public abstract class Feature {

    private static final Pattern ID_PATTERN = Pattern.compile("[a-z][a-z0-9_]*");

    private final String id;
    private final String description;
    private final boolean enabledByDefault;

    protected Feature(String id, String description, boolean enabledByDefault) {
        if (!ID_PATTERN.matcher(id).matches()) {
            throw new IllegalArgumentException("Invalid feature id '" + id
                    + "': use lowercase letters, digits and underscores, starting with a letter");
        }
        this.id = id;
        this.description = Objects.requireNonNull(description, "description");
        this.enabledByDefault = enabledByDefault;
    }

    public final String id() {
        return id;
    }

    public final String description() {
        return description;
    }

    public final boolean isEnabledByDefault() {
        return enabledByDefault;
    }

    /** Called for every feature, enabled or not, so all of its options show up in the config file. */
    protected void loadConfig(ConfigSection config) {
    }

    /** Return unavailable when this feature can't safely run next to something that's installed. */
    protected Availability checkAvailability(CompatibilityReport compat) {
        return Availability.AVAILABLE;
    }

    /**
     * Runs once on the client thread during pre-init. Register listeners through the context and never directly
     * on the Forge event bus, because handlers added there keep running after a failure and never get cleaned up.
     */
    protected abstract void setup(FeatureContext context) throws Exception;

    /**
     * Releases what setup created when the feature fails or is turned off, after its context listeners are
     * already detached. It can run after a partial setup and is only ever called once.
     */
    protected void cleanup() throws Exception {
    }
}
