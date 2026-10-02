// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only

// Declares the lightmap without using it, so the driver drops it and Focalis has nothing to set.

uniform sampler2D lightmap;

varying vec4 color;

void main() {
    gl_FragColor = color;
}
