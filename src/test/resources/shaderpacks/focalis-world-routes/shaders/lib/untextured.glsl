// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only

#include "/lib/frame.glsl"

varying vec4 color;

void main() {
    gl_FragColor = color * frameInputs();
}
