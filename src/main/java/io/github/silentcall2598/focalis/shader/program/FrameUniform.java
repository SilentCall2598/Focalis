// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.shader.program;

import javax.annotation.Nullable;

/**
 * The legacy view and frame uniforms world programs get from {@link FrameInputs}, with the names and types of the
 * OptiFine shader documentation.
 */
public enum FrameUniform {

    VIEW_WIDTH("viewWidth", false),
    VIEW_HEIGHT("viewHeight", false),
    ASPECT_RATIO("aspectRatio", false),
    FRAME_COUNTER("frameCounter", true),
    FRAME_TIME("frameTime", false),
    FRAME_TIME_COUNTER("frameTimeCounter", false);

    private final String uniformName;
    private final boolean integer;

    FrameUniform(String uniformName, boolean integer) {
        this.uniformName = uniformName;
        this.integer = integer;
    }

    public String uniformName() {
        return uniformName;
    }

    /** True for an int uniform, false for a float. */
    public boolean integer() {
        return integer;
    }

    @Nullable
    static FrameUniform named(String uniformName) {
        for (FrameUniform uniform : values()) {
            if (uniform.uniformName.equals(uniformName)) {
                return uniform;
            }
        }
        return null;
    }
}
