// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.config;

import net.minecraftforge.common.config.Configuration;

import java.io.File;

// Options are only read at startup, so changes to focalis.cfg need a restart.
public final class FocalisConfig {

    // Stored in the file so later versions can migrate renamed options.
    static final String CONFIG_VERSION = "1";

    private static final String CATEGORY_DIAGNOSTICS = "diagnostics";
    private static final String CATEGORY_FEATURES = "features";

    private final Configuration configuration;
    private final boolean logGlExtensions;

    public FocalisConfig(Configuration configuration) {
        this.configuration = configuration;

        configuration.setCategoryComment(CATEGORY_DIAGNOSTICS, "Diagnostic output. These options never change rendering.");
        logGlExtensions = configuration.getBoolean("logGlExtensions", CATEGORY_DIAGNOSTICS, false,
                "Log every OpenGL extension the graphics driver reports, in addition to the standard OpenGL summary.");

        configuration.setCategoryComment(CATEGORY_FEATURES,
                "Independently toggleable Focalis features. Changes take effect after a restart.");
    }

    // Forge renames an unreadable file to *.errored and starts over with defaults instead of failing.
    public static FocalisConfig load(File file) {
        return new FocalisConfig(new Configuration(file, CONFIG_VERSION));
    }

    public boolean logGlExtensions() {
        return logGlExtensions;
    }

    public ConfigSection featureSection(String featureId, String description) {
        String category = CATEGORY_FEATURES + Configuration.CATEGORY_SPLITTER + featureId;
        configuration.setCategoryComment(category, description);
        return new ConfigSection(configuration, category);
    }

    public void saveIfChanged() {
        if (configuration.hasChanged()) {
            configuration.save();
        }
    }
}
