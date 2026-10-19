// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.render.lifecycle;

import net.minecraft.entity.Entity;
import net.minecraft.tileentity.TileEntity;

import javax.annotation.Nullable;

/**
 * Hears about each entity and block entity renderer starting and returning, nested ones included, wherever they draw.
 * Every start is followed by exactly one {@link #objectEnded}, innermost first, unless a renderer throws. Runs on the
 * client thread with the GL context current, once per object on a hot path, so it has to be cheap and must not
 * allocate.
 */
public interface RenderObjectListener {

    void entityStarted(Entity entity);

    /** A block entity renderer starts, or with null the batch of Forge's fast block entity renderers is drawn. */
    void blockEntityStarted(@Nullable TileEntity blockEntity);

    void objectEnded();

    /**
     * Vanilla set the color it blends the innermost living entity with, like red while hurt, as the four values of
     * its constant color. All zero when it stopped blending.
     */
    void objectColor(float red, float green, float blue, float alpha);
}
