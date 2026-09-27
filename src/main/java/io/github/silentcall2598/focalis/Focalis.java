// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis;

import io.github.silentcall2598.focalis.core.FocalisCore;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;

@Mod(
        modid = Focalis.MOD_ID,
        name = Focalis.MOD_NAME,
        version = Focalis.VERSION,
        clientSideOnly = true,
        acceptedMinecraftVersions = "[1.12.2]",
        // Lets clients with Focalis join servers that don't have it.
        acceptableRemoteVersions = "*",
        // 2847 is the Forge build in the RetroFuturaGradle dev environment. 2860 is still the release target.
        // MixinBooter 10.7 is the version Focalis is built and tested against.
        dependencies = "required-after:forge@[14.23.5.2847,);required-after:mixinbooter@[10.7,)"
)
public final class Focalis {

    public static final String MOD_ID = "focalis";
    public static final String MOD_NAME = "Focalis";
    public static final String VERSION = Tags.VERSION;

    private final FocalisCore core = new FocalisCore();

    @Mod.EventHandler
    public void onPreInit(FMLPreInitializationEvent event) {
        core.preInit(event.getSuggestedConfigurationFile());
    }
}
