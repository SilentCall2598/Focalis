// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.silentcall2598.focalis.shader.terrain.StreamBufferBuilder;
import io.github.silentcall2598.focalis.shader.terrain.StreamCompanion;
import io.github.silentcall2598.focalis.shader.terrain.TerrainStreams;
import net.minecraft.client.renderer.BufferBuilder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import javax.annotation.Nullable;
import java.util.Comparator;

// Gives every builder a place for its stream companion, and lets the stream observe the order vanilla's translucent
// sort puts its quad indices in. The sort and the data it moves stay untouched.
@Mixin(BufferBuilder.class)
public abstract class BufferBuilderStreamMixin implements StreamBufferBuilder {

    @Unique
    @Nullable
    private StreamCompanion focalis$companion;

    @Nullable
    @Override
    public StreamCompanion focalis$streamCompanion() {
        return focalis$companion;
    }

    @Override
    public void focalis$streamCompanion(StreamCompanion companion) {
        focalis$companion = companion;
    }

    @WrapOperation(method = "sortVertexData(FFF)V", at = @At(value = "INVOKE",
            target = "Ljava/util/Arrays;sort([Ljava/lang/Object;Ljava/util/Comparator;)V"), require = 0, expect = 1)
    private void focalis$sorted(Object[] indices, Comparator<?> comparator, Operation<Void> original) {
        original.call(indices, comparator);
        if (focalis$companion != null) {
            TerrainStreams.sortObserved((BufferBuilder) (Object) this, indices);
        }
    }
}
