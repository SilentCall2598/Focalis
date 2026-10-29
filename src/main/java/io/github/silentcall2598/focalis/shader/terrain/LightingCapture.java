// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.shader.terrain;

import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.block.model.BakedQuad;

import javax.annotation.Nullable;
import java.nio.ByteOrder;

public final class LightingCapture {

    private static final boolean LITTLE_ENDIAN = ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN;

    private static volatile boolean enabled;
    private static final ThreadLocal<VertexColors> BOUND = new ThreadLocal<>();

    private LightingCapture() {
    }

    static void enabled(boolean on) {
        enabled = on;
    }

    public static boolean enabled() {
        return enabled;
    }

    static void bind(@Nullable VertexColors colors) {
        if (colors != null) {
            if (BOUND.get() != colors) {
                BOUND.set(colors);
            }
        } else if (enabled || BOUND.get() != null) {
            BOUND.remove();
        }
    }

    @Nullable
    static VertexColors bound() {
        return BOUND.get();
    }

    @Nullable
    private static VertexColors colors(BufferBuilder buffer) {
        if (!enabled || !(buffer instanceof StreamBufferBuilder)) {
            return null;
        }
        StreamCompanion companion = ((StreamBufferBuilder) buffer).focalis$streamCompanion();
        return companion == null ? null : companion.colors();
    }

    public static void forgeBase(@Nullable BufferBuilder buffer, float[] color, float tint, int multiplier) {
        if (buffer == null) {
            return;
        }
        try {
            VertexColors colors = colors(buffer);
            if (colors != null) {
                colors.forgeBase(color, tint != -1, multiplier);
            }
        } catch (RuntimeException | LinkageError e) {
            TerrainStreams.captureFailed(e);
        }
    }

    public static void forgeAo(@Nullable BufferBuilder buffer, float ao) {
        if (buffer == null) {
            return;
        }
        try {
            VertexColors colors = colors(buffer);
            if (colors != null) {
                colors.forgeAo(ao);
            }
        } catch (RuntimeException | LinkageError e) {
            TerrainStreams.captureFailed(e);
        }
    }

    public static void forgeDiffuse(@Nullable BufferBuilder buffer, float diffuse) {
        if (buffer == null) {
            return;
        }
        try {
            VertexColors colors = colors(buffer);
            if (colors != null) {
                colors.forgeDiffuse(diffuse);
            }
        } catch (RuntimeException | LinkageError e) {
            TerrainStreams.captureFailed(e);
        }
    }

    public static void forgeColor(BufferBuilder buffer, int vertex, float[] color) {
        try {
            VertexColors colors = colors(buffer);
            if (colors != null) {
                colors.forgeColor(buffer.getVertexCount() + vertex, color);
            }
        } catch (RuntimeException | LinkageError e) {
            TerrainStreams.captureFailed(e);
        }
    }

    public static void modelAo(float[] multipliers) {
        if (!enabled) {
            return;
        }
        try {
            VertexColors colors = BOUND.get();
            if (colors != null && colors.capturing()) {
                colors.modelAo(multipliers);
            }
        } catch (RuntimeException | LinkageError e) {
            TerrainStreams.captureFailed(e);
        }
    }

    public static void modelQuad(BufferBuilder buffer, BakedQuad quad, boolean smooth) {
        try {
            VertexColors colors = colors(buffer);
            if (colors != null) {
                colors.modelQuad(buffer.getVertexCount(), quad.getVertexData(), smooth);
            }
        } catch (RuntimeException | LinkageError e) {
            TerrainStreams.captureFailed(e);
        }
    }

    public static float modelDiffuse(BufferBuilder buffer, float diffuse) {
        try {
            VertexColors colors = colors(buffer);
            if (colors != null) {
                colors.modelDiffuse(diffuse);
            }
        } catch (RuntimeException | LinkageError e) {
            TerrainStreams.captureFailed(e);
        }
        return diffuse;
    }

    public static int modelTint(BufferBuilder buffer, int multiplier) {
        try {
            VertexColors colors = colors(buffer);
            if (colors != null) {
                colors.modelTint(multiplier);
            }
        } catch (RuntimeException | LinkageError e) {
            TerrainStreams.captureFailed(e);
        }
        return multiplier;
    }

    public static void modelVertex(BufferBuilder buffer, float red, float green, float blue, int vertexIndex) {
        try {
            VertexColors colors = colors(buffer);
            if (colors != null) {
                int base = buffer.getByteBuffer().getInt(buffer.getColorIndex(vertexIndex) * 4);
                colors.modelVertex(buffer.getVertexCount() - vertexIndex, 4 - vertexIndex,
                        LITTLE_ENDIAN ? base : Integer.reverseBytes(base), red, green, blue);
            }
        } catch (RuntimeException | LinkageError e) {
            TerrainStreams.captureFailed(e);
        }
    }

    public static int fluidTint(BufferBuilder buffer, int multiplier) {
        try {
            VertexColors colors = colors(buffer);
            if (colors != null) {
                colors.fluidTint(multiplier);
            }
        } catch (RuntimeException | LinkageError e) {
            TerrainStreams.captureFailed(e);
        }
        return multiplier;
    }

    public static void fluidVertex(BufferBuilder buffer, float red, float green, float blue, float alpha) {
        try {
            VertexColors colors = colors(buffer);
            if (colors != null) {
                colors.fluidVertex(buffer.getVertexCount(), red, green, blue, alpha);
            }
        } catch (RuntimeException | LinkageError e) {
            TerrainStreams.captureFailed(e);
        }
    }
}
