// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.shader.pack;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class LegacyLighting {

    public static final LegacyLighting UNSET = new LegacyLighting(null, null);

    static final String OLD_LIGHTING = "oldLighting";
    static final String SEPARATE_AO = "separateAo";

    @Nullable
    private final Boolean oldLighting;
    @Nullable
    private final Boolean separateAo;

    private LegacyLighting(@Nullable Boolean oldLighting, @Nullable Boolean separateAo) {
        this.oldLighting = oldLighting;
        this.separateAo = separateAo;
    }

    public static LegacyLighting of(@Nullable Boolean oldLighting, @Nullable Boolean separateAo) {
        return oldLighting == null && separateAo == null ? UNSET : new LegacyLighting(oldLighting, separateAo);
    }

    static LegacyLighting read(ShaderPath file, String text, Map<String, String> environment, List<PackIssue> issues) {
        List<ShaderProperties.Line> lines = ShaderProperties.readLines(file, text, environment,
                new ArrayList<PackIssue>());
        Boolean oldLighting = null;
        Boolean separateAo = null;
        ShaderProperties.Line oldLine = null;
        ShaderProperties.Line separateLine = null;
        for (ShaderProperties.Line line : lines) {
            boolean old = line.key.equals(OLD_LIGHTING);
            if (!old && !line.key.equals(SEPARATE_AO)) {
                continue;
            }
            ShaderProperties.Line earlier = old ? oldLine : separateLine;
            if (earlier != null) {
                issues.add(new PackIssue(new SourceLocation(file, earlier.line), line.key + "=" + earlier.value
                        + " is replaced by line " + line.line));
            }
            Boolean value = parse(line.value);
            if (value == null) {
                issues.add(new PackIssue(new SourceLocation(file, line.line), line.key + "=" + line.value
                        + " is ignored, it's neither true nor false"));
            }
            if (old) {
                oldLighting = value;
                oldLine = line;
            } else {
                separateAo = value;
                separateLine = line;
            }
        }
        return of(oldLighting, separateAo);
    }

    @Nullable
    private static Boolean parse(String value) {
        String trimmed = value.trim();
        if (trimmed.equals("true")) {
            return Boolean.TRUE;
        }
        return trimmed.equals("false") ? Boolean.FALSE : null;
    }

    public boolean faceShading() {
        return !Boolean.FALSE.equals(oldLighting);
    }

    public boolean separateAo() {
        return Boolean.TRUE.equals(separateAo);
    }

    public boolean changesTerrainColors() {
        return !faceShading() || separateAo();
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof LegacyLighting)) {
            return false;
        }
        LegacyLighting lighting = (LegacyLighting) other;
        return Objects.equals(oldLighting, lighting.oldLighting) && Objects.equals(separateAo, lighting.separateAo);
    }

    @Override
    public int hashCode() {
        return Objects.hash(oldLighting, separateAo);
    }

    @Override
    public String toString() {
        return "oldLighting " + (oldLighting == null ? "unset" : oldLighting) + ", separateAo "
                + (separateAo == null ? "unset" : separateAo);
    }
}
