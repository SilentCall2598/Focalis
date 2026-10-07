// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.shader.program;

import javax.annotation.Nullable;

/**
 * The legacy camera uniforms world programs get from {@link CameraSnapshot}, with the names and types of the OptiFine
 * shader documentation. The positions are vec3s and the matrices mat4s.
 */
public enum CameraUniform {

    CAMERA_POSITION("cameraPosition", false),
    PREVIOUS_CAMERA_POSITION("previousCameraPosition", false),
    MODEL_VIEW("gbufferModelView", true),
    MODEL_VIEW_INVERSE("gbufferModelViewInverse", true),
    PREVIOUS_MODEL_VIEW("gbufferPreviousModelView", true),
    PROJECTION("gbufferProjection", true),
    PROJECTION_INVERSE("gbufferProjectionInverse", true),
    PREVIOUS_PROJECTION("gbufferPreviousProjection", true);

    private final String uniformName;
    private final boolean matrix;

    CameraUniform(String uniformName, boolean matrix) {
        this.uniformName = uniformName;
        this.matrix = matrix;
    }

    public String uniformName() {
        return uniformName;
    }

    /** True for a mat4, false for a vec3. */
    public boolean matrix() {
        return matrix;
    }

    @Nullable
    static CameraUniform named(String uniformName) {
        for (CameraUniform uniform : values()) {
            if (uniform.uniformName.equals(uniformName)) {
                return uniform;
            }
        }
        return null;
    }
}
