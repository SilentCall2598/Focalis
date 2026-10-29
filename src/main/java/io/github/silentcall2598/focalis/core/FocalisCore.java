// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.core;

import io.github.silentcall2598.focalis.compat.CompatibilityReport;
import io.github.silentcall2598.focalis.config.FocalisConfig;
import io.github.silentcall2598.focalis.diagnostics.DevelopmentProbe;
import io.github.silentcall2598.focalis.diagnostics.EnvironmentReport;
import io.github.silentcall2598.focalis.diagnostics.FocalisCrashSection;
import io.github.silentcall2598.focalis.diagnostics.FrameStatsFeature;
import io.github.silentcall2598.focalis.diagnostics.OpenGlReport;
import io.github.silentcall2598.focalis.feature.FeatureManager;
import io.github.silentcall2598.focalis.render.RenderSubsystem;
import io.github.silentcall2598.focalis.render.state.GlContextInfo;
import io.github.silentcall2598.focalis.render.target.WorldTargetMonitor;
import io.github.silentcall2598.focalis.shader.ShaderFeature;
import io.github.silentcall2598.focalis.shader.WorldProgramMonitor;
import io.github.silentcall2598.focalis.shader.WorldProgramsFeature;
import io.github.silentcall2598.focalis.shader.WorldTargetFeature;
import io.github.silentcall2598.focalis.shader.post.PostPassMonitor;
import io.github.silentcall2598.focalis.shader.session.ShaderSession;
import io.github.silentcall2598.focalis.shader.terrain.TerrainStreams;
import net.minecraftforge.fml.common.FMLCommonHandler;

import javax.annotation.Nullable;
import java.io.File;

public final class FocalisCore {

    private final RenderSubsystem render;
    private final FeatureManager features;
    private final ShaderSession session;
    @Nullable
    private final DevelopmentProbe probe;
    private FocalisConfig config;

    public FocalisCore() {
        render = new RenderSubsystem(this::onGlContextReady);
        features = new FeatureManager(render.lifecycle());
        session = new ShaderSession(render::glContext);
        probe = DevelopmentProbe.fromSystemProperty(features::statuses, render::glContext);
        features.register(new FrameStatsFeature());
        // In the shader session's order, since listeners run in registration order: the world draws into the target,
        // the programs bind inside it, and the post pass needs the world copied back to Minecraft's framebuffer.
        features.register(new WorldTargetFeature(session,
                probe == null ? WorldTargetMonitor.NONE : probe.worldTargetMonitor()));
        features.register(new WorldProgramsFeature(session,
                probe == null ? WorldProgramMonitor.NONE : probe.worldProgramMonitor()));
        features.register(new ShaderFeature(session,
                probe == null ? PostPassMonitor.NONE : probe.postPassMonitor()));
    }

    public void preInit(File configFile) {
        CompatibilityReport compat = CompatibilityReport.scan();
        EnvironmentReport.logStartup(compat);

        config = FocalisConfig.load(configFile);
        FMLCommonHandler.instance().registerCrashCallable(new FocalisCrashSection(features));
        session.loadConfig(config);

        // Hooks first, so features see which render stages are dispatched when they register listeners.
        render.install();
        // Its renderer reload has to work even after the feature that turned the stream on failed.
        TerrainStreams.install(render.lifecycle());
        if (probe != null) {
            probe.installBefore(render.lifecycle());
        }
        features.initialize(config, compat);
        session.seal();
        if (probe != null) {
            probe.installAfter(render.lifecycle());
        }
        config.saveIfChanged();
    }

    private void onGlContextReady(GlContextInfo glContext) {
        OpenGlReport.log(glContext, config.logGlExtensions());
    }
}
