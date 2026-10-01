// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.shader.program;

/** One active uniform as the driver reports it. Arrays report their first element, like {@code name[0]}. */
final class ActiveUniform {

    final String name;
    /** The GL type enum, like GL_SAMPLER_2D. */
    final int type;
    /** 1, or the number of elements for an array. */
    final int size;

    ActiveUniform(String name, int type, int size) {
        this.name = name;
        this.type = type;
        this.size = size;
    }
}
