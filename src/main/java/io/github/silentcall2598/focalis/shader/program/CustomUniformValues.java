// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.shader.program;

import io.github.silentcall2598.focalis.shader.pack.CustomContext;
import io.github.silentcall2598.focalis.shader.pack.CustomInput;
import io.github.silentcall2598.focalis.shader.pack.CustomUniforms;

import java.util.Arrays;
import java.util.Objects;

public final class CustomUniformValues implements CustomContext {

    private static final int INPUTS = CustomInput.values().length;

    private final CustomUniforms uniforms;
    private final float[] inputs = new float[INPUTS];
    private final float[] values;
    private final float[] smoothed;
    private final boolean[] started;
    private final long[] smoothedAt;
    private long now;
    private long evaluatedPass = -1;
    private long evaluatedCamera = -1;
    private long world = -1;
    private long generation;
    private long evaluations;
    private long smoothSteps;
    private long resets;

    public CustomUniformValues(CustomUniforms uniforms) {
        this.uniforms = Objects.requireNonNull(uniforms, "uniforms");
        values = new float[uniforms.declarations().size()];
        smoothed = new float[uniforms.smoothSites()];
        started = new boolean[uniforms.smoothSites()];
        smoothedAt = new long[uniforms.smoothSites()];
    }

    public CustomUniforms uniforms() {
        return uniforms;
    }

    public void prepare(FrameInputs frame, CameraSnapshot camera, EnvironmentInputs environment) {
        if (environment.pass() == evaluatedPass && camera.sequence() == evaluatedCamera) {
            return;
        }
        if (camera.world() != world) {
            Arrays.fill(started, false);
            world = camera.world();
            resets++;
        }
        float[] in = inputs;
        in[CustomInput.VIEW_WIDTH.ordinal()] = frame.viewWidth();
        in[CustomInput.VIEW_HEIGHT.ordinal()] = frame.viewHeight();
        in[CustomInput.ASPECT_RATIO.ordinal()] = frame.aspectRatio();
        in[CustomInput.FRAME_COUNTER.ordinal()] = frame.frameCounter();
        in[CustomInput.FRAME_TIME.ordinal()] = frame.frameTime();
        in[CustomInput.FRAME_TIME_COUNTER.ordinal()] = frame.frameTimeCounter();
        in[CustomInput.WORLD_TIME.ordinal()] = environment.worldTime();
        in[CustomInput.WORLD_DAY.ordinal()] = environment.worldDay();
        in[CustomInput.MOON_PHASE.ordinal()] = environment.moonPhase();
        in[CustomInput.SUN_ANGLE.ordinal()] = environment.sunAngle();
        in[CustomInput.SHADOW_ANGLE.ordinal()] = environment.shadowAngle();
        in[CustomInput.RAIN_STRENGTH.ordinal()] = environment.rainStrength();
        in[CustomInput.IS_EYE_IN_WATER.ordinal()] = environment.medium();
        in[CustomInput.EYE_ALTITUDE.ordinal()] = environment.eyeAltitude();
        in[CustomInput.NEAR.ordinal()] = EnvironmentInputs.NEAR;
        in[CustomInput.FAR.ordinal()] = environment.far();
        in[CustomInput.NIGHT_VISION.ordinal()] = environment.nightVision();
        in[CustomInput.BLINDNESS.ordinal()] = environment.blindness();
        in[CustomInput.SCREEN_BRIGHTNESS.ordinal()] = environment.screenBrightness();
        in[CustomInput.BIOME_PRECIPITATION.ordinal()] = environment.precipitation();
        in[CustomInput.EYE_BRIGHTNESS_X.ordinal()] = environment.blockBrightness();
        in[CustomInput.EYE_BRIGHTNESS_Y.ordinal()] = environment.skyBrightness();
        in[CustomInput.SKY_COLOR_R.ordinal()] = environment.skyRed();
        in[CustomInput.SKY_COLOR_G.ordinal()] = environment.skyGreen();
        in[CustomInput.SKY_COLOR_B.ordinal()] = environment.skyBlue();
        in[CustomInput.CAMERA_POSITION_X.ordinal()] = (float) camera.x();
        in[CustomInput.CAMERA_POSITION_Y.ordinal()] = (float) camera.y();
        in[CustomInput.CAMERA_POSITION_Z.ordinal()] = (float) camera.z();
        in[CustomInput.PREVIOUS_CAMERA_POSITION_X.ordinal()] = (float) camera.previousX();
        in[CustomInput.PREVIOUS_CAMERA_POSITION_Y.ordinal()] = (float) camera.previousY();
        in[CustomInput.PREVIOUS_CAMERA_POSITION_Z.ordinal()] = (float) camera.previousZ();
        now = frame.runNanos();
        uniforms.evaluate(this, values);
        evaluatedPass = environment.pass();
        evaluatedCamera = camera.sequence();
        generation++;
        evaluations++;
    }

    @Override
    public float input(int input) {
        return inputs[input];
    }

    @Override
    public float smooth(int site, float target, float fadeIn, float fadeOut) {
        if (!started[site]) {
            started[site] = true;
            smoothed[site] = target;
            smoothedAt[site] = now;
            return target;
        }
        long elapsed = now - smoothedAt[site];
        smoothedAt[site] = now;
        float current = smoothed[site];
        float fade = target > current ? fadeIn : fadeOut;
        if (fade <= 0) {
            smoothed[site] = target;
        } else if (elapsed > 0) {
            double remaining = Math.pow(2, -10 * (elapsed / 1e9) / fade);
            smoothed[site] = (float) (target + (current - target) * remaining);
            smoothSteps++;
        }
        return smoothed[site];
    }

    public float value(int declaration) {
        return values[declaration];
    }

    public long generation() {
        return generation;
    }

    public long evaluations() {
        return evaluations;
    }

    public long smoothSteps() {
        return smoothSteps;
    }

    public long resets() {
        return resets;
    }
}
