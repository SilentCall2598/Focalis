#version 120
// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only

// Focalis draws one triangle over the whole screen with its corners already in clip space, so no matrices are used.

void main() {
    gl_Position = vec4(gl_Vertex.xy, 0.0, 1.0);
}
