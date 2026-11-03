// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.shader.pack;

public interface CustomContext {

    float input(int input);

    /**
     * Moves the smoothed value of one smooth() call site toward {@code target} and returns it.
     *
     * @param site the call site, from 0 to {@link CustomUniforms#smoothSites()} - 1
     * @param fadeIn seconds the rise takes, 0 or less for none
     * @param fadeOut seconds the fall takes, 0 or less for none
     */
    float smooth(int site, float target, float fadeIn, float fadeOut);
}
