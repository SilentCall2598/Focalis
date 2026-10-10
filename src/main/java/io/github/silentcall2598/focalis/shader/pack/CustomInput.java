// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.shader.pack;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The built-in values custom uniform expressions can read, all as floats like the format documents. These are the
 * values Focalis has for its standard uniforms, read from the same inputs, plus the documented biome_precipitation
 * parameter, and never the per draw fog values the format excludes. A vector is read one component at a time, like
 * {@code eyeBrightness.y}.
 */
public enum CustomInput {
    VIEW_WIDTH("viewWidth"),
    VIEW_HEIGHT("viewHeight"),
    ASPECT_RATIO("aspectRatio"),
    FRAME_COUNTER("frameCounter"),
    FRAME_TIME("frameTime"),
    FRAME_TIME_COUNTER("frameTimeCounter"),
    WORLD_TIME("worldTime"),
    WORLD_DAY("worldDay"),
    MOON_PHASE("moonPhase"),
    SUN_ANGLE("sunAngle"),
    SHADOW_ANGLE("shadowAngle"),
    RAIN_STRENGTH("rainStrength"),
    IS_EYE_IN_WATER("isEyeInWater"),
    EYE_ALTITUDE("eyeAltitude"),
    BIOME_PRECIPITATION("biome_precipitation"),
    EYE_BRIGHTNESS_X("eyeBrightness", "x"),
    EYE_BRIGHTNESS_Y("eyeBrightness", "y"),
    SKY_COLOR_R("skyColor", "r", "x"),
    SKY_COLOR_G("skyColor", "g", "y"),
    SKY_COLOR_B("skyColor", "b", "z"),
    CAMERA_POSITION_X("cameraPosition", "x"),
    CAMERA_POSITION_Y("cameraPosition", "y"),
    CAMERA_POSITION_Z("cameraPosition", "z"),
    PREVIOUS_CAMERA_POSITION_X("previousCameraPosition", "x"),
    PREVIOUS_CAMERA_POSITION_Y("previousCameraPosition", "y"),
    PREVIOUS_CAMERA_POSITION_Z("previousCameraPosition", "z");

    // values() copies the array, and lookups happen for every name in every expression.
    private static final CustomInput[] ALL = values();
    private static final Set<String> BASE_NAMES = new HashSet<>();

    static {
        for (CustomInput input : ALL) {
            BASE_NAMES.add(input.base);
        }
    }

    private final String base;
    private final List<String> components;

    CustomInput(String base, String... components) {
        this.base = base;
        this.components = Collections.unmodifiableList(Arrays.asList(components));
    }

    /** The uniform name, without a component. */
    public String baseName() {
        return base;
    }

    /** The input for a name like {@code frameTime} or {@code cameraPosition.x}, or null when there is none. */
    @Nullable
    static CustomInput find(String name) {
        int dot = name.indexOf('.');
        String base = dot < 0 ? name : name.substring(0, dot);
        String component = dot < 0 ? null : name.substring(dot + 1);
        for (CustomInput input : ALL) {
            if (!input.base.equals(base)) {
                continue;
            }
            if (component == null ? input.components.isEmpty() : input.components.contains(component)) {
                return input;
            }
        }
        return null;
    }

    static boolean isBaseName(String name) {
        return BASE_NAMES.contains(name);
    }
}
