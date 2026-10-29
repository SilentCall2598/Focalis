// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.shader.pack;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
    NEAR("near"),
    FAR("far"),
    NIGHT_VISION("nightVision"),
    BLINDNESS("blindness"),
    SCREEN_BRIGHTNESS("screenBrightness"),
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

    public String baseName() {
        return base;
    }

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
