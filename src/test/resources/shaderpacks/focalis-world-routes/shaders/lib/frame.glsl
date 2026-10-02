// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only

// Every view and frame value is at least 0, so this is always 1. The driver can't know that, so it keeps all of
// them active and QA can read them back without the picture changing.

uniform float viewWidth;
uniform float viewHeight;
uniform float aspectRatio;
uniform int frameCounter;
uniform float frameTime;
uniform float frameTimeCounter;

float frameInputs() {
    return step(0.0, viewWidth + viewHeight + aspectRatio + float(frameCounter) + frameTime + frameTimeCounter);
}
