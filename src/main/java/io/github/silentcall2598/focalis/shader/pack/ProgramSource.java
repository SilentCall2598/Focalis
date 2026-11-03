// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.shader.pack;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

public final class ProgramSource {

    private final String name;
    private final String directory;
    private final Map<ProgramStage, ShaderPath> stages;
    @Nullable
    private final String problem;

    ProgramSource(String name, String directory, Map<ProgramStage, ShaderPath> stages, @Nullable String problem) {
        this.name = name;
        this.directory = directory;
        this.stages = Collections.unmodifiableMap(new EnumMap<>(stages));
        this.problem = problem;
    }

    public String name() {
        return name;
    }

    public String directory() {
        return directory;
    }

    public Map<ProgramStage, ShaderPath> stages() {
        return stages;
    }

    @Nullable
    public ShaderPath file(ProgramStage stage) {
        return stages.get(stage);
    }

    /** Compile and link results belong to the GL layer and are never recorded here. */
    @Nullable
    public String problem() {
        return problem;
    }
}
