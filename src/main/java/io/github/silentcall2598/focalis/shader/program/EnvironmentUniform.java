// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.shader.program;

import javax.annotation.Nullable;

/**
 * The legacy world and environment uniforms world programs get, with the names and types of the OptiFine shader
 * documentation. Each one belongs to the update that sets it, see {@link Update}.
 */
public enum EnvironmentUniform {

    WORLD_TIME("worldTime", Type.INT, Update.FRAME),
    WORLD_DAY("worldDay", Type.INT, Update.FRAME),
    MOON_PHASE("moonPhase", Type.INT, Update.FRAME),
    SUN_ANGLE("sunAngle", Type.FLOAT, Update.FRAME),
    SHADOW_ANGLE("shadowAngle", Type.FLOAT, Update.FRAME),
    RAIN_STRENGTH("rainStrength", Type.FLOAT, Update.FRAME),
    SKY_COLOR("skyColor", Type.VEC3, Update.FRAME),
    EYE_BRIGHTNESS("eyeBrightness", Type.IVEC2, Update.FRAME),
    SUN_POSITION("sunPosition", Type.VEC3, Update.PASS),
    MOON_POSITION("moonPosition", Type.VEC3, Update.PASS),
    SHADOW_LIGHT_POSITION("shadowLightPosition", Type.VEC3, Update.PASS),
    UP_POSITION("upPosition", Type.VEC3, Update.PASS),
    IS_EYE_IN_WATER("isEyeInWater", Type.INT, Update.PASS),
    FOG_MODE("fogMode", Type.INT, Update.BIND),
    FOG_START("fogStart", Type.FLOAT, Update.BIND),
    FOG_END("fogEnd", Type.FLOAT, Update.BIND),
    FOG_DENSITY("fogDensity", Type.FLOAT, Update.BIND),
    FOG_COLOR("fogColor", Type.VEC3, Update.BIND);

    public enum Type {
        INT(0x1404, "an int"),
        FLOAT(0x1406, "a float"),
        VEC3(0x8B51, "a vec3"),
        IVEC2(0x8B53, "an ivec2");

        final int glType;
        final String description;

        Type(int glType, String description) {
            this.glType = glType;
            this.description = description;
        }
    }

    public enum Update {
        /** World values, taken once per displayed frame and the same in every world pass of it. */
        FRAME,
        /** Values that depend on the camera of one world pass, like positions in eye space. */
        PASS,
        /** Draw state, taken from Minecraft's fog every time a stage binds the program. */
        BIND
    }

    private final String uniformName;
    private final Type type;
    private final Update update;

    EnvironmentUniform(String uniformName, Type type, Update update) {
        this.uniformName = uniformName;
        this.type = type;
        this.update = update;
    }

    public String uniformName() {
        return uniformName;
    }

    public Type type() {
        return type;
    }

    public Update update() {
        return update;
    }

    @Nullable
    static EnvironmentUniform named(String uniformName) {
        for (EnvironmentUniform uniform : values()) {
            if (uniform.uniformName.equals(uniformName)) {
                return uniform;
            }
        }
        return null;
    }
}
