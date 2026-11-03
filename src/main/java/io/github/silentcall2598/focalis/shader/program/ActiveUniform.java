// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.shader.program;

/** Arrays report their first element, like {@code name[0]}. */
final class ActiveUniform {

    final String name;
    final int type;
    final int size;

    ActiveUniform(String name, int type, int size) {
        this.name = name;
        this.type = type;
        this.size = size;
    }
}
