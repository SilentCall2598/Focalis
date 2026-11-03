// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.shader.session;

import io.github.silentcall2598.focalis.shader.pack.ShaderPackException;

import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;

final class PackSelection {

    private PackSelection() {
    }

    static Path resolve(Path shaderpacks, String name) throws ShaderPackException {
        String trimmed = name.trim();
        Path relative;
        try {
            relative = shaderpacks.getFileSystem().getPath(trimmed);
        } catch (InvalidPathException e) {
            throw notAPackName(trimmed);
        }
        if (trimmed.isEmpty() || trimmed.equals(".") || trimmed.equals("..") || relative.getRoot() != null
                || relative.getNameCount() != 1) {
            throw notAPackName(trimmed);
        }
        Path pack = shaderpacks.resolve(relative);
        if (!Files.exists(pack)) {
            throw new ShaderPackException("There is no shaderpack named '" + trimmed + "' in " + shaderpacks);
        }
        return pack;
    }

    private static ShaderPackException notAPackName(String name) {
        return new ShaderPackException("'" + name + "' is not a shaderpack name. Use the name of a folder or zip"
                + " directly inside the shaderpacks folder.");
    }
}
