#version 120
// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only

// Left half shows scene depth, right half shows the scene untouched. A two pixel magenta frame marks the screen
// edges, so gaps in coverage or a wrong view size are easy to spot.

#include "/lib/depth.glsl"

uniform sampler2D sceneColor;
uniform sampler2D sceneDepth;
uniform float viewWidth;
uniform float viewHeight;

void main() {
    vec2 pixel = gl_FragCoord.xy;
    vec2 texCoord = pixel / vec2(viewWidth, viewHeight);
    vec4 color = texture2D(sceneColor, texCoord);

    if (min(pixel.x, pixel.y) < 2.0 || pixel.x > viewWidth - 2.0 || pixel.y > viewHeight - 2.0) {
        color.rgb = vec3(1.0, 0.0, 1.0);
    } else if (texCoord.x < 0.5) {
        color.rgb = vec3(depthShade(texture2D(sceneDepth, texCoord).r));
    }
    // Alpha passes through, so the framebuffer's alpha ends up exactly as it was.
    gl_FragColor = color;
}
