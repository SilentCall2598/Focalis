// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.core;

import io.github.silentcall2598.focalis.compat.CompatibilityReport;
import io.github.silentcall2598.focalis.config.FocalisConfig;
import io.github.silentcall2598.focalis.diagnostics.EnvironmentReport;
import io.github.silentcall2598.focalis.diagnostics.FocalisCrashSection;
import io.github.silentcall2598.focalis.diagnostics.FrameStatsFeature;
import io.github.silentcall2598.focalis.diagnostics.OpenGlReport;
import io.github.silentcall2598.focalis.feature.FeatureManager;
import io.github.silentcall2598.focalis.render.RenderSubsystem;
import io.github.silentcall2598.focalis.render.state.GlContextInfo;
import net.minecraftforge.fml.common.FMLCommonHandler;

import java.io.File;

public final class FocalisCore {

    private final RenderSubsystem render;
    private final FeatureManager features;
    private FocalisConfig config;

    public FocalisCore() {
        render = new RenderSubsystem(this::onGlContextReady);
        features = new FeatureManager(render.lifecycle());
        features.register(new FrameStatsFeature());
    }

    public void preInit(File configFile) {
        CompatibilityReport compat = CompatibilityReport.scan();
        EnvironmentReport.logStartup(compat);

        config = FocalisConfig.load(configFile);
        // Register this before feature setup so a crash during setup still shows feature states.
        FMLCommonHandler.instance().registerCrashCallable(new FocalisCrashSection(features));

        // Hooks first, so features see which render stages are dispatched when they register listeners.
        render.install();
        features.initialize(config, compat);
        config.saveIfChanged();
    }

    private void onGlContextReady(GlContextInfo glContext) {
        OpenGlReport.log(glContext, config.logGlExtensions());
    }
}
