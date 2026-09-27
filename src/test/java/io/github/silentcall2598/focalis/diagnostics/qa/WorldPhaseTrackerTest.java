// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.diagnostics.qa;

import io.github.silentcall2598.focalis.render.lifecycle.RenderPhase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldPhaseTrackerTest {

    private QaReport.WorldPhases phases;
    private WorldPhaseTracker tracker;

    @BeforeEach
    void setUp() {
        phases = new QaReport.WorldPhases();
        tracker = new WorldPhaseTracker(phases);
    }

    private void dispatch(RenderPhase phase, int frame) {
        tracker.dispatchStarted(phase, frame);
        tracker.dispatchFinished();
    }

    @Test
    void pairsAreCountedPerSession() {
        tracker.newSession();
        dispatch(RenderPhase.START, 1);
        dispatch(RenderPhase.END, 1);
        dispatch(RenderPhase.START, 2);
        dispatch(RenderPhase.END, 2);
        tracker.newSession();
        dispatch(RenderPhase.START, 3);
        dispatch(RenderPhase.END, 3);

        assertEquals(3, phases.starts);
        assertEquals(3, phases.ends);
        assertEquals(3, phases.pairs);
        assertEquals(Arrays.asList(2, 1), phases.pairsPerSession);
        assertTrue(phases.problems.isEmpty());
        assertFalse(tracker.waitingForEnd());
    }

    @Test
    void endWithoutStartIsNotAPair() {
        dispatch(RenderPhase.END, 7);

        assertEquals(1, phases.endsWithoutStart);
        assertEquals(0, phases.pairs);
        assertEquals(1, phases.problems.size());
        assertEquals(7, phases.problems.get(0).frame);
    }

    @Test
    void secondStartBeforeEndIsReported() {
        dispatch(RenderPhase.START, 1);
        dispatch(RenderPhase.START, 1);
        dispatch(RenderPhase.END, 1);

        assertEquals(1, phases.repeatedStarts);
        assertEquals(1, phases.pairs);
        assertEquals(2, phases.starts);
    }

    @Test
    void unfinishedStartIsStillWaiting() {
        dispatch(RenderPhase.START, 1);

        assertTrue(tracker.waitingForEnd());
    }

    @Test
    void passOnlyBelongsInsideOneWorldEnd() {
        tracker.dispatchStarted(RenderPhase.END, 1);
        tracker.passRendered(1);
        tracker.dispatchFinished();
        assertEquals(0, phases.passesOutsideEnd);
        assertEquals(0, phases.repeatedPasses);

        tracker.dispatchStarted(RenderPhase.START, 2);
        tracker.passRendered(2);
        tracker.dispatchFinished();
        tracker.dispatchStarted(RenderPhase.END, 2);
        tracker.passRendered(2);
        tracker.passRendered(2);
        tracker.dispatchFinished();
        tracker.passRendered(3);

        assertEquals(2, phases.passesOutsideEnd);
        assertEquals(1, phases.repeatedPasses);
    }

    @Test
    void problemSamplesAreCappedButCountersStayExact() {
        for (int i = 0; i < QaReport.MAX_ENTRIES + 10; i++) {
            dispatch(RenderPhase.END, i);
        }

        assertEquals(QaReport.MAX_ENTRIES + 10, phases.endsWithoutStart);
        assertEquals(QaReport.MAX_ENTRIES, phases.problems.size());
    }
}
