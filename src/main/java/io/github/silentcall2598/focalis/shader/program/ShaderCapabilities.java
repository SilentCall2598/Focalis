// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.shader.program;

import io.github.silentcall2598.focalis.render.state.GlContextInfo;
import io.github.silentcall2598.focalis.render.state.GlFeature;
import io.github.silentcall2598.focalis.shader.pack.ProgramStage;

/**
 * Which shader stages the current OpenGL context can compile. It says nothing about whether a pack's GLSL
 * version will be accepted, which only the driver can decide.
 */
public final class ShaderCapabilities {

    private final boolean vertexAndFragment;
    private final boolean geometry;
    private final boolean compute;

    ShaderCapabilities(boolean vertexAndFragment, boolean geometry, boolean compute) {
        this.vertexAndFragment = vertexAndFragment;
        this.geometry = geometry;
        this.compute = compute;
    }

    public static ShaderCapabilities from(GlContextInfo gl) {
        boolean gl20 = atLeast(gl, 2, 0);
        // Geometry shaders before 3.2 came from extensions with a different setup model, so they don't count.
        return new ShaderCapabilities(gl20, atLeast(gl, 3, 2), gl20 && gl.has(GlFeature.COMPUTE_SHADER));
    }

    public boolean supports(ProgramStage stage) {
        switch (stage) {
            case VERTEX:
            case FRAGMENT:
                return vertexAndFragment;
            case GEOMETRY:
                return geometry;
            case COMPUTE:
                return compute;
            default:
                return false;
        }
    }

    /** What an unsupported stage would need, for error messages. */
    static String requirement(ProgramStage stage) {
        switch (stage) {
            case GEOMETRY:
                return "OpenGL 3.2";
            case COMPUTE:
                return "OpenGL 4.3 or ARB_compute_shader";
            default:
                return "OpenGL 2.0";
        }
    }

    private static boolean atLeast(GlContextInfo gl, int major, int minor) {
        return gl.majorVersion() > major || (gl.majorVersion() == major && gl.minorVersion() >= minor);
    }
}
