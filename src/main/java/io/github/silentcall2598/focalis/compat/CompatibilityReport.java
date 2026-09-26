// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.compat;

import net.minecraftforge.fml.client.FMLClientHandler;
import net.minecraftforge.fml.common.Loader;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class CompatibilityReport {

    // Mods worth noting in the log when chasing rendering problems. Each id was checked against the @Mod
    // annotation in that mod's 1.12.2 source, and nothing is disabled because of this list.
    private static final List<String> RENDERING_RELATED_MOD_IDS = Collections.unmodifiableList(Arrays.asList(
            "betterfoliage", "ctm", "dynamiclights", "foamfix", "nothirium", "vanillafix"));

    private final Set<String> loadedModIds;
    private final boolean optiFinePresent;

    private CompatibilityReport(Set<String> loadedModIds, boolean optiFinePresent) {
        this.loadedModIds = loadedModIds;
        this.optiFinePresent = optiFinePresent;
    }

    // Only valid once FML has discovered every mod, so from pre-init on.
    public static CompatibilityReport scan() {
        // Forge already detects OptiFine at startup by looking for its Config class.
        return of(Loader.instance().getIndexedModList().keySet(), FMLClientHandler.instance().hasOptifine());
    }

    public static CompatibilityReport of(Collection<String> loadedModIds, boolean optiFinePresent) {
        return new CompatibilityReport(Collections.unmodifiableSet(new HashSet<>(loadedModIds)), optiFinePresent);
    }

    public boolean isModLoaded(String modId) {
        return loadedModIds.contains(modId);
    }

    public boolean isOptiFinePresent() {
        return optiFinePresent;
    }

    public List<String> renderingRelatedMods() {
        List<String> present = new ArrayList<>();
        for (String modId : RENDERING_RELATED_MOD_IDS) {
            if (loadedModIds.contains(modId)) {
                present.add(modId);
            }
        }
        return present;
    }
}
