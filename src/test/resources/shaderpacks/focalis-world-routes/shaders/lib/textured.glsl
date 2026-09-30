// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only

// The sampler is never set, so it reads texture unit 0, where Minecraft binds what it draws with. No lightmap and
// no fog, so the world comes out flat and bright.

uniform sampler2D texture;

varying vec4 color;
varying vec2 texCoord;

void main() {
    gl_FragColor = texture2D(texture, texCoord) * color;
}
