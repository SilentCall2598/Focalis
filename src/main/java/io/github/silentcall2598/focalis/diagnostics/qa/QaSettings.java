// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.diagnostics.qa;

import javax.annotation.Nullable;
import java.nio.file.Path;
import java.nio.file.Paths;

/** How a development QA run was launched. Normal launches never set these properties, so QA mode stays off. */
public final class QaSettings {

    static final String SCENARIO_PROPERTY = "focalis.qa.scenario";
    static final String OUTPUT_PROPERTY = "focalis.qa.output";
    static final String FULLSCREEN_PROPERTY = "focalis.qa.fullscreen";

    final QaScenario scenario;
    final Path output;
    final boolean fullscreenAllowed;

    private QaSettings(QaScenario scenario, Path output, boolean fullscreenAllowed) {
        this.scenario = scenario;
        this.output = output;
        this.fullscreenAllowed = fullscreenAllowed;
    }

    /** Null unless the launch set {@value #SCENARIO_PROPERTY}. */
    @Nullable
    public static QaSettings fromSystemProperties() {
        return parse(System.getProperty(SCENARIO_PROPERTY), System.getProperty(OUTPUT_PROPERTY),
                System.getProperty(FULLSCREEN_PROPERTY));
    }

    @Nullable
    static QaSettings parse(@Nullable String scenario, @Nullable String output, @Nullable String fullscreen) {
        if (scenario == null || scenario.trim().isEmpty()) {
            return null;
        }
        QaScenario found = QaScenario.byId(scenario.trim());
        if (found == null) {
            throw new IllegalArgumentException("Unknown QA scenario '" + scenario.trim() + "'. Known scenarios: "
                    + QaScenario.ids());
        }
        if (output == null || output.trim().isEmpty()) {
            throw new IllegalArgumentException(OUTPUT_PROPERTY + " has to name the folder QA output goes to");
        }
        return new QaSettings(found, Paths.get(output.trim()), Boolean.parseBoolean(fullscreen));
    }
}
