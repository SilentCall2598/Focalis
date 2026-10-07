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
import io.github.silentcall2598.focalis.render.target.WorldTargetFeature;
import io.github.silentcall2598.focalis.render.target.WorldTargetMonitor;
import io.github.silentcall2598.focalis.shader.ShaderFeature;
import io.github.silentcall2598.focalis.shader.WorldProgramMonitor;
import io.github.silentcall2598.focalis.shader.WorldProgramsFeature;
import io.github.silentcall2598.focalis.shader.post.PostPassMonitor;
import net.minecraftforge.fml.common.FMLCommonHandler;

import javax.annotation.Nullable;
import java.io.File;

public final class FocalisCore {

    private final RenderSubsystem render;
    private final FeatureManager features;
    // Only set when a development launch asks for a probe.
    @Nullable
    private final DevelopmentProbe probe;
    private FocalisConfig config;

    public FocalisCore() {
        render = new RenderSubsystem(this::onGlContextReady);
        features = new FeatureManager(render.lifecycle());
        probe = DevelopmentProbe.fromSystemProperty(features::statuses, render::glContext);
        features.register(new FrameStatsFeature());
        // Before the shader feature, since listeners run in registration order and the post pass needs the world
        // copied back to Minecraft's framebuffer first.
        features.register(new WorldTargetFeature(
                probe == null ? WorldTargetMonitor.NONE : probe.worldTargetMonitor()));
        features.register(new WorldProgramsFeature(render::glContext,
                probe == null ? WorldProgramMonitor.NONE : probe.worldProgramMonitor()));
        PostPassMonitor postPassMonitor = probe == null ? PostPassMonitor.NONE : probe.postPassMonitor();
        features.register(new ShaderFeature(render::glContext, postPassMonitor));
    }

    public void preInit(File configFile) {
        CompatibilityReport compat = CompatibilityReport.scan();
        EnvironmentReport.logStartup(compat);

        config = FocalisConfig.load(configFile);
        // Register this before feature setup so a crash during setup still shows feature states.
        FMLCommonHandler.instance().registerCrashCallable(new FocalisCrashSection(features));

        // Hooks first, so features see which render stages are dispatched when they register listeners.
        render.install();
        // Listeners run in registration order, so this puts the probe's listeners on both sides of the features' work.
        if (probe != null) {
            probe.installBefore(render.lifecycle());
        }
        features.initialize(config, compat);
        if (probe != null) {
            probe.installAfter(render.lifecycle());
        }
        config.saveIfChanged();
    }

    private void onGlContextReady(GlContextInfo glContext) {
        OpenGlReport.log(glContext, config.logGlExtensions());
    }
}
