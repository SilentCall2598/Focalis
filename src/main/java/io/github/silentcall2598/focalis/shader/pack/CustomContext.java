// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.shader.pack;

/** What custom uniform expressions read while they're evaluated. The runtime implements it, the pack never does. */
public interface CustomContext {

    /** The current value of a built-in, by {@link CustomInput} ordinal. */
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
