// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.diagnostics.qa;

import io.github.silentcall2598.focalis.render.lifecycle.RenderDrawKind;
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

    private void start(RenderStage stage, RenderDrawKind kind) {
        tracker.stage(stage, RenderPhase.START, kind, 1);
    }

    private void end(RenderStage stage, RenderDrawKind kind) {
        tracker.stage(stage, RenderPhase.END, kind, 1);
    }

    private void pair(RenderStage stage, RenderDrawKind kind) {
        start(stage, kind);
        end(stage, kind);
    }

    private void pair(RenderStage stage) {
        pair(stage, RenderDrawKind.DEFAULT);
    }

    // What vanilla does in one world pass below cloud height.
    private void vanillaPass() {
        tracker.world(RenderPhase.START, 1);
        pair(RenderStage.SKY);
        pair(RenderStage.CLOUDS);
        pair(RenderStage.TERRAIN, RenderDrawKind.TERRAIN_SOLID);
        pair(RenderStage.TERRAIN, RenderDrawKind.TERRAIN_CUTOUT_MIPPED);
        pair(RenderStage.TERRAIN, RenderDrawKind.TERRAIN_CUTOUT);
        pair(RenderStage.ENTITIES, RenderDrawKind.ENTITY_PASS_0);
        pair(RenderStage.PARTICLES, RenderDrawKind.PARTICLES_LIT);
        pair(RenderStage.PARTICLES, RenderDrawKind.PARTICLES_NORMAL);
        pair(RenderStage.WEATHER);
        pair(RenderStage.TRANSLUCENT, RenderDrawKind.TERRAIN_TRANSLUCENT);
        pair(RenderStage.ENTITIES, RenderDrawKind.ENTITY_PASS_1);
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
        assertEquals(1, report.kinds.get("TERRAIN/TERRAIN_CUTOUT").maxPerWorldPass);
        assertEquals(1, report.kinds.get("ENTITIES/ENTITY_PASS_1").pairs);
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
        assertEquals(2, report.kinds.get("PARTICLES/PARTICLES_LIT").pairs);
        assertEquals(1, report.kinds.get("PARTICLES/PARTICLES_LIT").maxPerWorldPass);
        assertEquals(1, report.stages.get("HAND").maxPerWorldPass);
    }

    @Test
    void endWithAnotherKindIsRejected() {
        tracker.frameStarted();
        tracker.world(RenderPhase.START, 1);
        start(RenderStage.TERRAIN, RenderDrawKind.TERRAIN_SOLID);
        end(RenderStage.TERRAIN, RenderDrawKind.TERRAIN_CUTOUT);

        assertEquals(1, report.kindMismatches);
        assertEquals(0, report.stages.get("TERRAIN").pairs);
        assertEquals("TERRAIN_CUTOUT", report.problems.get(0).kind);
        assertFalse(tracker.stageOpen());
    }

    @Test
    void sameStageWithAnotherKindMayNest() {
        tracker.frameStarted();
        tracker.world(RenderPhase.START, 1);
        start(RenderStage.ENTITIES, RenderDrawKind.ENTITY_PASS_0);
        pair(RenderStage.ENTITIES, RenderDrawKind.ENTITY_PASS_1);
        end(RenderStage.ENTITIES, RenderDrawKind.ENTITY_PASS_0);

        assertEquals(0, report.problemCount());
        assertEquals(2, report.stages.get("ENTITIES").pairs);
    }

    @Test
    void entityKindsHaveToMatchForgesRenderPass() {
        tracker.entityPass(RenderDrawKind.ENTITY_PASS_0, 0, 1);
        tracker.entityPass(RenderDrawKind.ENTITY_PASS_1, 1, 1);
        tracker.entityPass(RenderDrawKind.ENTITY_PASS_1, 0, 1);

        assertEquals(1, report.entityPassMismatches);
    }

    @Test
    void closingOutOfOrderIsReported() {
        tracker.frameStarted();
        tracker.world(RenderPhase.START, 1);
        start(RenderStage.ENTITIES, RenderDrawKind.ENTITY_PASS_0);
        start(RenderStage.PARTICLES, RenderDrawKind.PARTICLES_LIT);
        end(RenderStage.ENTITIES, RenderDrawKind.ENTITY_PASS_0);

        assertEquals(1, report.badNesting);
        assertEquals(1, report.unmatchedStarts);
        assertEquals(1, report.stages.get("ENTITIES").pairs);
        assertFalse(tracker.stageOpen());
    }

    @Test
    void endWithoutStartAndSameStageAndKindTwiceAreReported() {
        tracker.frameStarted();
        tracker.world(RenderPhase.START, 1);
        end(RenderStage.SKY, RenderDrawKind.DEFAULT);
        start(RenderStage.TERRAIN, RenderDrawKind.TERRAIN_SOLID);
        start(RenderStage.TERRAIN, RenderDrawKind.TERRAIN_SOLID);

        assertEquals(1, report.endsWithoutStart);
        assertEquals(1, report.repeatedStarts);
    }

    @Test
    void stagesOutsideTheirContextAreReported() {
        pair(RenderStage.HAND);
        tracker.frameStarted();
        pair(RenderStage.HAND);
        pair(RenderStage.TERRAIN, RenderDrawKind.TERRAIN_SOLID);

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
        start(RenderStage.WEATHER, RenderDrawKind.DEFAULT);
        tracker.world(RenderPhase.END, 1);

        assertEquals(1, report.unmatchedStarts);
        assertTrue(report.problems.get(0).problem.contains("WORLD END"));
    }

    @Test
    void problemSamplesAreCapped() {
        for (int i = 0; i < QaReport.MAX_ENTRIES + 5; i++) {
            tracker.stage(RenderStage.SKY, RenderPhase.END, RenderDrawKind.DEFAULT, i);
        }

        assertEquals(QaReport.MAX_ENTRIES + 5, report.endsWithoutStart);
        assertEquals(QaReport.MAX_ENTRIES, report.problems.size());
    }
}
