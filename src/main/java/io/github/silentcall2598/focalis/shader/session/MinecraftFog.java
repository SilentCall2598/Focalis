// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.shader.session;

import io.github.silentcall2598.focalis.mixin.EntityRendererFogAccessor;
import io.github.silentcall2598.focalis.render.state.FixedFunctionFog;
import io.github.silentcall2598.focalis.shader.program.FogSource;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.EntityRenderer;

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

    private static EntityRendererFogAccessor renderer() {
        EntityRenderer renderer = Minecraft.getMinecraft().entityRenderer;
        return (EntityRendererFogAccessor) renderer;
    }
}
