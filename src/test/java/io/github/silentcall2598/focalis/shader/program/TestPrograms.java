// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.shader.program;

import io.github.silentcall2598.focalis.shader.pack.ShaderMacros;
import io.github.silentcall2598.focalis.shader.pack.ShaderPack;
import io.github.silentcall2598.focalis.shader.pack.ShaderPackLoader;
import io.github.silentcall2598.focalis.shader.pack.StandardMacros;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Writes a tiny pack to disk and prepares one of its programs. Paths are relative to the shaders folder. */
final class TestPrograms {

    static final String VERTEX = "#version 120\nvoid main() { gl_Position = ftransform(); }\n";
    static final String FRAGMENT = "#version 120\nvoid main() { gl_FragColor = vec4(1.0); }\n";

    private TestPrograms() {
    }

    static ShaderPack pack(Path parent, String... pathsAndTexts) throws Exception {
        return ShaderPackLoader.load(packFolder(parent, pathsAndTexts));
    }

    // Only writes the files, so a test can add empty folders before loading.
    static Path packFolder(Path parent, String... pathsAndTexts) throws IOException {
        Path pack = Files.createTempDirectory(parent, "pack");
        for (int i = 0; i < pathsAndTexts.length; i += 2) {
            write(pack.resolve("shaders").resolve(pathsAndTexts[i]), pathsAndTexts[i + 1]);
        }
        return pack;
    }

    static PreparedProgram prepareFinal(Path parent, String... pathsAndTexts) throws Exception {
        ShaderPack pack = pack(parent, pathsAndTexts);
        return PreparedProgram.prepare(pack, pack.root().find("final"), StandardMacros.environment(),
                ShaderMacros.empty());
    }

    private static void write(Path file, String text) throws IOException {
        Files.createDirectories(file.getParent());
        Files.write(file, text.getBytes(StandardCharsets.UTF_8));
    }
}
