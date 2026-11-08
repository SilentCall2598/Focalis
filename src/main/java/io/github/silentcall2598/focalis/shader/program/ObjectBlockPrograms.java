// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.shader.program;

public interface ObjectBlockPrograms {

    int NONE = 0;
    int NORMAL = 1;
    int COLORS = 2;

    /** Render thread only, since it may ask GL for the current program. */
    int blockDraw();
}
