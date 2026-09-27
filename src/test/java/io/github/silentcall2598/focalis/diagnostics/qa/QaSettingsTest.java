// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.diagnostics.qa;

import org.junit.jupiter.api.Test;

import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QaSettingsTest {

    @Test
    void qaModeStaysOffUnlessAScenarioIsNamed() {
        assertNull(QaSettings.parse(null, null, null));
        assertNull(QaSettings.parse("  ", "out", "true"));
    }

    @Test
    void readsTheScenarioOutputAndFullscreenPermission() {
        QaSettings settings = QaSettings.parse(" post-process-smoke ", "build/qa/latest", null);

        assertNotNull(settings);
        assertEquals(QaScenario.POST_PROCESS_SMOKE, settings.scenario);
        assertEquals(Paths.get("build/qa/latest"), settings.output);
        assertFalse(settings.fullscreenAllowed);
        assertTrue(QaSettings.parse("post-process-resize", "out", "true").fullscreenAllowed);
    }

    @Test
    void unknownScenariosAndMissingOutputAreRejected() {
        IllegalArgumentException unknown = assertThrows(IllegalArgumentException.class,
                () -> QaSettings.parse("post-process-everything", "out", null));
        assertTrue(unknown.getMessage().contains("post-process-smoke"), unknown.getMessage());
        assertThrows(IllegalArgumentException.class, () -> QaSettings.parse("world-reload", " ", null));
    }
}
