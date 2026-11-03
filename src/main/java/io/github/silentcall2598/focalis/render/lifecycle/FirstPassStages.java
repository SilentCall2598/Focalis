// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.render.lifecycle;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

final class FirstPassStages {

    private static final int KINDS = RenderDrawKind.values().length;

    // Vanilla draws each of these exactly once per world pass, and no other kinds of these stages. CLOUDS and HAND
    // depend on settings, so they aren't checked. SKY is only checked once the vanilla surface sky shows up.
    private static final Set<RenderStage> REQUIRED = EnumSet.of(RenderStage.TERRAIN, RenderStage.TRANSLUCENT,
            RenderStage.ENTITIES, RenderStage.PARTICLES, RenderStage.WEATHER);
    private static final int[] EXPECTED = new int[RenderStage.values().length * KINDS];

    static {
        EXPECTED[index(RenderStage.SKY, RenderDrawKind.SKY_BASIC)] = 1;
        EXPECTED[index(RenderStage.SKY, RenderDrawKind.SKY_TEXTURED)] = 2;
        EXPECTED[index(RenderStage.TERRAIN, RenderDrawKind.TERRAIN_SOLID)] = 1;
        EXPECTED[index(RenderStage.TERRAIN, RenderDrawKind.TERRAIN_CUTOUT_MIPPED)] = 1;
        EXPECTED[index(RenderStage.TERRAIN, RenderDrawKind.TERRAIN_CUTOUT)] = 1;
        EXPECTED[index(RenderStage.TRANSLUCENT, RenderDrawKind.TERRAIN_TRANSLUCENT)] = 1;
        EXPECTED[index(RenderStage.ENTITIES, RenderDrawKind.ENTITY_PASS_0)] = 1;
        EXPECTED[index(RenderStage.ENTITIES, RenderDrawKind.ENTITY_PASS_1)] = 1;
        EXPECTED[index(RenderStage.PARTICLES, RenderDrawKind.PARTICLES_LIT)] = 1;
        EXPECTED[index(RenderStage.PARTICLES, RenderDrawKind.PARTICLES_NORMAL)] = 1;
        EXPECTED[index(RenderStage.WEATHER, RenderDrawKind.DEFAULT)] = 1;
    }

    private final int[] counts = new int[EXPECTED.length];
    private int passesStarted;

    private static int index(RenderStage stage, RenderDrawKind kind) {
        return stage.ordinal() * KINDS + kind.ordinal();
    }

    static int expected(RenderStage stage, RenderDrawKind kind) {
        return EXPECTED[index(stage, kind)];
    }

    boolean worldPassStarted() {
        if (passesStarted >= 2) {
            return false;
        }
        passesStarted++;
        return passesStarted == 2;
    }

    void stageStarted(RenderStage stage, RenderDrawKind kind) {
        if (passesStarted == 1) {
            counts[index(stage, kind)]++;
        }
    }

    List<String> seen() {
        List<String> seen = new ArrayList<>();
        for (RenderStage stage : RenderStage.values()) {
            for (RenderDrawKind kind : RenderDrawKind.values()) {
                int count = counts[index(stage, kind)];
                if (count > 0) {
                    seen.add(stage + "/" + kind + "=" + count);
                }
            }
        }
        return seen;
    }

    List<String> mismatches() {
        List<String> wrong = new ArrayList<>();
        // The camera's kind depends on whether anaglyph 3D is on, so only its total is checked.
        int cameras = 0;
        for (RenderDrawKind kind : RenderDrawKind.values()) {
            cameras += counts[index(RenderStage.CAMERA, kind)];
        }
        if (cameras != 1) {
            wrong.add(RenderStage.CAMERA + " " + cameras + " instead of 1");
        }
        for (RenderStage stage : RenderStage.values()) {
            if (!checked(stage)) {
                continue;
            }
            for (RenderDrawKind kind : RenderDrawKind.values()) {
                int i = index(stage, kind);
                if (counts[i] != EXPECTED[i]) {
                    wrong.add(stage + "/" + kind + " " + counts[i] + " instead of " + EXPECTED[i]);
                }
            }
        }
        return wrong;
    }

    // Custom sky renderers, the End and other dimensions don't draw the vanilla surface sky, so a missing sky is fine.
    // Once it's there, its sun and moon have to be there too.
    private boolean checked(RenderStage stage) {
        return REQUIRED.contains(stage) || stage == RenderStage.SKY
                && counts[index(RenderStage.SKY, RenderDrawKind.SKY_BASIC)] > 0;
    }
}
