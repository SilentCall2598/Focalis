// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only

// Only legacy built-ins. Minecraft sets up the lightmap unit's texture coordinate and matrix for terrain, entities,
// particles and weather, which turns them into lightmap texture coordinates.

varying vec4 color;
varying vec2 texCoord;
varying vec2 lightCoord;

void main() {
    gl_Position = ftransform();
    color = gl_Color;
    texCoord = (gl_TextureMatrix[0] * gl_MultiTexCoord0).st;
    lightCoord = (gl_TextureMatrix[1] * gl_MultiTexCoord1).st;
}
