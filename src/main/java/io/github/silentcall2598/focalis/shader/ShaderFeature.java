// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.shader;

import io.github.silentcall2598.focalis.compat.CompatibilityReport;
import io.github.silentcall2598.focalis.feature.Availability;
import io.github.silentcall2598.focalis.feature.Feature;
import io.github.silentcall2598.focalis.feature.FeatureContext;
import io.github.silentcall2598.focalis.render.lifecycle.RenderPhase;
import io.github.silentcall2598.focalis.render.lifecycle.RenderStage;
import io.github.silentcall2598.focalis.shader.post.PostPassMonitor;
import io.github.silentcall2598.focalis.shader.session.SessionPost;
import io.github.silentcall2598.focalis.shader.session.ShaderSession;

public final class ShaderFeature extends Feature {

    public static final String ID = "shaders";

    private final ShaderSession session;
    private final PostPassMonitor monitor;

    public ShaderFeature(ShaderSession session, PostPassMonitor monitor) {
        super(ID, "Experimental. Runs a Focalis test program from the shaderpack over the world image."
                + " Regular shaderpacks are not supported yet.", false);
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
        SessionPost post = session.createPost(monitor, context.logger());
        if (post == null) {
            return;
        }
        context.addRenderListener(RenderStage.WORLD, (stage, phase, drawKind, partialTicks) -> {
            if (phase == RenderPhase.END) {
                post.worldEnd();
            }
        });
    }

    @Override
    protected void cleanup() {
        session.releasePost();
    }
}
