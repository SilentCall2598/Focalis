// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.shader.pack;

import java.util.Objects;

/**
 * What the user picked for a pack, as written. A profile name from {@code shaders.properties}, empty for none, and
 * option entries in the profile syntax like {@code SHADOW_QUALITY=3 BLOOM !SSAO}. Immutable.
 */
public final class PackSettings {

    public static final PackSettings NONE = new PackSettings("", "");

    private final String profile;
    private final String options;

    private PackSettings(String profile, String options) {
        this.profile = profile;
        this.options = options;
    }

    public static PackSettings of(String profile, String options) {
        return new PackSettings(profile.trim(), options.trim());
    }

    public String profile() {
        return profile;
    }

    public String options() {
        return options;
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof PackSettings)) {
            return false;
        }
        PackSettings settings = (PackSettings) other;
        return profile.equals(settings.profile) && options.equals(settings.options);
    }

    @Override
    public int hashCode() {
        return Objects.hash(profile, options);
    }

    @Override
    public String toString() {
        return "profile '" + profile + "', options '" + options + "'";
    }
}
