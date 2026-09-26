// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.shader.pack;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/** An immutable, ordered set of preprocessor macros. An empty value means a plain {@code #define NAME}. */
public final class ShaderMacros {

    private static final ShaderMacros EMPTY = new ShaderMacros(Collections.<String, String>emptyMap());
    private static final Pattern NAME = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    private final Map<String, String> values;

    private ShaderMacros(Map<String, String> values) {
        this.values = values;
    }

    public static ShaderMacros empty() {
        return EMPTY;
    }

    /** Returns a copy with {@code name} defined as {@code value}, replacing any earlier value. */
    public ShaderMacros with(String name, String value) {
        if (!NAME.matcher(name).matches()) {
            throw new IllegalArgumentException("Invalid macro name '" + name + "'");
        }
        if (value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0) {
            throw new IllegalArgumentException("Macro '" + name + "' has a line break in its value");
        }
        Map<String, String> copy = new LinkedHashMap<>(values);
        copy.put(name, value);
        return new ShaderMacros(Collections.unmodifiableMap(copy));
    }

    public boolean isDefined(String name) {
        return values.containsKey(name);
    }

    @Nullable
    public String value(String name) {
        return values.get(name);
    }

    public Map<String, String> asMap() {
        return values;
    }
}
