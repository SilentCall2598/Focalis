// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.diagnostics.qa;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QaScenarioTest {

    @Test
    void idsAreUniqueCommandLineFriendlyAndFoundAgain() {
        Set<String> seen = new HashSet<>();
        for (QaScenario scenario : QaScenario.values()) {
            assertTrue(scenario.id.matches("[a-z0-9]+(-[a-z0-9]+)*"), scenario.id);
            assertTrue(seen.add(scenario.id), scenario.id);
            assertEquals(scenario, QaScenario.byId(scenario.id));
        }
    }

    @Test
    void everyScenarioStartsFromTheMainMenuAndHasTimeouts() {
        for (QaScenario scenario : QaScenario.values()) {
            List<QaStep> steps = scenario.steps();
            assertFalse(steps.isEmpty(), scenario.id);
            assertEquals("main-menu", steps.get(0).name, scenario.id);
            for (QaStep step : steps) {
                assertTrue(step.timeoutTicks > 0, scenario.id + ": " + step.name);
            }
        }
    }
}
