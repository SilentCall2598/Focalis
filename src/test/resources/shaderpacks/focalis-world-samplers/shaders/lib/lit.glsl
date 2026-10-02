// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only

// What vanilla draws without fog. The texture Minecraft bound for the draw, lit by its lightmap.

uniform sampler2D texture;
uniform sampler2D lightmap;

varying vec4 color;
varying vec2 texCoord;
varying vec2 lightCoord;

void main() {
    gl_FragColor = texture2D(texture, texCoord) * texture2D(lightmap, lightCoord) * color;
}
