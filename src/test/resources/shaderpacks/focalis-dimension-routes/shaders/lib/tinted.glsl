// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only

// Like textured.glsl, tinted with the TINT the including program defines, so each folder's programs are easy to
// tell apart in screenshots.

uniform sampler2D texture;

varying vec4 color;
varying vec2 texCoord;

void main() {
    gl_FragColor = texture2D(texture, texCoord) * color * TINT;
}
