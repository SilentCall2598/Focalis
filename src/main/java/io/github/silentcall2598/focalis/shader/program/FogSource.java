// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.shader.program;

/** This is draw state, so it's read every time a stage binds a program that uses it, never kept per frame. */
public interface FogSource {

    /** False when the fog can't be read in this session. Programs that use it then draw the normal way. */
    boolean available();

    boolean enabled();

    int mode();

    float start();

    float end();

    float density();

    float red();

    float green();

    float blue();
}
