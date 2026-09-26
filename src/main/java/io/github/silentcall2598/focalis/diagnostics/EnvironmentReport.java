// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.diagnostics;

import io.github.silentcall2598.focalis.Focalis;
import io.github.silentcall2598.focalis.compat.CompatibilityReport;
import io.github.silentcall2598.focalis.core.FocalisLog;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraftforge.common.ForgeVersion;
import net.minecraftforge.fml.common.Loader;
import org.apache.logging.log4j.Logger;
import org.lwjgl.Sys;

import java.util.List;

public final class EnvironmentReport {

    private EnvironmentReport() {
    }

    public static void logStartup(CompatibilityReport compat) {
        Logger log = FocalisLog.LOGGER;
        log.info("{} {} starting on Minecraft {} with Forge {}",
                Focalis.MOD_NAME, Focalis.VERSION, ForgeVersion.mcVersion, ForgeVersion.getVersion());
        log.info("Java {} ({}, {}), max heap {} MiB, LWJGL {}",
                System.getProperty("java.version"), System.getProperty("java.vendor"),
                System.getProperty("java.vm.name"), Runtime.getRuntime().maxMemory() / (1024 * 1024), Sys.getVersion());
        log.info("{} {} ({}), CPU: {}",
                System.getProperty("os.name"), System.getProperty("os.version"), System.getProperty("os.arch"),
                OpenGlHelper.getCpu());
        log.info("{} mods active", Loader.instance().getActiveModList().size());

        if (compat.isOptiFinePresent()) {
            log.info("OptiFine is installed");
        }
        List<String> renderingMods = compat.renderingRelatedMods();
        if (!renderingMods.isEmpty()) {
            log.info("Rendering-related mods present: {}", String.join(", ", renderingMods));
        }
    }
}
