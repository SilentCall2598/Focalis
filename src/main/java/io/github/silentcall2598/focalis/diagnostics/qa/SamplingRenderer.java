// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.diagnostics.qa;

import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.tileentity.TileEntity;

/**
 * QA only. Runs a check right before a block entity draws with the renderer it replaced, and another step as the
 * last thing before returning.
 */
final class SamplingRenderer<T extends TileEntity> extends TileEntitySpecialRenderer<T> {

    private final TileEntitySpecialRenderer<T> original;
    private final Runnable before;
    private final Runnable after;

    SamplingRenderer(TileEntitySpecialRenderer<T> original, Runnable before, Runnable after) {
        this.original = original;
        this.before = before;
        this.after = after;
    }

    @Override
    public void render(T te, double x, double y, double z, float partialTicks, int destroyStage, float alpha) {
        before.run();
        original.render(te, x, y, z, partialTicks, destroyStage, alpha);
        after.run();
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
