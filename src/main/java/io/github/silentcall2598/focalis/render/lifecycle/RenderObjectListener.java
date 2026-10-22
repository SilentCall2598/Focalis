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

    /**
     * A block entity renderer starts, or with null the batch of Forge's fast block entity renderers is drawn.
     * {@code damaged} when the renderer draws the block entity's breaking overlay instead of the block entity.
     */
    void blockEntityStarted(@Nullable TileEntity blockEntity, boolean damaged);

    void objectEnded();

    /**
     * A special draw of the innermost object starts, which is {@link RenderDrawKind#ARMOR_GLINT},
     * {@link RenderDrawKind#ITEM_GLINT}, {@link RenderDrawKind#EYES} or {@link RenderDrawKind#BEACON_BEAM}. Also
     * outside any object, like an item in a screen. A special draw whose code throws gets no end, see
     * {@link #specialDrawsAbandoned}.
     */
    void specialDrawStarted(RenderDrawKind kind);

    /** The special draw of that kind returned. */
    void specialDrawEnded(RenderDrawKind kind);

    /**
     * A living entity renderer caught a failure of its model or layers and draws on, so any special draw started inside
     * it since the innermost object started is over.
     */
    void specialDrawsAbandoned();

    /**
     * Vanilla set the color it blends the innermost living entity with, like red while hurt, as the four values of
     * its constant color. All zero when it stopped blending.
     */
    void objectColor(float red, float green, float blue, float alpha);
}
