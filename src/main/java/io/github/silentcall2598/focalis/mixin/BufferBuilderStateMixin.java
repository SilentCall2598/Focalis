// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.mixin;

import io.github.silentcall2598.focalis.shader.terrain.SortedIds;
import io.github.silentcall2598.focalis.shader.terrain.StreamVertexState;
import net.minecraft.client.renderer.BufferBuilder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import javax.annotation.Nullable;

// The sorted translucent data vanilla saves per chunk carries the stream's IDs in the same order, so they can't come
// apart from it.
@Mixin(BufferBuilder.State.class)
public abstract class BufferBuilderStateMixin implements StreamVertexState {

    @Unique
    @Nullable
    private SortedIds focalis$sortedIds;

    @Nullable
    @Override
    public SortedIds focalis$sortedIds() {
        return focalis$sortedIds;
    }

    @Override
    public void focalis$sortedIds(SortedIds ids) {
        focalis$sortedIds = ids;
    }
}
