// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.render.lifecycle;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FirstPassStagesTest {

    @Test
    void reportsOnceWhenTheSecondWorldPassStarts() {
        FirstPassStages stages = new FirstPassStages();

        assertFalse(stages.worldPassStarted());
        assertTrue(stages.worldPassStarted());
        assertFalse(stages.worldPassStarted());
        assertFalse(stages.worldPassStarted());
    }

    @Test
    void onlyTheFirstWorldPassCounts() {
        FirstPassStages stages = new FirstPassStages();
        stages.stageStarted(RenderStage.SKY);
        stages.worldPassStarted();
        stages.stageStarted(RenderStage.TERRAIN);
        stages.stageStarted(RenderStage.HAND);
        stages.worldPassStarted();
        stages.stageStarted(RenderStage.CLOUDS);

        assertEquals(Arrays.asList(RenderStage.TERRAIN, RenderStage.HAND), stages.seen());
    }

    @Test
    void missingListsOnlyStagesVanillaAlwaysRenders() {
        FirstPassStages stages = new FirstPassStages();
        stages.worldPassStarted();
        stages.stageStarted(RenderStage.TERRAIN);
        stages.stageStarted(RenderStage.ENTITIES);
        stages.stageStarted(RenderStage.PARTICLES);
        stages.stageStarted(RenderStage.TRANSLUCENT);

        assertEquals(Collections.singletonList(RenderStage.WEATHER), stages.missing());
    }
}
