// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.diagnostics;

import io.github.silentcall2598.focalis.Focalis;
import io.github.silentcall2598.focalis.feature.FeatureManager;
import net.minecraftforge.fml.common.ICrashCallable;

// Adds a Focalis line with the version and feature states to the System Details of crash reports.
public final class FocalisCrashSection implements ICrashCallable {

    private final FeatureManager features;

    public FocalisCrashSection(FeatureManager features) {
        this.features = features;
    }

    @Override
    public String getLabel() {
        return Focalis.MOD_NAME;
    }

    @Override
    public String call() {
        return Focalis.VERSION + "; features: " + features.statuses();
    }
}
