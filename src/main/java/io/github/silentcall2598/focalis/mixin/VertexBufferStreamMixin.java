// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.mixin;

import io.github.silentcall2598.focalis.shader.terrain.BufferStream;
import io.github.silentcall2598.focalis.shader.terrain.StreamVertexBuffer;
import io.github.silentcall2598.focalis.shader.terrain.TerrainStreams;
import net.minecraft.client.renderer.vertex.VertexBuffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import javax.annotation.Nullable;
import java.nio.ByteBuffer;

// A chunk layer's vertex buffer keeps its stream buffer, which only counts while it belongs to the data vanilla
// uploaded last and goes away with the vertex buffer.
@Mixin(VertexBuffer.class)
public abstract class VertexBufferStreamMixin implements StreamVertexBuffer {

    @Shadow
    private int glBufferId;

    @Shadow
    private int count;

    @Unique
    @Nullable
    private BufferStream focalis$stream;

    @Nullable
    @Override
    public BufferStream focalis$stream() {
        return focalis$stream;
    }

    @Override
    public void focalis$stream(@Nullable BufferStream stream) {
        focalis$stream = stream;
    }

    @Override
    public int focalis$glBuffer() {
        return glBufferId;
    }

    @Override
    public int focalis$vertexCount() {
        return count;
    }

    @Inject(method = "bufferData(Ljava/nio/ByteBuffer;)V", at = @At("HEAD"), require = 0, expect = 1)
    private void focalis$dataReplaced(ByteBuffer data, CallbackInfo ci) {
        if (focalis$stream != null) {
            TerrainStreams.vertexDataReplaced((VertexBuffer) (Object) this);
        }
    }

    @Inject(method = "deleteGlBuffers()V", at = @At("TAIL"), require = 0, expect = 1)
    private void focalis$deleted(CallbackInfo ci) {
        if (focalis$stream != null) {
            TerrainStreams.vertexBufferDeleted((VertexBuffer) (Object) this);
        }
    }
}
