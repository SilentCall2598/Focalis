// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.shader.program;

/**
 * Minecraft's fixed-function fog as it stands right now. This is draw state, so it's read every time a stage binds a
 * program that uses it, never kept per frame. Client thread only.
 */
public interface FogSource {

    /** False when the fog can't be read in this session. Programs that use it then draw the normal way. */
    boolean available();

    /** Whether GL_FOG is on. */
    boolean enabled();

    /** GL_FOG_MODE, GL_LINEAR, GL_EXP or GL_EXP2. */
    int mode();

    float start();

    float end();

    float density();

    float red();

    float green();

    float blue();
}
