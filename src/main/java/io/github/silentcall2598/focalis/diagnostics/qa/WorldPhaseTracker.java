// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.diagnostics.qa;

import io.github.silentcall2598.focalis.render.lifecycle.RenderPhase;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Checks that every WORLD START gets exactly one WORLD END before the next START, and that the post pass only renders
 * inside a WORLD END. The QA listeners around the features' own report each dispatch, the post pass monitor reports
 * each pass. Client thread only.
 */
final class WorldPhaseTracker {

    private final QaReport.WorldPhases phases;
    // A START that hasn't had its END yet.
    private boolean open;
    @Nullable
    private RenderPhase dispatching;
    private int passesThisDispatch;

    WorldPhaseTracker(QaReport.WorldPhases phases) {
        this.phases = phases;
    }

    void newSession() {
        phases.pairsPerSession.add(0);
    }

    // Runs before the features' listeners of that phase.
    void dispatchStarted(RenderPhase phase, int frame) {
        dispatching = phase;
        passesThisDispatch = 0;
        if (phase == RenderPhase.START) {
            phases.starts++;
            if (open) {
                phases.repeatedStarts++;
                problem(frame, "WORLD START again before the previous one got its END");
            }
            open = true;
            return;
        }
        phases.ends++;
        if (!open) {
            phases.endsWithoutStart++;
            problem(frame, "WORLD END without a WORLD START");
            return;
        }
        open = false;
        phases.pairs++;
        List<Integer> sessions = phases.pairsPerSession;
        if (sessions.isEmpty()) {
            sessions.add(0);
        }
        int last = sessions.size() - 1;
        sessions.set(last, sessions.get(last) + 1);
    }

    // Runs after the features' listeners of that phase.
    void dispatchFinished() {
        dispatching = null;
    }

    void passRendered(int frame) {
        if (dispatching != RenderPhase.END) {
            phases.passesOutsideEnd++;
            problem(frame, dispatching == RenderPhase.START ? "post pass rendered at WORLD START"
                    : "post pass rendered outside the world pass");
        } else if (++passesThisDispatch > 1) {
            phases.repeatedPasses++;
            problem(frame, "post pass rendered again in the same WORLD END");
        }
    }

    boolean waitingForEnd() {
        return open;
    }

    private void problem(int frame, String problem) {
        QaReport.addCapped(phases.problems, new QaReport.PhaseProblem(frame, phases.starts, phases.ends, problem));
    }
}
