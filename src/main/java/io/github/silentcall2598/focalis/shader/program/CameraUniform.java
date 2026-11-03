// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.shader.program;

import javax.annotation.Nullable;

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
