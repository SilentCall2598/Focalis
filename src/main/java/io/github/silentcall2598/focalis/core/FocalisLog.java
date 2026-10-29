// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.core;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class FocalisLog {

    public static final Logger LOGGER = LogManager.getLogger("Focalis");

    private FocalisLog() {
    }

    public static Logger forFeature(String featureId) {
        return LogManager.getLogger("Focalis/" + featureId);
    }

    public static Logger forSystem(String name) {
        return LogManager.getLogger("Focalis/" + name);
    }
}
