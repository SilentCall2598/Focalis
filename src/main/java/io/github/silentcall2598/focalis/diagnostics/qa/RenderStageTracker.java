// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.diagnostics.qa;

import io.github.silentcall2598.focalis.render.lifecycle.RenderDrawKind;
import io.github.silentcall2598.focalis.render.lifecycle.RenderPhase;
import io.github.silentcall2598.focalis.render.lifecycle.RenderStage;

import java.util.Arrays;
import java.util.Map;

/**
 * Checks the precise render stages. Every START needs an END with the same stage and draw kind, stages close in the
 * reverse order they opened, stages of the world pass only happen while WORLD is open, and HAND happens in the frame
 * after its world pass ended. The highest count per world pass is kept for each stage and kind, so a hook that fires
 * twice stands out. Client thread only.
 */
final class RenderStageTracker {

    private static final RenderStage[] STAGES = RenderStage.values();
    private static final RenderDrawKind[] KINDS = RenderDrawKind.values();

    private final QaReport.RenderStages report;
    // Open stages as stage and kind codes, innermost last.
    private int[] open = new int[16];
    private int openCount;
    // Starts of each stage and kind in the current world pass, hand included.
    private final int[] thisPass = new int[STAGES.length * KINDS.length];
    private boolean frameOpen;
    private boolean worldOpen;
    private boolean passCounting;
    private int worldPassesInFrame;

    RenderStageTracker(QaReport.RenderStages report) {
        this.report = report;
    }

    private static int code(RenderStage stage, RenderDrawKind kind) {
        return stage.ordinal() * KINDS.length + kind.ordinal();
    }

    private static RenderStage stageOf(int code) {
        return STAGES[code / KINDS.length];
    }

    private static RenderDrawKind kindOf(int code) {
        return KINDS[code % KINDS.length];
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

    void stage(RenderStage stage, RenderPhase phase, RenderDrawKind kind, int frame) {
        if (phase == RenderPhase.START) {
            counts(stage).starts++;
            kindCounts(stage, kind).starts++;
            started(stage, kind, frame);
        } else {
            counts(stage).ends++;
            kindCounts(stage, kind).ends++;
            ended(stage, kind, frame);
        }
    }

    // Forge's render pass while an ENTITIES stage starts. Pass 0 and 1 have to match the two entity kinds.
    void entityPass(RenderDrawKind kind, int forgePass, int frame) {
        boolean matches = kind == RenderDrawKind.ENTITY_PASS_0 ? forgePass == 0
                : kind == RenderDrawKind.ENTITY_PASS_1 && forgePass == 1;
        if (!matches) {
            report.entityPassMismatches++;
            problem(frame, RenderStage.ENTITIES, kind, "Forge render pass is " + forgePass);
        }
    }

    private void started(RenderStage stage, RenderDrawKind kind, int frame) {
        if (!frameOpen) {
            report.outsideFrame++;
            problem(frame, stage, kind, "START outside a frame");
        } else if (stage == RenderStage.HAND) {
            if (worldPassesInFrame == 0) {
                report.outsideFrame++;
                problem(frame, stage, kind, "HAND before any world pass in this frame");
            } else if (worldOpen) {
                report.handBeforeWorldEnd++;
                problem(frame, stage, kind, "HAND while the world pass is still open");
            }
        } else if (!worldOpen) {
            report.outsideWorld++;
            problem(frame, stage, kind, "START while no world pass is open");
        }
        int code = code(stage, kind);
        if (find(code) >= 0) {
            report.repeatedStarts++;
            problem(frame, stage, kind, "START while the same stage and kind is already open");
        }
        if (openCount == open.length) {
            open = Arrays.copyOf(open, open.length * 2);
        }
        open[openCount++] = code;
        if (passCounting) {
            thisPass[code]++;
        }
    }

    private void ended(RenderStage stage, RenderDrawKind kind, int frame) {
        int exact = find(code(stage, kind));
        if (exact >= 0) {
            if (exact != openCount - 1) {
                report.badNesting++;
                problem(frame, stage, kind, "END while " + describe(open[openCount - 1]) + " is still open inside it");
                dropAbove(exact);
            }
            openCount--;
            counts(stage).pairs++;
            kindCounts(stage, kind).pairs++;
            return;
        }
        int sameStage = findStage(stage);
        if (sameStage >= 0) {
            report.kindMismatches++;
            problem(frame, stage, kind, "END for a " + describe(open[sameStage]) + " START");
            dropAbove(sameStage);
            openCount--;
            return;
        }
        report.endsWithoutStart++;
        problem(frame, stage, kind, "END without a START");
    }

    private int find(int code) {
        for (int i = openCount - 1; i >= 0; i--) {
            if (open[i] == code) {
                return i;
            }
        }
        return -1;
    }

    private int findStage(RenderStage stage) {
        for (int i = openCount - 1; i >= 0; i--) {
            if (stageOf(open[i]) == stage) {
                return i;
            }
        }
        return -1;
    }

    // Whatever is still open inside the closed entry never gets a proper END.
    private void dropAbove(int index) {
        report.unmatchedStarts += openCount - 1 - index;
        openCount = index + 1;
    }

    private void closeLeftovers(int frame, String when) {
        while (openCount > 0) {
            int code = open[--openCount];
            report.unmatchedStarts++;
            problem(frame, stageOf(code), kindOf(code), when);
        }
    }

    private void finishPass() {
        if (!passCounting) {
            return;
        }
        for (RenderStage stage : STAGES) {
            int stageTotal = 0;
            for (RenderDrawKind kind : KINDS) {
                int count = thisPass[code(stage, kind)];
                if (count > 0) {
                    QaReport.StageCounts counts = kindCounts(stage, kind);
                    counts.maxPerWorldPass = Math.max(counts.maxPerWorldPass, count);
                    stageTotal += count;
                }
            }
            if (stageTotal > 0) {
                QaReport.StageCounts counts = counts(stage);
                counts.maxPerWorldPass = Math.max(counts.maxPerWorldPass, stageTotal);
            }
        }
        Arrays.fill(thisPass, 0);
        passCounting = false;
    }

    boolean stageOpen() {
        return openCount > 0;
    }

    private static String describe(int code) {
        return stageOf(code) + "/" + kindOf(code);
    }

    private QaReport.StageCounts counts(RenderStage stage) {
        return counts(report.stages, stage.name());
    }

    private QaReport.StageCounts kindCounts(RenderStage stage, RenderDrawKind kind) {
        return counts(report.kinds, stage + "/" + kind);
    }

    private static QaReport.StageCounts counts(Map<String, QaReport.StageCounts> map, String key) {
        QaReport.StageCounts counts = map.get(key);
        if (counts == null) {
            counts = new QaReport.StageCounts();
            map.put(key, counts);
        }
        return counts;
    }

    private void problem(int frame, RenderStage stage, RenderDrawKind kind, String problem) {
        QaReport.addCapped(report.problems, new QaReport.StageProblem(frame, stage.name(), kind.name(), problem));
    }
}
