// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.shader.pack;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ProgramDirectory {

    private static final Pattern WORLD_FOLDER = Pattern.compile("world(-?\\d+)");

    private final String name;
    private final Map<String, ProgramSource> programs;

    ProgramDirectory(String name, Map<String, ProgramSource> programs) {
        this.name = name;
        this.programs = Collections.unmodifiableMap(new TreeMap<>(programs));
    }

    public String name() {
        return name;
    }

    public String path() {
        return name.isEmpty() ? "shaders" : "shaders/" + name;
    }

    @Nullable
    public ProgramSource find(String programName) {
        return programs.get(programName);
    }

    public Collection<ProgramSource> programs() {
        return programs.values();
    }

    @Nullable
    public Integer worldId() {
        return worldId(name);
    }

    public static String worldFolder(int dimension) {
        return "world" + dimension;
    }

    @Nullable
    static Integer worldId(String folderName) {
        Matcher matcher = WORLD_FOLDER.matcher(folderName);
        if (!matcher.matches()) {
            return null;
        }
        try {
            return Integer.valueOf(matcher.group(1));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
