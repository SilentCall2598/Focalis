// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.diagnostics.qa;

import io.github.silentcall2598.focalis.render.lifecycle.RenderPhase;
import io.github.silentcall2598.focalis.render.lifecycle.RenderStage;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Checks the precise render stages. Every START needs its END, stages close in the reverse order they opened, stages
 * of the world pass only happen while WORLD is open, and HAND happens in the frame after its world pass ended. The
 * highest count per world pass is kept, so a hook that fires twice stands out. Client thread only.
 */
final class RenderStageTracker {

    private final QaReport.RenderStages report;
    private final Deque<RenderStage> open = new ArrayDeque<>();
    // Starts of each stage in the current world pass, hand included, indexed by ordinal.
    private final int[] thisPass = new int[RenderStage.values().length];
    private boolean frameOpen;
    private boolean worldOpen;
    private boolean passCounting;
    private int worldPassesInFrame;

    RenderStageTracker(QaReport.RenderStages report) {
        this.report = report;
    }

    void frameStarted() {
        frameOpen = true;
        worldPassesInFrame = 0;
    }

    void frameEnded(int frame) {
        closeLeftovers(frame, "still open at FRAME END");
        finishPass();
        frameOpen = false;
        worldOpen = false;
    }

    void world(RenderPhase phase, int frame) {
        if (phase == RenderPhase.START) {
            closeLeftovers(frame, "still open at WORLD START");
            finishPass();
            passCounting = true;
            worldOpen = true;
            worldPassesInFrame++;
        } else {
            closeLeftovers(frame, "still open at WORLD END");
            worldOpen = false;
        }
    }

    void stage(RenderStage stage, RenderPhase phase, int frame) {
        QaReport.StageCounts counts = counts(stage);
        if (phase == RenderPhase.START) {
            counts.starts++;
            started(stage, frame);
        } else {
            counts.ends++;
            ended(stage, counts, frame);
        }
    }

    private void started(RenderStage stage, int frame) {
        if (!frameOpen) {
            report.outsideFrame++;
            problem(frame, stage, "START outside a frame");
        } else if (stage == RenderStage.HAND) {
            if (worldPassesInFrame == 0) {
                report.outsideFrame++;
                problem(frame, stage, "HAND before any world pass in this frame");
            } else if (worldOpen) {
                report.handBeforeWorldEnd++;
                problem(frame, stage, "HAND while the world pass is still open");
            }
        } else if (!worldOpen) {
            report.outsideWorld++;
            problem(frame, stage, "START while no world pass is open");
        }
        if (open.contains(stage)) {
            report.repeatedStarts++;
            problem(frame, stage, "START while the same stage is already open");
        }
        open.push(stage);
        if (passCounting) {
            thisPass[stage.ordinal()]++;
        }
    }

    private void ended(RenderStage stage, QaReport.StageCounts counts, int frame) {
        if (!open.contains(stage)) {
            report.endsWithoutStart++;
            problem(frame, stage, "END without a START");
            return;
        }
        if (open.peek() != stage) {
            report.badNesting++;
            problem(frame, stage, "END while " + open.peek() + " is still open inside it");
            while (open.peek() != stage) {
                open.pop();
                report.unmatchedStarts++;
            }
        }
        open.pop();
        counts.pairs++;
    }

    private void closeLeftovers(int frame, String when) {
        while (!open.isEmpty()) {
            RenderStage stage = open.pop();
            report.unmatchedStarts++;
            problem(frame, stage, when);
        }
    }

    private void finishPass() {
        if (!passCounting) {
            return;
        }
        for (RenderStage stage : RenderStage.values()) {
            int count = thisPass[stage.ordinal()];
            if (count > 0) {
                QaReport.StageCounts counts = counts(stage);
                counts.maxPerWorldPass = Math.max(counts.maxPerWorldPass, count);
            }
            thisPass[stage.ordinal()] = 0;
        }
        passCounting = false;
    }

    boolean stageOpen() {
        return !open.isEmpty();
    }

    private QaReport.StageCounts counts(RenderStage stage) {
        QaReport.StageCounts counts = report.stages.get(stage.name());
        if (counts == null) {
            counts = new QaReport.StageCounts();
            report.stages.put(stage.name(), counts);
        }
        return counts;
    }

    private void problem(int frame, RenderStage stage, String problem) {
        QaReport.addCapped(report.problems, new QaReport.StageProblem(frame, stage.name(), problem));
    }
}
