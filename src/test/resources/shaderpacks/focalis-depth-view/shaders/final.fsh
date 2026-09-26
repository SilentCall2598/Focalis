#version 120
// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only

// Left half shows linear depth, right half shows the scene untouched.

#include "/lib/depth.glsl"

uniform sampler2D colortex0;
uniform sampler2D depthtex0;
uniform float near;
uniform float far;

varying vec2 texCoord;

void main() {
    vec3 color = texture2D(colortex0, texCoord).rgb;
    if (texCoord.x < 0.5) {
        color = vec3(linearDepth(texture2D(depthtex0, texCoord).r, near, far) / far);
    }
    gl_FragColor = vec4(color, 1.0);
}
