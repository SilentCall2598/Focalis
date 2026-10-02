// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only

uniform sampler2D texture;

varying vec4 color;
varying vec2 texCoord;

void main() {
    gl_FragColor = texture2D(texture, texCoord) * color;
}
