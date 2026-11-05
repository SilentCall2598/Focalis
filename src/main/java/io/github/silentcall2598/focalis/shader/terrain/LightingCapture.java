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
    // The Tessellator buffer an object renderer is building on the render thread. The hooks on chunk worker threads
    // read it too, and only compare it with their own buffers.
    @Nullable
    private static volatile BufferBuilder objectBuffer;
    // Kept after a release: a hook's failure belongs to the object feed when it came from this instance, which no chunk
    // build ever holds.
    @Nullable
    private static volatile VertexColors objectColors;

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
        if (companion != null) {
            return companion.colors();
        }
        if (buffer != objectBuffer) {
            return null;
        }
        VertexColors object = objectColors;
        return object != null && object.capturing() ? object : null;
    }

    static void captureObject(BufferBuilder buffer, VertexColors colors) {
        objectColors = colors;
        objectBuffer = buffer;
        bind(colors);
    }

    static boolean capturesObject(BufferBuilder buffer) {
        return buffer == objectBuffer;
    }

    static void releaseObject(@Nullable BufferBuilder buffer) {
        if (buffer != null && buffer == objectBuffer) {
            dropObject();
        }
    }

    static void dropObject() {
        objectBuffer = null;
        VertexColors object = objectColors;
        if (object != null && BOUND.get() == object) {
            BOUND.remove();
        }
    }

    private static void failed(@Nullable VertexColors colors, Throwable e) {
        if (colors != null && colors == objectColors) {
            TerrainStreams.objectCaptureFailed(e);
        } else {
            TerrainStreams.captureFailed(e);
        }
    }

    @Nullable
    static BufferBuilder objectBuffer() {
        return objectBuffer;
    }

    public static void forgeBase(@Nullable BufferBuilder buffer, float[] color, float tint, int multiplier) {
        if (buffer == null) {
            return;
        }
        VertexColors colors = null;
        try {
            colors = colors(buffer);
            if (colors != null) {
                colors.forgeBase(color, tint != -1, multiplier);
            }
        } catch (RuntimeException | LinkageError e) {
            failed(colors, e);
        }
    }

    public static void forgeAo(@Nullable BufferBuilder buffer, float ao) {
        if (buffer == null) {
            return;
        }
        VertexColors colors = null;
        try {
            colors = colors(buffer);
            if (colors != null) {
                colors.forgeAo(ao);
            }
        } catch (RuntimeException | LinkageError e) {
            failed(colors, e);
        }
    }

    public static void forgeDiffuse(@Nullable BufferBuilder buffer, float diffuse) {
        if (buffer == null) {
            return;
        }
        VertexColors colors = null;
        try {
            colors = colors(buffer);
            if (colors != null) {
                colors.forgeDiffuse(diffuse);
            }
        } catch (RuntimeException | LinkageError e) {
            failed(colors, e);
        }
    }

    public static void forgeColor(BufferBuilder buffer, int vertex, float[] color) {
        VertexColors colors = null;
        try {
            colors = colors(buffer);
            if (colors != null) {
                colors.forgeColor(buffer.getVertexCount() + vertex, color);
            }
        } catch (RuntimeException | LinkageError e) {
            failed(colors, e);
        }
    }

    public static void modelAo(float[] multipliers) {
        if (!enabled) {
            return;
        }
        VertexColors colors = null;
        try {
            colors = BOUND.get();
            // An object capture dropped from another thread leaves the render thread's binding behind.
            if (colors != null && colors.capturing() && (colors != objectColors || objectBuffer != null)) {
                colors.modelAo(multipliers);
            }
        } catch (RuntimeException | LinkageError e) {
            failed(colors, e);
        }
    }

    public static void modelQuad(BufferBuilder buffer, BakedQuad quad, boolean smooth) {
        VertexColors colors = null;
        try {
            colors = colors(buffer);
            if (colors != null) {
                colors.modelQuad(buffer.getVertexCount(), quad.getVertexData(), smooth);
            }
        } catch (RuntimeException | LinkageError e) {
            failed(colors, e);
        }
    }

    public static float modelDiffuse(BufferBuilder buffer, float diffuse) {
        VertexColors colors = null;
        try {
            colors = colors(buffer);
            if (colors != null) {
                colors.modelDiffuse(diffuse);
            }
        } catch (RuntimeException | LinkageError e) {
            failed(colors, e);
        }
        return diffuse;
    }

    public static int modelTint(BufferBuilder buffer, int multiplier) {
        VertexColors colors = null;
        try {
            colors = colors(buffer);
            if (colors != null) {
                colors.modelTint(multiplier);
            }
        } catch (RuntimeException | LinkageError e) {
            failed(colors, e);
        }
        return multiplier;
    }

    public static void modelVertex(BufferBuilder buffer, float red, float green, float blue, int vertexIndex) {
        VertexColors colors = null;
        try {
            colors = colors(buffer);
            if (colors != null) {
                int base = buffer.getByteBuffer().getInt(buffer.getColorIndex(vertexIndex) * 4);
                colors.modelVertex(buffer.getVertexCount() - vertexIndex, 4 - vertexIndex,
                        LITTLE_ENDIAN ? base : Integer.reverseBytes(base), red, green, blue);
            }
        } catch (RuntimeException | LinkageError e) {
            failed(colors, e);
        }
    }

    public static int fluidTint(BufferBuilder buffer, int multiplier) {
        VertexColors colors = null;
        try {
            colors = colors(buffer);
            if (colors != null) {
                colors.fluidTint(multiplier);
            }
        } catch (RuntimeException | LinkageError e) {
            failed(colors, e);
        }
        return multiplier;
    }

    public static void fluidVertex(BufferBuilder buffer, float red, float green, float blue, float alpha) {
        VertexColors colors = null;
        try {
            colors = colors(buffer);
            if (colors != null) {
                colors.fluidVertex(buffer.getVertexCount(), red, green, blue, alpha);
            }
        } catch (RuntimeException | LinkageError e) {
            failed(colors, e);
        }
    }
}
