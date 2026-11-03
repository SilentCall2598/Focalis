// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.shader.terrain;

public interface StreamDisplayLists {

    int focalis$streamGeneration(int layer);

    void focalis$streamGeneration(int layer, int generation);
}
