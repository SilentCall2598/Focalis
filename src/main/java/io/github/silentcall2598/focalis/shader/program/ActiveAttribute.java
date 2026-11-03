// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.shader.program;

final class ActiveAttribute {

    final String name;
    final int type;
    final int size;

    ActiveAttribute(String name, int type, int size) {
        this.name = name;
        this.type = type;
        this.size = size;
    }

    @Override
    public String toString() {
        return name + " (0x" + Integer.toHexString(type) + (size > 1 ? "[" + size + "]" : "") + ")";
    }
}
