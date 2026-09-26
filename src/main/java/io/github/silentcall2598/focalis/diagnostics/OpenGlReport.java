// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.diagnostics;

import io.github.silentcall2598.focalis.core.FocalisLog;
import io.github.silentcall2598.focalis.render.state.GlContextInfo;
import io.github.silentcall2598.focalis.render.state.GlFeature;
import net.minecraft.client.renderer.OpenGlHelper;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;

public final class OpenGlReport {

    private OpenGlReport() {
    }

    // Client thread only, because it also reads Minecraft's render settings.
    public static void log(GlContextInfo gl, boolean includeExtensions) {
        Logger log = FocalisLog.LOGGER;
        log.info("OpenGL renderer: {} ({})", gl.renderer(), gl.vendor());
        log.info("OpenGL version: {}, GLSL {}", gl.version(),
                gl.shadingLanguageVersion() == null ? "unsupported" : gl.shadingLanguageVersion());
        log.info("OpenGL limits: max texture size {}, texture units {}, draw buffers {}, color attachments {},"
                        + " MSAA samples {}, max anisotropy {}",
                value(gl.maxTextureSize()), value(gl.maxTextureImageUnits()), value(gl.maxDrawBuffers()),
                value(gl.maxColorAttachments()), value(gl.maxSamples()),
                gl.maxAnisotropy() == GlContextInfo.UNKNOWN ? "n/a" : String.valueOf(gl.maxAnisotropy()));

        List<String> supported = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        for (GlFeature feature : GlFeature.values()) {
            (gl.has(feature) ? supported : missing).add(feature.displayName());
        }
        log.info("OpenGL capabilities: {}; missing: {}", join(supported), join(missing));

        if (gl.dedicatedVideoMemoryKiB() != GlContextInfo.UNKNOWN || gl.availableVideoMemoryKiB() != GlContextInfo.UNKNOWN) {
            log.info("Video memory: {} MiB dedicated, {} MiB free",
                    mebibytes(gl.dedicatedVideoMemoryKiB()), mebibytes(gl.availableVideoMemoryKiB()));
        }

        log.info("Minecraft render settings: framebuffers {}, VBOs {}, shaders {}",
                OpenGlHelper.isFramebufferEnabled() ? "on" : "off",
                OpenGlHelper.useVbo() ? "on" : "off",
                OpenGlHelper.shadersSupported ? "supported" : "unsupported");

        if (includeExtensions) {
            log.info("OpenGL extensions ({}): {}", gl.extensions().size(), String.join(" ", gl.extensions()));
        }
    }

    private static String value(int value) {
        return value == GlContextInfo.UNKNOWN ? "n/a" : String.valueOf(value);
    }

    private static String mebibytes(int kibibytes) {
        return kibibytes == GlContextInfo.UNKNOWN ? "n/a" : String.valueOf(kibibytes / 1024);
    }

    private static String join(List<String> names) {
        return names.isEmpty() ? "none" : String.join(", ", names);
    }
}
