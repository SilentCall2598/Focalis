// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.render.lifecycle;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FirstPassStagesTest {

    private FirstPassStages stages;

    @BeforeEach
    void setUp() {
        stages = new FirstPassStages();
    }

    private void fire(RenderStage stage, int times) {
        for (int i = 0; i < times; i++) {
            stages.stageStarted(stage);
        }
    }

    // Vanilla's required calls in one world pass, without the conditional ones.
    private void requiredVanillaCalls() {
        fire(RenderStage.TERRAIN, 3);
        fire(RenderStage.ENTITIES, 2);
        fire(RenderStage.PARTICLES, 2);
        fire(RenderStage.TRANSLUCENT, 1);
        fire(RenderStage.WEATHER, 1);
    }

    @Test
    void reportsOnceWhenTheSecondWorldPassStarts() {
        assertFalse(stages.worldPassStarted());
        assertTrue(stages.worldPassStarted());
        assertFalse(stages.worldPassStarted());
        assertFalse(stages.worldPassStarted());
    }

    @Test
    void exactVanillaCountsMatch() {
        stages.worldPassStarted();
        requiredVanillaCalls();
        fire(RenderStage.SKY, 1);
        fire(RenderStage.CLOUDS, 1);
        fire(RenderStage.HAND, 1);

        assertTrue(stages.mismatches().isEmpty());
        assertEquals(Integer.valueOf(3), stages.seen().get(RenderStage.TERRAIN));
    }

    @Test
    void partialTerrainIsDetected() {
        stages.worldPassStarted();
        fire(RenderStage.TERRAIN, 2);
        fire(RenderStage.ENTITIES, 2);
        fire(RenderStage.PARTICLES, 2);
        fire(RenderStage.TRANSLUCENT, 1);
        fire(RenderStage.WEATHER, 1);

        assertEquals(Collections.singletonMap(RenderStage.TERRAIN, 2), stages.mismatches());
        assertEquals(Collections.singletonList("TERRAIN 2 instead of 3"),
                FirstPassStages.describe(stages.mismatches()));
    }

    @Test
    void partialEntitiesAndParticlesAreDetected() {
        stages.worldPassStarted();
        fire(RenderStage.TERRAIN, 3);
        fire(RenderStage.ENTITIES, 1);
        fire(RenderStage.PARTICLES, 1);
        fire(RenderStage.TRANSLUCENT, 1);
        fire(RenderStage.WEATHER, 1);

        Map<RenderStage, Integer> wrong = stages.mismatches();
        assertEquals(2, wrong.size());
        assertEquals(Integer.valueOf(1), wrong.get(RenderStage.ENTITIES));
        assertEquals(Integer.valueOf(1), wrong.get(RenderStage.PARTICLES));
    }

    @Test
    void missingWeatherIsDetected() {
        stages.worldPassStarted();
        fire(RenderStage.TERRAIN, 3);
        fire(RenderStage.ENTITIES, 2);
        fire(RenderStage.PARTICLES, 2);
        fire(RenderStage.TRANSLUCENT, 1);

        assertEquals(Collections.singletonMap(RenderStage.WEATHER, 0), stages.mismatches());
    }

    @Test
    void conditionalStagesMayBeAbsent() {
        stages.worldPassStarted();
        requiredVanillaCalls();

        assertTrue(stages.mismatches().isEmpty());
        assertFalse(stages.seen().containsKey(RenderStage.SKY));
        assertFalse(stages.seen().containsKey(RenderStage.CLOUDS));
        assertFalse(stages.seen().containsKey(RenderStage.HAND));
    }

    @Test
    void onlyTheFirstWorldPassIsMeasured() {
        fire(RenderStage.TERRAIN, 5);
        stages.worldPassStarted();
        requiredVanillaCalls();
        stages.worldPassStarted();
        fire(RenderStage.TERRAIN, 1);
        fire(RenderStage.WEATHER, 4);

        assertTrue(stages.mismatches().isEmpty());
        assertEquals(Integer.valueOf(3), stages.seen().get(RenderStage.TERRAIN));
    }
}
