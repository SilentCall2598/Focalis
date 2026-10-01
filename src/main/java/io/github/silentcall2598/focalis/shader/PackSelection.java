// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.shader;

import io.github.silentcall2598.focalis.shader.pack.ProgramSource;
import io.github.silentcall2598.focalis.shader.pack.ShaderMacros;
import io.github.silentcall2598.focalis.shader.pack.ShaderPack;
import io.github.silentcall2598.focalis.shader.pack.ShaderPackException;
import io.github.silentcall2598.focalis.shader.pack.ShaderPackLoader;
import io.github.silentcall2598.focalis.shader.pack.StandardMacros;
import io.github.silentcall2598.focalis.shader.program.PreparedProgram;

import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;

/** Finds the configured pack and prepares the programs Focalis runs from it. No OpenGL is involved. */
final class PackSelection {

    private PackSelection() {
    }

    // The name has to be a single folder or zip name, so the config can't point outside the shaderpacks folder.
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

    static PreparedProgram prepare(Path pack, String programName) throws ShaderPackException {
        ShaderPack loaded = ShaderPackLoader.load(pack);
        ProgramSource program = loaded.root().find(programName);
        if (program == null) {
            throw new ShaderPackException("'" + loaded.name() + "' has no shaders/" + programName + " program."
                    + " Focalis can only run its own test program so far, not regular shaderpacks.");
        }
        return PreparedProgram.prepare(loaded, program, StandardMacros.environment(), ShaderMacros.empty());
    }

    private static ShaderPackException notAPackName(String name) {
        return new ShaderPackException("'" + name + "' is not a shaderpack name. Use the name of a folder or zip"
                + " directly inside the shaderpacks folder.");
    }
}
