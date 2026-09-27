// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only

// Rough distance from the camera in blocks. Minecraft 1.12.2 puts the world's near plane at 0.05 and the far plane
// is much further out, so near / (1 - depth) is close for anything well short of the far plane.
float approximateDistance(float depth) {
    return 0.05 / max(1.0 - depth, 0.000001);
}

// White at the camera, fading to black with distance. The overworld sky doesn't write depth, so it comes out black.
float depthShade(float depth) {
    return exp(-approximateDistance(depth) / 16.0);
}
