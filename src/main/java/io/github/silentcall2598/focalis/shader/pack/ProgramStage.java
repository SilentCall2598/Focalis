// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.shader.pack;

import javax.annotation.Nullable;

/** A shader stage and the file extension OptiFine-style packs use for it. */
public enum ProgramStage {
    VERTEX("vsh"),
    GEOMETRY("gsh"),
    FRAGMENT("fsh"),
    COMPUTE("csh");

    private final String extension;

    ProgramStage(String extension) {
        this.extension = extension;
    }

    public String extension() {
        return extension;
    }

    /** The stage for a file name like {@code final.fsh}, or null if it isn't a stage file. */
    @Nullable
    static ProgramStage forFileName(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot <= 0) {
            return null;
        }
        String extension = fileName.substring(dot + 1);
        for (ProgramStage stage : values()) {
            if (stage.extension.equals(extension)) {
                return stage;
            }
        }
        return null;
    }
}
