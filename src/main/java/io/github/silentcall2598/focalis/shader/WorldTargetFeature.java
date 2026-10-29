// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.shader;

import io.github.silentcall2598.focalis.compat.CompatibilityReport;
import io.github.silentcall2598.focalis.feature.Availability;
import io.github.silentcall2598.focalis.feature.Feature;
import io.github.silentcall2598.focalis.feature.FeatureContext;
import io.github.silentcall2598.focalis.render.lifecycle.RenderPhase;
import io.github.silentcall2598.focalis.render.lifecycle.RenderStage;
import io.github.silentcall2598.focalis.render.target.WorldTargetMonitor;
import io.github.silentcall2598.focalis.shader.session.SessionTarget;
import io.github.silentcall2598.focalis.shader.session.ShaderSession;

public final class WorldTargetFeature extends Feature {

    public static final String ID = "world_target";

    private final ShaderSession session;
    private final WorldTargetMonitor monitor;

    public WorldTargetFeature(ShaderSession session, WorldTargetMonitor monitor) {
        super(ID, "Experimental. Draws the world into a Focalis framebuffer and copies it back, which looks the"
                + " same as vanilla. Groundwork for shaderpack rendering.", false);
        this.session = session;
        this.monitor = monitor;
    }

    @Override
    protected Availability checkAvailability(CompatibilityReport compat) {
        if (compat.isOptiFinePresent()) {
            return Availability.unavailable("OptiFine is installed and changes how the world is rendered");
        }
        return Availability.AVAILABLE;
    }

    @Override
    protected void setup(FeatureContext context) {
        SessionTarget target = session.createTarget(monitor, context.logger());
        context.addRenderListener(RenderStage.WORLD, (stage, phase, drawKind, partialTicks) -> target.world(phase));
        context.addRenderListener(RenderStage.FRAME, (stage, phase, drawKind, partialTicks) -> {
            if (phase == RenderPhase.END) {
                target.frameEnd();
            }
        });
    }

    @Override
    protected void cleanup() {
        session.releaseTarget();
    }
}
