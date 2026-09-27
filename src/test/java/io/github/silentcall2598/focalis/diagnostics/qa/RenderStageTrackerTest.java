// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.diagnostics.qa;

import io.github.silentcall2598.focalis.render.lifecycle.RenderPhase;
import io.github.silentcall2598.focalis.render.lifecycle.RenderStage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RenderStageTrackerTest {

    private QaReport.RenderStages report;
    private RenderStageTracker tracker;

    @BeforeEach
    void setUp() {
        report = new QaReport.RenderStages();
        tracker = new RenderStageTracker(report);
    }

    private void pair(RenderStage stage) {
        tracker.stage(stage, RenderPhase.START, 1);
        tracker.stage(stage, RenderPhase.END, 1);
    }

    // What vanilla does in one world pass below cloud height.
    private void vanillaPass() {
        tracker.world(RenderPhase.START, 1);
        pair(RenderStage.SKY);
        pair(RenderStage.CLOUDS);
        pair(RenderStage.TERRAIN);
        pair(RenderStage.TERRAIN);
        pair(RenderStage.TERRAIN);
        pair(RenderStage.ENTITIES);
        pair(RenderStage.PARTICLES);
        pair(RenderStage.PARTICLES);
        pair(RenderStage.WEATHER);
        pair(RenderStage.TRANSLUCENT);
        pair(RenderStage.ENTITIES);
        tracker.world(RenderPhase.END, 1);
        pair(RenderStage.HAND);
    }

    @Test
    void vanillaFrameIsCleanAndCountedPerWorldPass() {
        tracker.frameStarted();
        vanillaPass();
        tracker.frameEnded(1);

        assertEquals(0, report.problemCount());
        assertEquals(3, report.stages.get("TERRAIN").pairs);
        assertEquals(3, report.stages.get("TERRAIN").maxPerWorldPass);
        assertEquals(1, report.stages.get("HAND").maxPerWorldPass);
        assertFalse(tracker.stageOpen());
    }

    @Test
    void twoWorldPassesInOneFrameKeepPerPassCounts() {
        tracker.frameStarted();
        vanillaPass();
        vanillaPass();
        tracker.frameEnded(1);

        assertEquals(0, report.problemCount());
        assertEquals(6, report.stages.get("TERRAIN").pairs);
        assertEquals(3, report.stages.get("TERRAIN").maxPerWorldPass);
        assertEquals(2, report.stages.get("HAND").pairs);
        assertEquals(1, report.stages.get("HAND").maxPerWorldPass);
    }

    @Test
    void nestedStagesAreFineWhenTheyCloseInOrder() {
        tracker.frameStarted();
        tracker.world(RenderPhase.START, 1);
        tracker.stage(RenderStage.ENTITIES, RenderPhase.START, 1);
        pair(RenderStage.PARTICLES);
        tracker.stage(RenderStage.ENTITIES, RenderPhase.END, 1);
        tracker.world(RenderPhase.END, 1);
        tracker.frameEnded(1);

        assertEquals(0, report.problemCount());
    }

    @Test
    void closingOutOfOrderIsReported() {
        tracker.frameStarted();
        tracker.world(RenderPhase.START, 1);
        tracker.stage(RenderStage.ENTITIES, RenderPhase.START, 1);
        tracker.stage(RenderStage.PARTICLES, RenderPhase.START, 1);
        tracker.stage(RenderStage.ENTITIES, RenderPhase.END, 1);

        assertEquals(1, report.badNesting);
        assertEquals(1, report.unmatchedStarts);
        assertEquals(1, report.stages.get("ENTITIES").pairs);
        assertFalse(tracker.stageOpen());
    }

    @Test
    void endWithoutStartAndSameStageTwiceAreReported() {
        tracker.frameStarted();
        tracker.world(RenderPhase.START, 1);
        tracker.stage(RenderStage.SKY, RenderPhase.END, 1);
        tracker.stage(RenderStage.TERRAIN, RenderPhase.START, 1);
        tracker.stage(RenderStage.TERRAIN, RenderPhase.START, 1);

        assertEquals(1, report.endsWithoutStart);
        assertEquals(1, report.repeatedStarts);
    }

    @Test
    void stagesOutsideTheirContextAreReported() {
        pair(RenderStage.HAND);
        tracker.frameStarted();
        pair(RenderStage.HAND);
        pair(RenderStage.TERRAIN);

        assertEquals(2, report.outsideFrame);
        assertEquals(1, report.outsideWorld);
    }

    @Test
    void handInsideTheWorldPassIsRejected() {
        tracker.frameStarted();
        tracker.world(RenderPhase.START, 1);
        pair(RenderStage.HAND);
        tracker.world(RenderPhase.END, 1);
        tracker.frameEnded(1);

        assertEquals(1, report.handBeforeWorldEnd);
        assertEquals(1, report.problemCount());
        assertEquals("HAND", report.problems.get(0).stage);
    }

    @Test
    void stageLeftOpenIsReportedAtTheNextBoundary() {
        tracker.frameStarted();
        tracker.world(RenderPhase.START, 1);
        tracker.stage(RenderStage.WEATHER, RenderPhase.START, 1);
        tracker.world(RenderPhase.END, 1);

        assertEquals(1, report.unmatchedStarts);
        assertTrue(report.problems.get(0).problem.contains("WORLD END"));
    }

    @Test
    void problemSamplesAreCapped() {
        for (int i = 0; i < QaReport.MAX_ENTRIES + 5; i++) {
            tracker.stage(RenderStage.SKY, RenderPhase.END, i);
        }

        assertEquals(QaReport.MAX_ENTRIES + 5, report.endsWithoutStart);
        assertEquals(QaReport.MAX_ENTRIES, report.problems.size());
    }
}
