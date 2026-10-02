#version 120
// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only

// rainStrength isn't set by Focalis yet, so it reads zero and never discards anything.

uniform sampler2D texture;
uniform float rainStrength;

varying vec4 color;
varying vec2 texCoord;

void main() {
    if (rainStrength > 1.0e9) {
        discard;
    }
    gl_FragColor = texture2D(texture, texCoord) * color;
}
