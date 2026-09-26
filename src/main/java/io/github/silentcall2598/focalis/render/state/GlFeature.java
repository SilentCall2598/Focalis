// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.render.state;

import org.lwjgl.opengl.ContextCapabilities;

import java.util.function.Predicate;

// A capability counts if either the core GL version or the matching extension provides it.
public enum GlFeature {
    FRAMEBUFFER_OBJECT("framebuffer objects",
            caps -> caps.OpenGL30 || caps.GL_ARB_framebuffer_object || caps.GL_EXT_framebuffer_object),
    FLOAT_TEXTURES("float textures", caps -> caps.OpenGL30 || caps.GL_ARB_texture_float),
    VERTEX_ARRAY_OBJECT("vertex array objects", caps -> caps.OpenGL30 || caps.GL_ARB_vertex_array_object),
    COMPUTE_SHADER("compute shaders", caps -> caps.OpenGL43 || caps.GL_ARB_compute_shader),
    SHADER_STORAGE_BUFFER("shader storage buffers",
            caps -> caps.OpenGL43 || caps.GL_ARB_shader_storage_buffer_object),
    MULTI_DRAW_INDIRECT("multi-draw indirect", caps -> caps.OpenGL43 || caps.GL_ARB_multi_draw_indirect),
    BUFFER_STORAGE("buffer storage", caps -> caps.OpenGL44 || caps.GL_ARB_buffer_storage),
    DIRECT_STATE_ACCESS("direct state access", caps -> caps.OpenGL45 || caps.GL_ARB_direct_state_access),
    DEBUG_OUTPUT("debug output", caps -> caps.OpenGL43 || caps.GL_KHR_debug || caps.GL_ARB_debug_output),
    ANISOTROPIC_FILTERING("anisotropic filtering", caps -> caps.GL_EXT_texture_filter_anisotropic);

    private final String displayName;
    private final Predicate<ContextCapabilities> detector;

    GlFeature(String displayName, Predicate<ContextCapabilities> detector) {
        this.displayName = displayName;
        this.detector = detector;
    }

    public String displayName() {
        return displayName;
    }

    boolean isSupported(ContextCapabilities caps) {
        return detector.test(caps);
    }
}
