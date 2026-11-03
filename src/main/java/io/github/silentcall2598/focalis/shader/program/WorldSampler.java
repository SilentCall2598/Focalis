// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.shader.program;

import javax.annotation.Nullable;

public enum WorldSampler {

    TEXTURE("texture", 0),
    GTEXTURE("gtexture", 0),
    // shaders.txt lists tex for the shadow programs only, but packs that use GLSL 1.30, where texture is a built-in
    // function, read the gbuffers albedo through it, like the pinned Complementary r5.9.3.
    TEX("tex", 0),
    LIGHTMAP("lightmap", 1);

    private final String uniformName;
    private final int unit;

    WorldSampler(String uniformName, int unit) {
        this.uniformName = uniformName;
        this.unit = unit;
    }

    public String uniformName() {
        return uniformName;
    }

    public int unit() {
        return unit;
    }

    @Nullable
    static WorldSampler named(String uniformName) {
        for (WorldSampler sampler : values()) {
            if (sampler.uniformName.equals(uniformName)) {
                return sampler;
            }
        }
        return null;
    }
}
