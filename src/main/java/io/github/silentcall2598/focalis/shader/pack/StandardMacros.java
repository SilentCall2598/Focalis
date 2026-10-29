// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.shader.pack;

import io.github.silentcall2598.focalis.render.state.GlContextInfo;

import javax.annotation.Nullable;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class StandardMacros {

    public static final int MC_VERSION = 11202;

    private static final Pattern VERSION = Pattern.compile("^(\\d+)\\.(\\d+)");
    private static final String EXTENSION_PREFIX = "MC_GL_";

    private StandardMacros() {
    }

    public static ShaderMacros environment() {
        return ShaderMacros.empty().with("MC_VERSION", Integer.toString(MC_VERSION));
    }

    public static ShaderMacros environment(String osName, GlContextInfo gl, Set<String> mentioned) {
        ShaderMacros macros = environment();
        if (gl.majorVersion() >= 0 && gl.minorVersion() >= 0) {
            macros = macros.with("MC_GL_VERSION", Integer.toString(gl.majorVersion() * 100 + gl.minorVersion() * 10));
        }
        int glsl = glslVersion(gl.shadingLanguageVersion());
        if (glsl > 0) {
            macros = macros.with("MC_GLSL_VERSION", Integer.toString(glsl));
        }
        macros = macros.with("MC_OS_" + os(osName), "");
        macros = macros.with("MC_GL_VENDOR_" + vendor(gl.vendor()), "");
        macros = macros.with("MC_GL_RENDERER_" + renderer(gl.renderer()), "");
        for (String name : new TreeSet<>(mentioned)) {
            if (!name.startsWith(EXTENSION_PREFIX) || name.startsWith("MC_GL_VENDOR_")
                    || name.startsWith("MC_GL_RENDERER_") || name.equals("MC_GL_VERSION")) {
                continue;
            }
            if (gl.extensions().contains("GL_" + name.substring(EXTENSION_PREFIX.length()))) {
                macros = macros.with(name, "");
            }
        }
        return macros;
    }

    private static int glslVersion(@Nullable String version) {
        if (version == null) {
            return -1;
        }
        Matcher matcher = VERSION.matcher(version.trim());
        if (!matcher.find()) {
            return -1;
        }
        String minor = matcher.group(2);
        int scaled = minor.length() == 1 ? Integer.parseInt(minor) * 10 : Integer.parseInt(minor.substring(0, 2));
        return Integer.parseInt(matcher.group(1)) * 100 + scaled;
    }

    private static String os(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.startsWith("windows")) {
            return "WINDOWS";
        }
        if (lower.contains("mac") || lower.contains("darwin")) {
            return "MAC";
        }
        return lower.contains("linux") ? "LINUX" : "OTHER";
    }

    // AMD's Windows driver still reports "ATI Technologies Inc.". "ati" alone would also match "Corporation".
    static String vendor(String vendor) {
        String lower = vendor.toLowerCase(Locale.ROOT);
        if (lower.startsWith("ati ") || lower.contains("ati technologies")) {
            return "ATI";
        }
        if (lower.contains("amd") || lower.contains("advanced micro devices")) {
            return "AMD";
        }
        if (lower.contains("nvidia")) {
            return "NVIDIA";
        }
        if (lower.contains("intel")) {
            return "INTEL";
        }
        if (lower.contains("mesa")) {
            return "MESA";
        }
        return lower.contains("x.org") ? "XORG" : "OTHER";
    }

    static String renderer(String renderer) {
        String lower = renderer.toLowerCase(Locale.ROOT);
        if (lower.contains("radeon")) {
            return "RADEON";
        }
        if (lower.contains("geforce")) {
            return "GEFORCE";
        }
        if (lower.contains("quadro")) {
            return "QUADRO";
        }
        if (lower.contains("intel")) {
            return "INTEL";
        }
        if (lower.contains("gallium")) {
            return "GALLIUM";
        }
        return lower.contains("mesa") ? "MESA" : "OTHER";
    }
}
