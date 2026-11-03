// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.shader.terrain;

/**
 * Each path repeats its renderer's float operations in the renderer's order and only leaves factors out, so a kept
 * factor rounds exactly as in vanilla's own color, and with every factor kept the result is vanilla's color. Colors
 * are packed with red in the low byte and alpha in the high one.
 */
final class PackColors {

    private PackColors() {
    }

    static int pack(int red, int green, int blue, int alpha) {
        return red & 0xFF | (green & 0xFF) << 8 | (blue & 0xFF) << 16 | (alpha & 0xFF) << 24;
    }

    static int channel(int color, int index) {
        return color >> (index * 8) & 0xFF;
    }

    static float tint(int multiplier, int index) {
        return (float) (multiplier >> (16 - index * 8) & 0xFF) / 255.0F;
    }

    private static int forgeByte(float value) {
        return Math.round(value * 255) & 0xFF;
    }

    static float forgeChannel(float base, boolean tinted, int multiplier, int index, float ao, float diffuse,
            boolean keepAo, boolean keepDiffuse) {
        float value = base;
        if (tinted) {
            value *= tint(multiplier, index);
        }
        if (keepAo) {
            value *= ao;
        }
        if (keepDiffuse) {
            value *= diffuse;
        }
        return value;
    }

    static int forgeVanilla(float[] base, boolean tinted, int multiplier, float ao, float diffuse) {
        return pack(forgeByte(forgeChannel(base[0], tinted, multiplier, 0, ao, diffuse, true, true)),
                forgeByte(forgeChannel(base[1], tinted, multiplier, 1, ao, diffuse, true, true)),
                forgeByte(forgeChannel(base[2], tinted, multiplier, 2, ao, diffuse, true, true)), forgeByte(base[3]));
    }

    static int forgePack(float[] base, boolean tinted, int multiplier, float ao, float diffuse, boolean faceShading,
            boolean separateAo) {
        int alpha = forgeByte(separateAo ? base[3] * ao : base[3]);
        return pack(forgeByte(forgeChannel(base[0], tinted, multiplier, 0, ao, diffuse, !separateAo, faceShading)),
                forgeByte(forgeChannel(base[1], tinted, multiplier, 1, ao, diffuse, !separateAo, faceShading)),
                forgeByte(forgeChannel(base[2], tinted, multiplier, 2, ao, diffuse, !separateAo, faceShading)), alpha);
    }

    private static int vanillaByte(int base, float multiplier) {
        return (int) ((float) base * multiplier);
    }

    static float modelMultiplier(boolean smooth, boolean tinted, int multiplier, int index, float ao,
            boolean diffused, float diffuse, boolean keepAo, boolean keepDiffuse) {
        if (smooth) {
            float value = keepAo ? ao : 1.0F;
            if (diffused && keepDiffuse) {
                value *= diffuse;
            }
            return tinted ? value * tint(multiplier, index) : value;
        }
        if (tinted) {
            float value = tint(multiplier, index);
            return diffused && keepDiffuse ? value * diffuse : value;
        }
        return diffused && keepDiffuse ? diffuse : 1.0F;
    }

    static int modelPack(int base, boolean smooth, boolean tinted, int multiplier, float ao, boolean diffused,
            float diffuse, boolean faceShading, boolean separateAo) {
        int alpha = channel(base, 3);
        if (separateAo) {
            alpha = vanillaByte(alpha, ao);
        }
        return pack(vanillaByte(channel(base, 0), modelMultiplier(smooth, tinted, multiplier, 0, ao, diffused, diffuse,
                        !separateAo, faceShading)),
                vanillaByte(channel(base, 1), modelMultiplier(smooth, tinted, multiplier, 1, ao, diffused, diffuse,
                        !separateAo, faceShading)),
                vanillaByte(channel(base, 2), modelMultiplier(smooth, tinted, multiplier, 2, ao, diffused, diffuse,
                        !separateAo, faceShading)), alpha);
    }

    static int modelVanilla(int base, float red, float green, float blue) {
        return pack(vanillaByte(channel(base, 0), red), vanillaByte(channel(base, 1), green),
                vanillaByte(channel(base, 2), blue), channel(base, 3));
    }

    static final float FLUID_TOP = 1.0F;
    static final float FLUID_NORTH_SOUTH = 0.8F;
    static final float FLUID_EAST_WEST = 0.6F;
    static final float FLUID_BOTTOM = 0.5F;
    private static final float[] TINTED_FLUID_FACES = {FLUID_TOP, FLUID_NORTH_SOUTH, FLUID_EAST_WEST};

    static float fluidFace(int multiplier, float red, float green, float blue) {
        for (float factor : TINTED_FLUID_FACES) {
            if (red == 1.0F * factor * tint(multiplier, 0) && green == 1.0F * factor * tint(multiplier, 1)
                    && blue == 1.0F * factor * tint(multiplier, 2)) {
                return factor;
            }
        }
        return red == FLUID_BOTTOM && green == FLUID_BOTTOM && blue == FLUID_BOTTOM ? FLUID_BOTTOM : -1;
    }

    private static int fluidByte(float value) {
        return (int) (value * 255.0F);
    }

    static int fluidVanilla(float red, float green, float blue, float alpha) {
        return pack(fluidByte(red), fluidByte(green), fluidByte(blue), fluidByte(alpha));
    }

    static int fluidPack(int multiplier, float face, float red, float green, float blue, float alpha,
            boolean faceShading) {
        if (faceShading) {
            return fluidVanilla(red, green, blue, alpha);
        }
        if (face == FLUID_BOTTOM) {
            return fluidVanilla(1.0F, 1.0F, 1.0F, alpha);
        }
        return fluidVanilla(1.0F * tint(multiplier, 0), 1.0F * tint(multiplier, 1), 1.0F * tint(multiplier, 2), alpha);
    }
}
