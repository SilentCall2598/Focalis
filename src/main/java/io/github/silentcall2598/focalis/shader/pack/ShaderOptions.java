// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.shader.pack;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public final class ShaderOptions {

    static final List<String> SWITCH_VALUES = Collections.unmodifiableList(Arrays.asList("true", "false"));

    private final Map<String, ShaderOption> options;

    ShaderOptions(Map<String, ShaderOption> options) {
        this.options = options;
    }

    public Map<String, ShaderOption> all() {
        return options;
    }

    @Nullable
    public ShaderOption get(String name) {
        return options.get(name);
    }
}
