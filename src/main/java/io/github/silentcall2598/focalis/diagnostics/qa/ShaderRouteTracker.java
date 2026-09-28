// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.diagnostics.qa;

import io.github.silentcall2598.focalis.render.lifecycle.RenderDrawKind;
import io.github.silentcall2598.focalis.render.lifecycle.RenderStage;
import io.github.silentcall2598.focalis.shader.routing.ShaderProgramRole;
import io.github.silentcall2598.focalis.shader.routing.ShaderProgramRouter;

import java.util.Arrays;

/**
 * Routes every precise stage START the way a shader system would and counts the resulting program roles, in total
 * and at most per world pass. Client thread only.
 */
final class ShaderRouteTracker {

    private static final ShaderProgramRole[] ROLES = ShaderProgramRole.values();

    private final QaReport.ShaderRoutes report;
    // Roles in the current world pass, hand included.
    private final int[] thisPass = new int[ROLES.length];
    private boolean passCounting;

    ShaderRouteTracker(QaReport.ShaderRoutes report) {
        this.report = report;
    }

    void worldStarted() {
        finishPass();
        passCounting = true;
    }

    void frameEnded() {
        finishPass();
    }

    void stageStarted(RenderStage stage, RenderDrawKind kind) {
        ShaderProgramRole role = ShaderProgramRouter.route(stage, kind);
        counts(role).count++;
        if (passCounting) {
            thisPass[role.ordinal()]++;
        }
    }

    private void finishPass() {
        if (!passCounting) {
            return;
        }
        for (ShaderProgramRole role : ROLES) {
            int count = thisPass[role.ordinal()];
            if (count > 0) {
                QaReport.RoleCounts counts = counts(role);
                counts.maxPerWorldPass = Math.max(counts.maxPerWorldPass, count);
            }
        }
        Arrays.fill(thisPass, 0);
        passCounting = false;
    }

    private QaReport.RoleCounts counts(ShaderProgramRole role) {
        QaReport.RoleCounts counts = report.roles.get(role.name());
        if (counts == null) {
            counts = new QaReport.RoleCounts();
            report.roles.put(role.name(), counts);
        }
        return counts;
    }
}
