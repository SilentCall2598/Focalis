#version 120
// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only

// frameTimeCounter isn't set by Focalis yet, so it reads zero and never discards anything.

uniform sampler2D texture;
uniform float frameTimeCounter;

varying vec4 color;
varying vec2 texCoord;

void main() {
    if (frameTimeCounter > 1.0e9) {
        discard;
    }
    gl_FragColor = texture2D(texture, texCoord) * color;
}
