// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.shader.program;

import javax.annotation.Nullable;

/**
 * The legacy samplers world programs get. Each one reads the texture unit Minecraft already keeps that texture on,
 * so Focalis never binds a texture for them.
 */
public enum WorldSampler {

    /** Whatever Minecraft bound for the current draw, like the block atlas, an entity's skin or a chest texture. */
    TEXTURE("texture", 0),
    /** Minecraft's lightmap, which it binds on unit 1 while a stage draws with lighting. */
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

    /** The texture unit index the uniform is set to. It's i for GL_TEXTUREi, never the enum itself. */
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
