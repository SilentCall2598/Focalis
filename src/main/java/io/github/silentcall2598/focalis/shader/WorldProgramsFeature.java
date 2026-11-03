// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.shader;

import io.github.silentcall2598.focalis.compat.CompatibilityReport;
import io.github.silentcall2598.focalis.feature.Availability;
import io.github.silentcall2598.focalis.feature.Feature;
import io.github.silentcall2598.focalis.feature.FeatureContext;
import io.github.silentcall2598.focalis.render.lifecycle.RenderStage;
import io.github.silentcall2598.focalis.shader.program.WorldProgramBinding;
import io.github.silentcall2598.focalis.shader.session.SessionPrograms;
import io.github.silentcall2598.focalis.shader.session.ShaderSession;

public final class WorldProgramsFeature extends Feature {

    public static final String ID = "world_programs";

    private final ShaderSession session;
    private final WorldProgramMonitor monitor;

    public WorldProgramsFeature(ShaderSession session, WorldProgramMonitor monitor) {
        super(ID, "Experimental. Binds the world programs of the shaderpack while the world draws. Only the texture"
                + " and lightmap samplers, the view size and frame timing uniforms, the camera position and matrices,"
                + " the time, sun and moon, rain, sky and fog, eye brightness and altitude, water or lava, near and"
                + " far, night vision, blindness, screen brightness and atlas size uniforms are set, along with the"
                + " pack's custom uniforms that only use those, its gbuffers custom and noise textures, its alpha test"
                + " and blend directives, and the normal, block ID, texture midpoint and tangent attributes of chunk"
                + " terrain. No composite passes run. For testing only,"
                + " regular shaderpacks are not supported yet.", false);
        this.session = session;
        this.monitor = monitor;
    }

    @Override
    protected Availability checkAvailability(CompatibilityReport compat) {
        if (compat.isOptiFinePresent()) {
            return Availability.unavailable("OptiFine is installed and has its own shader support");
        }
        return Availability.AVAILABLE;
    }

    @Override
    protected void setup(FeatureContext context) {
        SessionPrograms programs = session.createPrograms(monitor, context.logger());
        if (programs == null) {
            return;
        }
        context.addRenderListener(RenderStage.WORLD, (stage, phase, drawKind, partialTicks) -> programs.world(phase));
        context.addRenderListener(RenderStage.FRAME, (stage, phase, drawKind, partialTicks) -> programs.frame(phase));
        context.addRenderListener(RenderStage.CAMERA,
                (stage, phase, drawKind, partialTicks) -> programs.camera(phase, drawKind, partialTicks));
        for (RenderStage stage : WorldProgramBinding.STAGES) {
            context.addRenderListener(stage, (bound, phase, drawKind, partialTicks) -> programs.stage(bound, phase,
                    drawKind));
        }
        for (RenderStage stage : WorldProgramBinding.VANILLA_PROGRAM_STAGES) {
            context.addRenderListener(stage, (bound, phase, drawKind, partialTicks) -> programs.vanillaPrograms(bound,
                    phase, drawKind));
        }
        context.addCheckpointListener(checkpoint -> programs.rendererReturned());
        context.addObjectListener(programs.objects());
    }

    @Override
    protected void cleanup() {
        session.releasePrograms();
    }
}
