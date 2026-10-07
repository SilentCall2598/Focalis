// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.shader.program;

/** 4x4 float matrices stored the way OpenGL returns and takes them, column by column. */
final class Matrix4 {

    static final int SIZE = 16;

    private Matrix4() {
    }

    /**
     * Writes the inverse of {@code m} into {@code out}, worked out in double precision. Inverting a transposed matrix
     * gives the transposed inverse, so this doesn't depend on the storage order.
     *
     * @return false, with {@code out} left untouched, when {@code m} has no finite inverse
     */
    static boolean invert(float[] m, float[] out) {
        double a00 = m[0], a01 = m[1], a02 = m[2], a03 = m[3];
        double a10 = m[4], a11 = m[5], a12 = m[6], a13 = m[7];
        double a20 = m[8], a21 = m[9], a22 = m[10], a23 = m[11];
        double a30 = m[12], a31 = m[13], a32 = m[14], a33 = m[15];
        // 2x2 determinants of the first two rows and of the last two.
        double s0 = a00 * a11 - a10 * a01;
        double s1 = a00 * a12 - a10 * a02;
        double s2 = a00 * a13 - a10 * a03;
        double s3 = a01 * a12 - a11 * a02;
        double s4 = a01 * a13 - a11 * a03;
        double s5 = a02 * a13 - a12 * a03;
        double c0 = a20 * a31 - a30 * a21;
        double c1 = a20 * a32 - a30 * a22;
        double c2 = a20 * a33 - a30 * a23;
        double c3 = a21 * a32 - a31 * a22;
        double c4 = a21 * a33 - a31 * a23;
        double c5 = a22 * a33 - a32 * a23;
        double det = s0 * c5 - s1 * c4 + s2 * c3 + s3 * c2 - s4 * c1 + s5 * c0;
        if (det == 0 || !finite(det)) {
            return false;
        }
        double inv = 1 / det;
        double[] r = {
                (a11 * c5 - a12 * c4 + a13 * c3) * inv,
                (-a01 * c5 + a02 * c4 - a03 * c3) * inv,
                (a31 * s5 - a32 * s4 + a33 * s3) * inv,
                (-a21 * s5 + a22 * s4 - a23 * s3) * inv,
                (-a10 * c5 + a12 * c2 - a13 * c1) * inv,
                (a00 * c5 - a02 * c2 + a03 * c1) * inv,
                (-a30 * s5 + a32 * s2 - a33 * s1) * inv,
                (a20 * s5 - a22 * s2 + a23 * s1) * inv,
                (a10 * c4 - a11 * c2 + a13 * c0) * inv,
                (-a00 * c4 + a01 * c2 - a03 * c0) * inv,
                (a30 * s4 - a31 * s2 + a33 * s0) * inv,
                (-a20 * s4 + a21 * s2 - a23 * s0) * inv,
                (-a10 * c3 + a11 * c1 - a12 * c0) * inv,
                (a00 * c3 - a01 * c1 + a02 * c0) * inv,
                (-a30 * s3 + a31 * s1 - a32 * s0) * inv,
                (a20 * s3 - a21 * s1 + a22 * s0) * inv};
        // A huge determinant can still leave values that overflow a float.
        for (double value : r) {
            if (!finite((float) value)) {
                return false;
            }
        }
        for (int i = 0; i < SIZE; i++) {
            out[i] = (float) r[i];
        }
        return true;
    }

    private static boolean finite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }
}
