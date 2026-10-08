// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.shader.pack;

import java.util.List;

/**
 * One option a pack's program sources define, found the way the shaderpack format documents it. Its values are kept
 * as the text the pack writes, and a switch uses {@code true} and {@code false}. Immutable.
 */
public final class ShaderOption {

    public enum Kind {
        /** {@code #define NAME} for on or {@code // #define NAME} for off, tested by an #ifdef or #ifndef. */
        SWITCH,
        /** {@code #define NAME value // [allowed values]}. */
        VALUE,
        /** One of the documented {@code const} names with a list of allowed values. */
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

    /** The value the sources have as written. For an ambiguous option it's the first definition's value. */
    public String defaultValue() {
        return defaultValue;
    }

    /** The values the option may take, always including the default. A switch allows true and false. */
    public List<String> allowedValues() {
        return allowedValues;
    }

    /** Every place the option is defined, in path and line order. They all change together. */
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
