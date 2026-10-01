// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only

// Only legacy built-ins, since Focalis doesn't set any uniforms or vertex attributes for world programs yet.

varying vec4 color;
varying vec2 texCoord;

void main() {
    gl_Position = ftransform();
    color = gl_Color;
    texCoord = (gl_TextureMatrix[0] * gl_MultiTexCoord0).st;
}
