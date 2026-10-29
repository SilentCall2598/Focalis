// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.shader.terrain;

import net.minecraft.client.renderer.BufferBuilder;

import javax.annotation.Nullable;

public interface LighterLink {

    @Nullable
    BufferBuilder focalis$builder();

    void focalis$builder(@Nullable BufferBuilder builder);

    static void link(Object lighter, @Nullable BufferBuilder builder) {
        if (lighter instanceof LighterLink) {
            ((LighterLink) lighter).focalis$builder(builder);
        }
    }

    @Nullable
    static BufferBuilder builder(Object lighter) {
        return lighter instanceof LighterLink ? ((LighterLink) lighter).focalis$builder() : null;
    }
}
