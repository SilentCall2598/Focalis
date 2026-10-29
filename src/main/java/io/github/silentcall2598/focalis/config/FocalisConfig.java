// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.config;

import net.minecraftforge.common.config.Configuration;

import javax.annotation.Nullable;
import java.io.File;

public final class FocalisConfig {

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

    public static FocalisConfig load(File file) {
        return new FocalisConfig(new Configuration(file, CONFIG_VERSION));
    }

    public boolean logGlExtensions() {
        return logGlExtensions;
    }

    public ConfigSection featureSection(String featureId, String description) {
        configuration.setCategoryComment(featureCategory(featureId), description);
        return new ConfigSection(configuration, featureCategory(featureId));
    }

    public ConfigSection section(String name, String comment) {
        configuration.setCategoryComment(name, comment);
        return new ConfigSection(configuration, name);
    }

    @Nullable
    public String removeFeatureOption(String featureId, String key) {
        String category = featureCategory(featureId);
        if (!configuration.hasKey(category, key)) {
            return null;
        }
        String value = configuration.getCategory(category).get(key).getString();
        configuration.getCategory(category).remove(key);
        return value;
    }

    private static String featureCategory(String featureId) {
        return CATEGORY_FEATURES + Configuration.CATEGORY_SPLITTER + featureId;
    }

    public void saveIfChanged() {
        if (configuration.hasChanged()) {
            configuration.save();
        }
    }
}
