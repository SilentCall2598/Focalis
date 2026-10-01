// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.shader.pack;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;

/** Read access to the {@code shaders} directory of a pack stored as a folder or a ZIP file. */
public interface ShaderPackSource extends Closeable {

    /** The folder or file name the user sees in the shaderpacks directory. */
    String name();

    /** Every regular file under {@code shaders}, whether or not Focalis ever reads it. */
    Set<ShaderPath> files();

    /**
     * The names of the folders directly in {@code shaders}, also empty ones. A ZIP only has an empty folder when it
     * has an entry for that folder.
     */
    Set<String> folders();

    /** Reads a file as UTF-8 text. */
    String readText(ShaderPath path) throws IOException;

    static ShaderPackSource open(Path pack) throws ShaderPackException, IOException {
        if (Files.isDirectory(pack)) {
            return DirectoryPackSource.open(pack);
        }
        if (Files.isRegularFile(pack) && pack.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".zip")) {
            return ZipPackSource.open(pack);
        }
        throw new ShaderPackException("'" + pack.getFileName() + "' is not a shaderpack folder or .zip file");
    }
}
