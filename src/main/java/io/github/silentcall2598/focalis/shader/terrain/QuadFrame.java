// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.shader.terrain;

/**
 * The per-quad stream values worked out from a quad's four positions and texture coordinates, in stored order. Reused
 * for every quad, so a chunk build allocates nothing per quad. Not thread safe.
 *
 * <p>The normal is the direction of the quad's vector area, so it points out of the side vanilla's winding faces. The
 * tangent is where the position moves while U grows and V stays put, made perpendicular to the normal. {@code tangentW}
 * is 1 when V grows along cross(tangent, normal) and -1 when the texture is mirrored against that. The midpoint is the
 * mean of the four texture coordinates.
 */
final class QuadFrame {

    // Below these, a quad or triangle is treated as having no area, or its texture coordinates as having no direction.
    private static final double FLAT = 1.0e-12;
    private static final double PARALLEL = 1.0e-6;

    private final double[] x = new double[4];
    private final double[] y = new double[4];
    private final double[] z = new double[4];
    private final double[] u = new double[4];
    private final double[] v = new double[4];

    double normalX;
    double normalY;
    double normalZ;
    double tangentX;
    double tangentY;
    double tangentZ;
    double tangentW;
    double midU;
    double midV;

    void vertex(int index, double px, double py, double pz, double tu, double tv) {
        x[index] = px;
        y[index] = py;
        z[index] = pz;
        u[index] = tu;
        v[index] = tv;
    }

    void compute() {
        normal();
        midU = (u[0] + u[1] + u[2] + u[3]) * 0.25;
        midV = (v[0] + v[1] + v[2] + v[3]) * 0.25;
        if (!finite(midU) || !finite(midV)) {
            midU = 0.0;
            midV = 0.0;
        }
        // The other half of the quad when the first has no area or no texture direction, like a triangle stored
        // with a repeated vertex.
        if (!tangent(0, 1, 2) && !tangent(0, 2, 3)) {
            fallbackTangent();
        }
    }

    // The cross product of the diagonals is twice the vector area, whichever way the quad is split into triangles.
    private void normal() {
        double ax = x[2] - x[0];
        double ay = y[2] - y[0];
        double az = z[2] - z[0];
        double bx = x[3] - x[1];
        double by = y[3] - y[1];
        double bz = z[3] - z[1];
        double nx = ay * bz - az * by;
        double ny = az * bx - ax * bz;
        double nz = ax * by - ay * bx;
        double length = Math.sqrt(nx * nx + ny * ny + nz * nz);
        double scale = Math.max(diagonal(ax, ay, az), diagonal(bx, by, bz));
        if (!(length > FLAT * scale * scale) || !finite(length)) {
            // A quad without area draws nothing, so any unit normal does.
            normalX = 0.0;
            normalY = 1.0;
            normalZ = 0.0;
            return;
        }
        normalX = nx / length;
        normalY = ny / length;
        normalZ = nz / length;
    }

    private static double diagonal(double dx, double dy, double dz) {
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private boolean tangent(int a, int b, int c) {
        double e1x = x[b] - x[a];
        double e1y = y[b] - y[a];
        double e1z = z[b] - z[a];
        double e2x = x[c] - x[a];
        double e2y = y[c] - y[a];
        double e2z = z[c] - z[a];
        double cx = e1y * e2z - e1z * e2y;
        double cy = e1z * e2x - e1x * e2z;
        double cz = e1x * e2y - e1y * e2x;
        double area = Math.sqrt(cx * cx + cy * cy + cz * cz);
        double edges = diagonal(e1x, e1y, e1z) * diagonal(e2x, e2y, e2z);
        if (!(area > PARALLEL * edges)) {
            return false;
        }
        double du1 = u[b] - u[a];
        double dv1 = v[b] - v[a];
        double du2 = u[c] - u[a];
        double dv2 = v[c] - v[a];
        double det = du1 * dv2 - du2 * dv1;
        double spans = Math.sqrt((du1 * du1 + dv1 * dv1) * (du2 * du2 + dv2 * dv2));
        if (!(Math.abs(det) > PARALLEL * spans)) {
            return false;
        }
        // Solves e1 = du1 T + dv1 B and e2 = du2 T + dv2 B for T and B, the directions U and V grow in.
        double tx = (e1x * dv2 - e2x * dv1) / det;
        double ty = (e1y * dv2 - e2y * dv1) / det;
        double tz = (e1z * dv2 - e2z * dv1) / det;
        double bx = (e2x * du1 - e1x * du2) / det;
        double by = (e2y * du1 - e1y * du2) / det;
        double bz = (e2z * du1 - e1z * du2) / det;
        double along = tx * normalX + ty * normalY + tz * normalZ;
        tx -= normalX * along;
        ty -= normalY * along;
        tz -= normalZ * along;
        double length = Math.sqrt(tx * tx + ty * ty + tz * tz);
        double size = Math.sqrt(bx * bx + by * by + bz * bz);
        // A tangent that is mostly along the normal says more about a bent quad than about its texture.
        if (!(length > PARALLEL * (length + Math.abs(along))) || !finite(length) || !finite(size)) {
            return false;
        }
        tx /= length;
        ty /= length;
        tz /= length;
        double handedness = (ty * normalZ - tz * normalY) * bx + (tz * normalX - tx * normalZ) * by
                + (tx * normalY - ty * normalX) * bz;
        tangentX = tx;
        tangentY = ty;
        tangentZ = tz;
        tangentW = handedness < 0.0 ? -1.0 : 1.0;
        return true;
    }

    // Texture coordinates with no direction leave nothing to follow, so the tangent is the X axis, or the Z axis for
    // faces that point along X, made perpendicular to the normal.
    private void fallbackTangent() {
        double ax = Math.abs(normalX) < 0.9 ? 1.0 : 0.0;
        double az = 1.0 - ax;
        double along = ax * normalX + az * normalZ;
        double tx = ax - normalX * along;
        double ty = -normalY * along;
        double tz = az - normalZ * along;
        double length = Math.sqrt(tx * tx + ty * ty + tz * tz);
        tangentX = tx / length;
        tangentY = ty / length;
        tangentZ = tz / length;
        tangentW = 1.0;
    }

    private static boolean finite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }
}
