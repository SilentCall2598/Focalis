// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.shader.program;

import io.github.silentcall2598.focalis.shader.pack.CustomContext;
import io.github.silentcall2598.focalis.shader.pack.CustomInput;
import io.github.silentcall2598.focalis.shader.pack.CustomUniforms;

import java.util.Arrays;
import java.util.Objects;

/**
 * The current values of one session's custom uniforms and variables, and the state their smooth() calls keep. Every
 * world pass computes them at most once, when the first program that needs them binds, and all programs share the
 * result. Built-ins come from the same frame, camera and environment inputs the standard uniforms use.
 *
 * <p>Smoothed values move toward their target by {@code 2^(-10 dt / fade)} of the remaining gap, with dt the time
 * between frame starts, so extra world passes and programs in one frame don't move them. The first value of a call
 * site is taken as is, and a new client world or dimension starts every call site over. Client thread only.
 */
public final class CustomUniformValues implements CustomContext {

    private static final int INPUTS = CustomInput.values().length;

    private final CustomUniforms uniforms;
    private final float[] inputs = new float[INPUTS];
    private final float[] values;
    private final float[] smoothed;
    private final boolean[] started;
    private final long[] smoothedAt;
    private long now;
    // What the values were last computed for, so a world pass computes them only once.
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

    /**
     * Computes the values for the world pass {@code camera} and {@code environment} belong to, unless that was done
     * already. The frame has to be captured and the environment taken for this camera.
     */
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
        in[CustomInput.EYE_BRIGHTNESS_X.ordinal()] = environment.blockBrightness();
        in[CustomInput.EYE_BRIGHTNESS_Y.ordinal()] = environment.skyBrightness();
        in[CustomInput.SKY_COLOR_R.ordinal()] = environment.skyRed();
        in[CustomInput.SKY_COLOR_G.ordinal()] = environment.skyGreen();
        in[CustomInput.SKY_COLOR_B.ordinal()] = environment.skyBlue();
        // Programs get the position as floats too, so expressions see what the shaders see.
        in[CustomInput.CAMERA_POSITION_X.ordinal()] = (float) camera.x();
        in[CustomInput.CAMERA_POSITION_Y.ordinal()] = (float) camera.y();
        in[CustomInput.CAMERA_POSITION_Z.ordinal()] = (float) camera.z();
        in[CustomInput.PREVIOUS_CAMERA_POSITION_X.ordinal()] = (float) camera.previousX();
        in[CustomInput.PREVIOUS_CAMERA_POSITION_Y.ordinal()] = (float) camera.previousY();
        in[CustomInput.PREVIOUS_CAMERA_POSITION_Z.ordinal()] = (float) camera.previousZ();
        now = frame.runNanos();
        uniforms.evaluate(this, values);
        // Only marked once everything is in, so a throw never passes for a finished evaluation.
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

    /** The value of a declaration from the last evaluation, a bool as 1 or 0. */
    public float value(int declaration) {
        return values[declaration];
    }

    /** Goes up by one with every evaluation, so programs can tell whether they have the latest values. */
    public long generation() {
        return generation;
    }

    /** How many times the values were computed. */
    public long evaluations() {
        return evaluations;
    }

    /** How many times a smoothed value moved because time had passed. */
    public long smoothSteps() {
        return smoothSteps;
    }

    /** How many times the smoothing state started over, including the first world. */
    public long resets() {
        return resets;
    }
}
