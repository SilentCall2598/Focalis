// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.shader;

import io.github.silentcall2598.focalis.mixin.EntityRendererFogAccessor;
import io.github.silentcall2598.focalis.render.state.FixedFunctionFog;
import io.github.silentcall2598.focalis.shader.program.FogSource;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.EntityRenderer;

// The fog mode, start, end and density come from GlStateManager's cache. The color only exists in EntityRenderer,
// which hands it to GL every time setupFog runs, so it's what GL holds whenever a stage starts.
final class MinecraftFog implements FogSource {

    @Override
    public boolean available() {
        return FixedFunctionFog.available()
                && Minecraft.getMinecraft().entityRenderer instanceof EntityRendererFogAccessor;
    }

    @Override
    public boolean enabled() {
        return FixedFunctionFog.enabled();
    }

    @Override
    public int mode() {
        return FixedFunctionFog.mode();
    }

    @Override
    public float start() {
        return FixedFunctionFog.start();
    }

    @Override
    public float end() {
        return FixedFunctionFog.end();
    }

    @Override
    public float density() {
        return FixedFunctionFog.density();
    }

    @Override
    public float red() {
        return renderer().focalis$fogColorRed();
    }

    @Override
    public float green() {
        return renderer().focalis$fogColorGreen();
    }

    @Override
    public float blue() {
        return renderer().focalis$fogColorBlue();
    }

    // Some mods replace the EntityRenderer with their own subclass, so it's looked up each time.
    private static EntityRendererFogAccessor renderer() {
        EntityRenderer renderer = Minecraft.getMinecraft().entityRenderer;
        return (EntityRendererFogAccessor) renderer;
    }
}
