// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.core;

import io.github.silentcall2598.focalis.compat.CompatibilityReport;
import io.github.silentcall2598.focalis.config.FocalisConfig;
import io.github.silentcall2598.focalis.diagnostics.EnvironmentReport;
import io.github.silentcall2598.focalis.diagnostics.FocalisCrashSection;
import io.github.silentcall2598.focalis.diagnostics.FrameStatsFeature;
import io.github.silentcall2598.focalis.diagnostics.OpenGlReport;
import io.github.silentcall2598.focalis.diagnostics.qa.QaProbe;
import io.github.silentcall2598.focalis.diagnostics.qa.QaSettings;
import io.github.silentcall2598.focalis.feature.FeatureManager;
import io.github.silentcall2598.focalis.render.RenderSubsystem;
import io.github.silentcall2598.focalis.render.state.GlContextInfo;
import io.github.silentcall2598.focalis.render.target.WorldTargetFeature;
import io.github.silentcall2598.focalis.render.target.WorldTargetMonitor;
import io.github.silentcall2598.focalis.shader.ShaderFeature;
import io.github.silentcall2598.focalis.shader.post.PostPassMonitor;
import net.minecraftforge.fml.common.FMLCommonHandler;

import javax.annotation.Nullable;
import java.io.File;

public final class FocalisCore {

    private final RenderSubsystem render;
    private final FeatureManager features;
    // Only set in development QA runs from tools/qa.
    @Nullable
    private final QaProbe qa;
    private FocalisConfig config;

    public FocalisCore() {
        render = new RenderSubsystem(this::onGlContextReady);
        features = new FeatureManager(render.lifecycle());
        QaSettings qaSettings = QaSettings.fromSystemProperties();
        qa = qaSettings == null ? null : new QaProbe(qaSettings, features::statuses, render::glContext);
        features.register(new FrameStatsFeature());
        // Before the shader feature, since listeners run in registration order and the post pass needs the world
        // copied back to Minecraft's framebuffer first.
        features.register(new WorldTargetFeature(qa == null ? WorldTargetMonitor.NONE : qa.worldTargetMonitor()));
        PostPassMonitor postPassMonitor = qa == null ? PostPassMonitor.NONE : qa.postPassMonitor();
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
        // Listeners run in registration order, so this puts the QA checks on both sides of the features' work.
        if (qa != null) {
            qa.installBefore(render.lifecycle());
        }
        features.initialize(config, compat);
        if (qa != null) {
            qa.installAfter(render.lifecycle());
        }
        config.saveIfChanged();
    }

    private void onGlContextReady(GlContextInfo glContext) {
        OpenGlReport.log(glContext, config.logGlExtensions());
    }
}
