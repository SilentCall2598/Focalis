// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only

// Turns a depth buffer value back into distance from the camera in blocks.
float linearDepth(float depth, float near, float far) {
    float z = depth * 2.0 - 1.0;
    return 2.0 * near * far / (far + near - z * (far - near));
}
