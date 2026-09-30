// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.diagnostics.qa;

import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.tileentity.TileEntity;

/** QA only. Runs a check right before a block entity draws, then draws it with the renderer it replaced. */
final class SamplingRenderer<T extends TileEntity> extends TileEntitySpecialRenderer<T> {

    private final TileEntitySpecialRenderer<T> original;
    private final Runnable sample;

    SamplingRenderer(TileEntitySpecialRenderer<T> original, Runnable sample) {
        this.original = original;
        this.sample = sample;
    }

    @Override
    public void render(T te, double x, double y, double z, float partialTicks, int destroyStage, float alpha) {
        sample.run();
        original.render(te, x, y, z, partialTicks, destroyStage, alpha);
    }

    @Override
    public boolean isGlobalRenderer(T te) {
        return original.isGlobalRenderer(te);
    }

    @Override
    public void setRendererDispatcher(TileEntityRendererDispatcher dispatcher) {
        super.setRendererDispatcher(dispatcher);
        original.setRendererDispatcher(dispatcher);
    }
}
