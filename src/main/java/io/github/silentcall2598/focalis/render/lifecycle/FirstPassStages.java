// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.render.lifecycle;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

// Which stage hooks fired during the first world pass. The stage hooks may not apply without any error, so this is
// how a missing one shows up in a normal log.
final class FirstPassStages {

    // Vanilla calls these in every world pass, so their hooks must have fired by the end of the first one.
    static final Set<RenderStage> ALWAYS = EnumSet.of(RenderStage.TERRAIN, RenderStage.ENTITIES, RenderStage.PARTICLES,
            RenderStage.TRANSLUCENT, RenderStage.WEATHER);

    private int passesStarted;
    private int seen;

    // True when the first world pass, hand included, has just finished and should be reported.
    boolean worldPassStarted() {
        if (passesStarted >= 2) {
            return false;
        }
        passesStarted++;
        return passesStarted == 2;
    }

    void stageStarted(RenderStage stage) {
        if (passesStarted == 1) {
            seen |= 1 << stage.ordinal();
        }
    }

    List<RenderStage> seen() {
        List<RenderStage> stages = new ArrayList<>();
        for (RenderStage stage : RenderStage.values()) {
            if ((seen & 1 << stage.ordinal()) != 0) {
                stages.add(stage);
            }
        }
        return stages;
    }

    List<RenderStage> missing() {
        List<RenderStage> stages = new ArrayList<>();
        for (RenderStage stage : ALWAYS) {
            if ((seen & 1 << stage.ordinal()) == 0) {
                stages.add(stage);
            }
        }
        return stages;
    }
}
