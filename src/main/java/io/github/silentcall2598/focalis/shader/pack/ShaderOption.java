// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.shader.pack;

import java.util.List;

public final class ShaderOption {

    public enum Kind {
        SWITCH,
        VALUE,
        CONST
    }

    private final String name;
    private final Kind kind;
    private final String defaultValue;
    private final List<String> allowedValues;
    private final List<SourceLocation> definitions;
    private final boolean ambiguous;

    ShaderOption(String name, Kind kind, String defaultValue, List<String> allowedValues,
            List<SourceLocation> definitions, boolean ambiguous) {
        this.name = name;
        this.kind = kind;
        this.defaultValue = defaultValue;
        this.allowedValues = allowedValues;
        this.definitions = definitions;
        this.ambiguous = ambiguous;
    }

    public String name() {
        return name;
    }

    public Kind kind() {
        return kind;
    }

    public String defaultValue() {
        return defaultValue;
    }

    public List<String> allowedValues() {
        return allowedValues;
    }

    public List<SourceLocation> definitions() {
        return definitions;
    }

    /** Definitions that disagree on the kind or default make an option fixed at its sources as written. */
    public boolean ambiguous() {
        return ambiguous;
    }

    @Override
    public String toString() {
        return name + " " + kind + " default " + defaultValue + (ambiguous ? " (ambiguous)" : "");
    }
}
