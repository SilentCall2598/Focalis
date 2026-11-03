// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.render.state;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.ATIMeminfo;
import org.lwjgl.opengl.ContextCapabilities;
import org.lwjgl.opengl.EXTTextureFilterAnisotropic;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GLContext;
import org.lwjgl.opengl.NVXGpuMemoryInfo;

import javax.annotation.Nullable;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class GlContextInfo {

    public static final int UNKNOWN = -1;

    private static final Pattern VERSION_PREFIX = Pattern.compile("^(\\d+)\\.(\\d+)");

    private final String vendor;
    private final String renderer;
    private final String version;
    @Nullable
    private final String shadingLanguageVersion;
    private final int majorVersion;
    private final int minorVersion;
    private final Set<GlFeature> features;
    private final int maxTextureSize;
    private final int maxTextureImageUnits;
    private final int maxDrawBuffers;
    private final int maxColorAttachments;
    private final int maxSamples;
    private final float maxAnisotropy;
    private final int dedicatedVideoMemoryKiB;
    private final int availableVideoMemoryKiB;
    private final List<String> extensions;

    private GlContextInfo(ContextCapabilities caps) {
        vendor = orUnknown(GL11.glGetString(GL11.GL_VENDOR));
        renderer = orUnknown(GL11.glGetString(GL11.GL_RENDERER));
        version = orUnknown(GL11.glGetString(GL11.GL_VERSION));
        shadingLanguageVersion = caps.OpenGL20 ? GL11.glGetString(GL20.GL_SHADING_LANGUAGE_VERSION) : null;

        Matcher matcher = VERSION_PREFIX.matcher(version);
        boolean parsed = matcher.find();
        majorVersion = parsed ? Integer.parseInt(matcher.group(1)) : UNKNOWN;
        minorVersion = parsed ? Integer.parseInt(matcher.group(2)) : UNKNOWN;

        features = EnumSet.noneOf(GlFeature.class);
        for (GlFeature feature : GlFeature.values()) {
            if (feature.isSupported(caps)) {
                features.add(feature);
            }
        }

        maxTextureSize = GL11.glGetInteger(GL11.GL_MAX_TEXTURE_SIZE);
        maxTextureImageUnits = caps.OpenGL20 ? GL11.glGetInteger(GL20.GL_MAX_TEXTURE_IMAGE_UNITS) : UNKNOWN;
        maxDrawBuffers = caps.OpenGL20 ? GL11.glGetInteger(GL20.GL_MAX_DRAW_BUFFERS) : UNKNOWN;
        maxColorAttachments = has(GlFeature.FRAMEBUFFER_OBJECT)
                ? GL11.glGetInteger(GL30.GL_MAX_COLOR_ATTACHMENTS) : UNKNOWN;
        maxSamples = caps.OpenGL30 || caps.GL_ARB_framebuffer_object
                ? GL11.glGetInteger(GL30.GL_MAX_SAMPLES) : UNKNOWN;
        maxAnisotropy = has(GlFeature.ANISOTROPIC_FILTERING)
                ? GL11.glGetFloat(EXTTextureFilterAnisotropic.GL_MAX_TEXTURE_MAX_ANISOTROPY_EXT) : UNKNOWN;

        if (caps.GL_NVX_gpu_memory_info) {
            dedicatedVideoMemoryKiB = GL11.glGetInteger(NVXGpuMemoryInfo.GL_GPU_MEMORY_INFO_DEDICATED_VIDMEM_NVX);
            availableVideoMemoryKiB = GL11.glGetInteger(NVXGpuMemoryInfo.GL_GPU_MEMORY_INFO_CURRENT_AVAILABLE_VIDMEM_NVX);
        } else if (caps.GL_ATI_meminfo) {
            IntBuffer values = BufferUtils.createIntBuffer(16);
            GL11.glGetInteger(ATIMeminfo.GL_TEXTURE_FREE_MEMORY_ATI, values);
            dedicatedVideoMemoryKiB = UNKNOWN;
            availableVideoMemoryKiB = values.get(0);
        } else {
            dedicatedVideoMemoryKiB = UNKNOWN;
            availableVideoMemoryKiB = UNKNOWN;
        }

        extensions = queryExtensions(caps);
    }

    private GlContextInfo(String vendor, String renderer, String version, @Nullable String shadingLanguageVersion,
            Set<GlFeature> features, List<String> extensions) {
        this.vendor = vendor;
        this.renderer = renderer;
        this.version = version;
        this.shadingLanguageVersion = shadingLanguageVersion;
        Matcher matcher = VERSION_PREFIX.matcher(version);
        boolean parsed = matcher.find();
        majorVersion = parsed ? Integer.parseInt(matcher.group(1)) : UNKNOWN;
        minorVersion = parsed ? Integer.parseInt(matcher.group(2)) : UNKNOWN;
        this.features = Collections.unmodifiableSet(new HashSet<>(features));
        maxTextureSize = UNKNOWN;
        maxTextureImageUnits = UNKNOWN;
        maxDrawBuffers = UNKNOWN;
        maxColorAttachments = UNKNOWN;
        maxSamples = UNKNOWN;
        maxAnisotropy = UNKNOWN;
        dedicatedVideoMemoryKiB = UNKNOWN;
        availableVideoMemoryKiB = UNKNOWN;
        List<String> sorted = new ArrayList<>(extensions);
        Collections.sort(sorted);
        this.extensions = Collections.unmodifiableList(sorted);
    }

    public static GlContextInfo capture() {
        return new GlContextInfo(GLContext.getCapabilities());
    }

    public static GlContextInfo describe(String vendor, String renderer, String version,
            @Nullable String shadingLanguageVersion, Set<GlFeature> features, List<String> extensions) {
        return new GlContextInfo(vendor, renderer, version, shadingLanguageVersion, features, extensions);
    }

    private static List<String> queryExtensions(ContextCapabilities caps) {
        List<String> names = new ArrayList<>();
        if (caps.OpenGL30) {
            int count = GL11.glGetInteger(GL30.GL_NUM_EXTENSIONS);
            for (int i = 0; i < count; i++) {
                String name = GL30.glGetStringi(GL11.GL_EXTENSIONS, i);
                if (name != null) {
                    names.add(name);
                }
            }
        } else {
            String all = GL11.glGetString(GL11.GL_EXTENSIONS);
            if (all != null && !all.trim().isEmpty()) {
                names.addAll(Arrays.asList(all.trim().split("\\s+")));
            }
        }
        Collections.sort(names);
        return Collections.unmodifiableList(names);
    }

    private static String orUnknown(@Nullable String value) {
        return value == null ? "unknown" : value;
    }

    public String vendor() {
        return vendor;
    }

    public String renderer() {
        return renderer;
    }

    public String version() {
        return version;
    }

    @Nullable
    public String shadingLanguageVersion() {
        return shadingLanguageVersion;
    }

    public int majorVersion() {
        return majorVersion;
    }

    public int minorVersion() {
        return minorVersion;
    }

    public boolean has(GlFeature feature) {
        return features.contains(feature);
    }

    public int maxTextureSize() {
        return maxTextureSize;
    }

    public int maxTextureImageUnits() {
        return maxTextureImageUnits;
    }

    public int maxDrawBuffers() {
        return maxDrawBuffers;
    }

    public int maxColorAttachments() {
        return maxColorAttachments;
    }

    public int maxSamples() {
        return maxSamples;
    }

    public float maxAnisotropy() {
        return maxAnisotropy;
    }

    public int dedicatedVideoMemoryKiB() {
        return dedicatedVideoMemoryKiB;
    }

    public int availableVideoMemoryKiB() {
        return availableVideoMemoryKiB;
    }

    public List<String> extensions() {
        return extensions;
    }
}
