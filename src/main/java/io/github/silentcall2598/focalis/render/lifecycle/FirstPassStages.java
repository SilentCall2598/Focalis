// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.render.lifecycle;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

// How often each stage hook fired during the first world pass. The stage hooks may not apply without any error, so
// this is how a missing one, or one of several call sites of a stage, shows up in a normal log.
final class FirstPassStages {

    // Vanilla calls these exactly this often in every world pass. SKY, CLOUDS and HAND depend on settings.
    static final Map<RenderStage, Integer> EXPECTED;

    static {
        Map<RenderStage, Integer> expected = new EnumMap<>(RenderStage.class);
        expected.put(RenderStage.TERRAIN, 3);
        expected.put(RenderStage.ENTITIES, 2);
        expected.put(RenderStage.PARTICLES, 2);
        expected.put(RenderStage.TRANSLUCENT, 1);
        expected.put(RenderStage.WEATHER, 1);
        EXPECTED = Collections.unmodifiableMap(expected);
    }

    private final int[] counts = new int[RenderStage.values().length];
    private int passesStarted;

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
            counts[stage.ordinal()]++;
        }
    }

    Map<RenderStage, Integer> seen() {
        Map<RenderStage, Integer> seen = new EnumMap<>(RenderStage.class);
        for (RenderStage stage : RenderStage.values()) {
            if (counts[stage.ordinal()] > 0) {
                seen.put(stage, counts[stage.ordinal()]);
            }
        }
        return seen;
    }

    // Required stages whose first pass count isn't vanilla's, with the count that was seen.
    Map<RenderStage, Integer> mismatches() {
        Map<RenderStage, Integer> wrong = new EnumMap<>(RenderStage.class);
        for (Map.Entry<RenderStage, Integer> expected : EXPECTED.entrySet()) {
            int actual = counts[expected.getKey().ordinal()];
            if (actual != expected.getValue()) {
                wrong.put(expected.getKey(), actual);
            }
        }
        return wrong;
    }

    static List<String> describe(Map<RenderStage, Integer> mismatches) {
        List<String> lines = new ArrayList<>();
        for (Map.Entry<RenderStage, Integer> mismatch : mismatches.entrySet()) {
            lines.add(mismatch.getKey() + " " + mismatch.getValue() + " instead of " + EXPECTED.get(mismatch.getKey()));
        }
        return lines;
    }
}
