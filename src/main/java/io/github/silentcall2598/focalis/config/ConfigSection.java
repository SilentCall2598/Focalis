// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.config;

import net.minecraftforge.common.config.Configuration;

// Reading a missing option adds it with its default value. Forge's Configuration isn't thread-safe, so only read
// sections during startup on the client thread.
public final class ConfigSection {

    private final Configuration configuration;
    private final String category;

    ConfigSection(Configuration configuration, String category) {
        this.configuration = configuration;
        this.category = category;
    }

    public boolean getBoolean(String key, boolean defaultValue, String comment) {
        return configuration.getBoolean(key, category, defaultValue, comment);
    }

    // Forge clamps values outside min and max.
    public int getInt(String key, int defaultValue, int min, int max, String comment) {
        return configuration.getInt(key, category, defaultValue, min, max, comment);
    }
}
