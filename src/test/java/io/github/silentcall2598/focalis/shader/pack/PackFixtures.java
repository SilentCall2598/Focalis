// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.shader.pack;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Builds small shaderpacks on disk for tests. Paths are relative to the pack root, like "shaders/final.fsh". */
final class PackFixtures {

    static final String VERTEX = "#version 120\nvoid main() { gl_Position = ftransform(); }\n";
    static final String FRAGMENT = "#version 120\nvoid main() { gl_FragColor = vec4(1.0); }\n";

    private PackFixtures() {
    }

    static Map<String, String> files(String... pathsAndTexts) {
        Map<String, String> files = new LinkedHashMap<>();
        for (int i = 0; i < pathsAndTexts.length; i += 2) {
            files.put(pathsAndTexts[i], pathsAndTexts[i + 1]);
        }
        return files;
    }

    static Map<ShaderPath, String> shaderFiles(String... pathsAndTexts) {
        Map<ShaderPath, String> files = new LinkedHashMap<>();
        for (Map.Entry<String, String> file : files(pathsAndTexts).entrySet()) {
            files.put(ShaderPath.of(file.getKey()), file.getValue());
        }
        return files;
    }

    static Path directoryPack(Path parent, String name, Map<String, String> files) throws IOException {
        Path pack = parent.resolve(name);
        for (Map.Entry<String, String> file : files.entrySet()) {
            Path target = pack.resolve(file.getKey());
            Files.createDirectories(target.getParent());
            Files.write(target, file.getValue().getBytes(StandardCharsets.UTF_8));
        }
        return pack;
    }

    // Entry names are written exactly as given, which is how the traversal tests build hostile ZIPs.
    static Path zipPack(Path parent, String name, Map<String, String> entries) throws IOException {
        Path zip = parent.resolve(name);
        try (OutputStream file = Files.newOutputStream(zip); ZipOutputStream output = new ZipOutputStream(file)) {
            for (Map.Entry<String, String> entry : entries.entrySet()) {
                output.putNextEntry(new ZipEntry(entry.getKey()));
                output.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                output.closeEntry();
            }
        }
        return zip;
    }
}
