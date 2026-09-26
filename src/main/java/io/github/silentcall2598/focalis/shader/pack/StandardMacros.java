// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.shader.pack;

/** Macros Focalis defines for every pack, independent of the pack's own options. */
public final class StandardMacros {

    /** Minecraft 1.12.2 in the format packs compare against, where 1.13 is 11300. */
    public static final int MC_VERSION = 11202;

    private StandardMacros() {
    }

    // IS_IRIS and IRIS_FEATURE_* are left out on purpose. Packs use them to switch to code paths that need
    // Iris features Focalis doesn't have.
    public static ShaderMacros environment() {
        return ShaderMacros.empty().with("MC_VERSION", Integer.toString(MC_VERSION));
    }
}
