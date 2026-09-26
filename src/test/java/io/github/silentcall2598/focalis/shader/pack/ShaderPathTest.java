// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.shader.pack;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ShaderPathTest {

    @Test
    void normalizesSeparatorsDotsAndLeadingSlashes() {
        assertEquals("lib/common.glsl", ShaderPath.of("/lib//./world0/../common.glsl").toString());
        assertEquals("lib/common.glsl", ShaderPath.of("lib\\common.glsl").toString());
        assertEquals("lib/tone.glsl", ShaderPath.of("world0/final.fsh").resolveSibling("../lib/tone.glsl").toString());
    }

    @Test
    void rejectsPathsThatLeaveTheShadersFolderOrLookLikeFilesystemPaths() {
        assertThrows(IllegalArgumentException.class, () -> ShaderPath.of("../options.txt"));
        assertThrows(IllegalArgumentException.class, () -> ShaderPath.of("lib/../../options.txt"));
        assertThrows(IllegalArgumentException.class, () -> ShaderPath.of("C:/Windows/win.ini"));
        assertThrows(IllegalArgumentException.class, () -> ShaderPath.of("./"));
    }
}
