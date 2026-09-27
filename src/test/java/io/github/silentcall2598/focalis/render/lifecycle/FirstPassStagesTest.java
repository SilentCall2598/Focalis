// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.render.lifecycle;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FirstPassStagesTest {

    private FirstPassStages stages;

    @BeforeEach
    void setUp() {
        stages = new FirstPassStages();
    }

    private void fire(RenderStage stage, RenderDrawKind kind) {
        stages.stageStarted(stage, kind);
    }

    // Vanilla's required calls in one world pass, leaving out the one named.
    private void requiredVanillaCallsExcept(RenderStage skipStage, RenderDrawKind skipKind) {
        Object[][] calls = {
                {RenderStage.TERRAIN, RenderDrawKind.TERRAIN_SOLID},
                {RenderStage.TERRAIN, RenderDrawKind.TERRAIN_CUTOUT_MIPPED},
                {RenderStage.TERRAIN, RenderDrawKind.TERRAIN_CUTOUT},
                {RenderStage.TRANSLUCENT, RenderDrawKind.TERRAIN_TRANSLUCENT},
                {RenderStage.ENTITIES, RenderDrawKind.ENTITY_PASS_0},
                {RenderStage.ENTITIES, RenderDrawKind.ENTITY_PASS_1},
                {RenderStage.PARTICLES, RenderDrawKind.PARTICLES_LIT},
                {RenderStage.PARTICLES, RenderDrawKind.PARTICLES_NORMAL},
                {RenderStage.WEATHER, RenderDrawKind.DEFAULT}};
        for (Object[] call : calls) {
            if (call[0] != skipStage || call[1] != skipKind) {
                fire((RenderStage) call[0], (RenderDrawKind) call[1]);
            }
        }
    }

    private void requiredVanillaCalls() {
        requiredVanillaCallsExcept(null, null);
    }

    @Test
    void reportsOnceWhenTheSecondWorldPassStarts() {
        assertFalse(stages.worldPassStarted());
        assertTrue(stages.worldPassStarted());
        assertFalse(stages.worldPassStarted());
        assertFalse(stages.worldPassStarted());
    }

    @Test
    void exactVanillaDistributionMatches() {
        stages.worldPassStarted();
        requiredVanillaCalls();
        fire(RenderStage.SKY, RenderDrawKind.DEFAULT);
        fire(RenderStage.CLOUDS, RenderDrawKind.DEFAULT);
        fire(RenderStage.HAND, RenderDrawKind.DEFAULT);

        assertTrue(stages.mismatches().isEmpty());
        assertTrue(stages.seen().contains("TERRAIN/TERRAIN_CUTOUT=1"));
    }

    @Test
    void missingCutoutIsDetected() {
        stages.worldPassStarted();
        requiredVanillaCallsExcept(RenderStage.TERRAIN, RenderDrawKind.TERRAIN_CUTOUT);

        assertEquals(Collections.singletonList("TERRAIN/TERRAIN_CUTOUT 0 instead of 1"), stages.mismatches());
    }

    @Test
    void missingEntityPass1IsDetected() {
        stages.worldPassStarted();
        requiredVanillaCallsExcept(RenderStage.ENTITIES, RenderDrawKind.ENTITY_PASS_1);

        assertEquals(Collections.singletonList("ENTITIES/ENTITY_PASS_1 0 instead of 1"), stages.mismatches());
    }

    @Test
    void unclassifiedEntityPassIsDetected() {
        stages.worldPassStarted();
        requiredVanillaCallsExcept(RenderStage.ENTITIES, RenderDrawKind.ENTITY_PASS_1);
        fire(RenderStage.ENTITIES, RenderDrawKind.DEFAULT);

        assertEquals(Arrays.asList("ENTITIES/DEFAULT 1 instead of 0",
                "ENTITIES/ENTITY_PASS_1 0 instead of 1"), stages.mismatches());
    }

    @Test
    void missingLitParticlesIsDetected() {
        stages.worldPassStarted();
        requiredVanillaCallsExcept(RenderStage.PARTICLES, RenderDrawKind.PARTICLES_LIT);

        assertEquals(Collections.singletonList("PARTICLES/PARTICLES_LIT 0 instead of 1"), stages.mismatches());
    }

    @Test
    void missingWeatherIsDetected() {
        stages.worldPassStarted();
        requiredVanillaCallsExcept(RenderStage.WEATHER, RenderDrawKind.DEFAULT);

        assertEquals(Collections.singletonList("WEATHER/DEFAULT 0 instead of 1"), stages.mismatches());
    }

    @Test
    void duplicateOrWrongKindsAreDetected() {
        stages.worldPassStarted();
        requiredVanillaCalls();
        fire(RenderStage.ENTITIES, RenderDrawKind.ENTITY_PASS_0);
        fire(RenderStage.TERRAIN, RenderDrawKind.DEFAULT);

        assertTrue(stages.mismatches().contains("ENTITIES/ENTITY_PASS_0 2 instead of 1"));
        assertTrue(stages.mismatches().contains("TERRAIN/DEFAULT 1 instead of 0"));
        assertEquals(2, stages.mismatches().size());
    }

    @Test
    void conditionalStagesMayBeAbsent() {
        stages.worldPassStarted();
        requiredVanillaCalls();

        assertTrue(stages.mismatches().isEmpty());
        assertFalse(stages.seen().toString().contains("SKY"));
        assertFalse(stages.seen().toString().contains("CLOUDS"));
        assertFalse(stages.seen().toString().contains("HAND"));
    }

    @Test
    void onlyTheFirstWorldPassIsMeasured() {
        fire(RenderStage.TERRAIN, RenderDrawKind.TERRAIN_SOLID);
        stages.worldPassStarted();
        requiredVanillaCalls();
        stages.worldPassStarted();
        fire(RenderStage.TERRAIN, RenderDrawKind.TERRAIN_SOLID);
        fire(RenderStage.WEATHER, RenderDrawKind.DEFAULT);

        assertTrue(stages.mismatches().isEmpty());
        assertTrue(stages.seen().contains("TERRAIN/TERRAIN_SOLID=1"));
    }
}
