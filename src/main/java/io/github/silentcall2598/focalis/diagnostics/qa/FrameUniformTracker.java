// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.diagnostics.qa;

import io.github.silentcall2598.focalis.shader.program.BuiltWorldPrograms;
import io.github.silentcall2598.focalis.shader.program.FrameInputs;
import io.github.silentcall2598.focalis.shader.program.FrameUniform;
import io.github.silentcall2598.focalis.shader.program.ShaderProgram;
import io.github.silentcall2598.focalis.shader.program.WorldProgramInputs;
import io.github.silentcall2598.focalis.shader.program.WorldSampler;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL20;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Reads the view and frame uniforms back from the program a bound stage just made current and judges them against
 * the probe's own clock, taken right before the features at every FRAME START, and the surface the pass draws into.
 * Client thread only, with the GL context current.
 */
final class FrameUniformTracker {

    private static final FrameUniform[] UNIFORMS = FrameUniform.values();
    // The probe and Focalis each read the clock once per FRAME START, a few listeners apart.
    private static final double TIME_TOLERANCE_SECONDS = 0.002;
    private static final int SAMPLE_INTERVAL = 500;

    private final QaReport.FrameUniforms report;
    private final Map<Integer, WorldProgramInputs> inputs = new HashMap<>();
    private final Map<Integer, String> names = new HashMap<>();
    private final FloatBuffer floats = BufferUtils.createFloatBuffer(16);
    private final IntBuffer ints = BufferUtils.createIntBuffer(16);

    // The probe's view of the current frame.
    private int frame;
    private long frameNanos;
    private long previousFrameNanos;
    private int displayWidth;
    private int displayHeight;
    private int worldPasses;

    // The first readback anchors the counter and the run time to the probe's frames and clock.
    private boolean anchored;
    private int counterOffset;
    private double timeAnchor;
    private long nanosAnchor;

    // What the first readback of the current frame held, NaN where its program has no such uniform.
    private int valuesFrame = -1;
    private final float[] frameValues = new float[UNIFORMS.length];
    private final float[] read = new float[UNIFORMS.length];
    private final Set<Integer> programsThisFrame = new HashSet<>();
    private int firstPassThisFrame;
    private boolean severalPassesCounted;
    private int lastReadbackFrame = -1;

    FrameUniformTracker(QaReport.FrameUniforms report) {
        this.report = report;
    }

    void programsBuilt(BuiltWorldPrograms programs) {
        for (BuiltWorldPrograms.Build build : programs.builds()) {
            ShaderProgram program = build.program();
            WorldProgramInputs programInputs = build.inputs();
            if (program != null && programInputs != null) {
                inputs.put(program.id(), programInputs);
                names.put(program.id(), program.name());
            }
        }
    }

    void frameStarted(int frameNumber, long nanos, int width, int height) {
        frame = frameNumber;
        previousFrameNanos = frameNanos;
        frameNanos = nanos;
        displayWidth = width;
        displayHeight = height;
    }

    void worldStarted() {
        worldPasses++;
    }

    /**
     * Checks the program the features left current right after a bound stage START.
     *
     * @param surfaceWidth the width of what the pass draws into, the world target or Minecraft's framebuffer
     */
    void check(int program, String where, int surfaceWidth, int surfaceHeight) {
        WorldProgramInputs programInputs = inputs.get(program);
        if (programInputs == null) {
            return;
        }
        String name = names.get(program);
        recheckSamplers(program, name, programInputs);
        boolean any = false;
        for (FrameUniform uniform : UNIFORMS) {
            int location = programInputs.location(uniform);
            read[uniform.ordinal()] = location < 0 ? Float.NaN : readValue(program, location, uniform.integer());
            any |= location >= 0;
        }
        if (!any) {
            return;
        }
        report.readbacks++;
        String problem = judge(surfaceWidth, surfaceHeight);
        if (problem != null) {
            report.mismatches++;
            QaReport.addCapped(report.problems, name + " at " + where + " in frame " + frame + ": " + problem
                    + ", held " + describe(read));
        }
        compareWithFrame(program, name, where);
        float width = read[FrameUniform.VIEW_WIDTH.ordinal()];
        float height = read[FrameUniform.VIEW_HEIGHT.ordinal()];
        boolean newSize = !Float.isNaN(width) && !Float.isNaN(height)
                && report.viewSizes.add((int) width + "x" + (int) height);
        if (newSize || report.readbacks % SAMPLE_INTERVAL == 1) {
            QaReport.addCapped(report.samples, "frame " + frame + " pass " + worldPasses + " " + name + " at "
                    + where + ": " + describe(read) + ", display " + displayWidth + "x" + displayHeight
                    + ", surface " + surfaceWidth + "x" + surfaceHeight);
        }
        if (lastReadbackFrame >= 0) {
            report.maxReadbackGapFrames = Math.max(report.maxReadbackGapFrames, frame - lastReadbackFrame);
        }
        lastReadbackFrame = frame;
    }

    private float readValue(int program, int location, boolean integer) {
        if (integer) {
            GL20.glGetUniform(program, location, ints);
            return ints.get(0);
        }
        GL20.glGetUniform(program, location, floats);
        return floats.get(0);
    }

    // Returns what's wrong, or null.
    private String judge(int surfaceWidth, int surfaceHeight) {
        for (float value : read) {
            if (Float.isInfinite(value) || (value < 0)) {
                return "a value is negative or infinite";
            }
        }
        float width = read[FrameUniform.VIEW_WIDTH.ordinal()];
        float height = read[FrameUniform.VIEW_HEIGHT.ordinal()];
        if (!Float.isNaN(width) && (width != displayWidth || width != surfaceWidth)) {
            return "viewWidth isn't the display width " + displayWidth + " or the surface width " + surfaceWidth;
        }
        if (!Float.isNaN(height) && (height != displayHeight || height != surfaceHeight)) {
            return "viewHeight isn't the display height " + displayHeight + " or the surface height " + surfaceHeight;
        }
        float aspect = read[FrameUniform.ASPECT_RATIO.ordinal()];
        float expectedAspect = (float) displayWidth / displayHeight;
        if (!Float.isNaN(aspect) && Math.abs(aspect - expectedAspect) > 1e-6f * expectedAspect) {
            return "aspectRatio isn't " + expectedAspect;
        }
        float frameTime = read[FrameUniform.FRAME_TIME.ordinal()];
        if (!Float.isNaN(frameTime) && previousFrameNanos != 0) {
            double expected = (frameNanos - previousFrameNanos) / 1e9;
            double deviation = Math.abs(frameTime - expected);
            report.maxFrameTimeDeviationMs = Math.max(report.maxFrameTimeDeviationMs, deviation * 1000);
            if (deviation > TIME_TOLERANCE_SECONDS) {
                return "frameTime is off the probe's " + expected + " s";
            }
        }
        float counter = read[FrameUniform.FRAME_COUNTER.ordinal()];
        float timeCounter = read[FrameUniform.FRAME_TIME_COUNTER.ordinal()];
        if (!anchored && !Float.isNaN(counter) && !Float.isNaN(timeCounter)) {
            anchored = true;
            counterOffset = frame - (int) counter;
            timeAnchor = timeCounter;
            nanosAnchor = frameNanos;
            report.firstCounter = (int) counter;
            report.firstTimeCounter = timeCounter;
        }
        if (!Float.isNaN(counter)) {
            int expected = Math.floorMod(frame - counterOffset, FrameInputs.FRAME_COUNTER_PERIOD);
            report.lastCounter = (int) counter;
            if (anchored && (int) counter != expected) {
                return "frameCounter isn't " + expected + " after " + (frame - counterOffset) + " probe frames";
            }
        }
        if (!Float.isNaN(timeCounter)) {
            report.lastTimeCounter = timeCounter;
            if (timeCounter >= FrameInputs.TIME_COUNTER_PERIOD_SECONDS) {
                return "frameTimeCounter isn't below " + FrameInputs.TIME_COUNTER_PERIOD_SECONDS;
            }
            if (anchored) {
                double period = FrameInputs.TIME_COUNTER_PERIOD_SECONDS;
                double expected = (timeAnchor + (frameNanos - nanosAnchor) / 1e9) % period;
                double deviation = Math.abs(timeCounter - expected);
                deviation = Math.min(deviation, period - deviation);
                report.maxTimeCounterDeviationMs = Math.max(report.maxTimeCounterDeviationMs, deviation * 1000);
                if (deviation > TIME_TOLERANCE_SECONDS) {
                    return "frameTimeCounter is off the probe's " + expected + " s";
                }
            }
        }
        return null;
    }

    private void compareWithFrame(int program, String name, String where) {
        if (valuesFrame != frame) {
            valuesFrame = frame;
            System.arraycopy(read, 0, frameValues, 0, read.length);
            programsThisFrame.clear();
            programsThisFrame.add(program);
            firstPassThisFrame = worldPasses;
            severalPassesCounted = false;
            return;
        }
        report.consistencyChecks++;
        for (int i = 0; i < read.length; i++) {
            if (!Float.isNaN(read[i]) && !Float.isNaN(frameValues[i]) && read[i] != frameValues[i]) {
                report.inconsistencies++;
                QaReport.addCapped(report.problems, name + " at " + where + " in frame " + frame + " held "
                        + describe(read) + " after another program held " + describe(frameValues));
                break;
            }
        }
        if (programsThisFrame.add(program) && programsThisFrame.size() == 2) {
            report.framesWithSeveralPrograms++;
        }
        if (worldPasses != firstPassThisFrame && !severalPassesCounted) {
            severalPassesCounted = true;
            report.framesWithSeveralPasses++;
        }
    }

    // The samplers were set once at build time and frame updates must never touch them.
    private void recheckSamplers(int program, String name, WorldProgramInputs programInputs) {
        for (WorldSampler sampler : WorldSampler.values()) {
            int location = programInputs.location(sampler);
            if (location < 0) {
                continue;
            }
            GL20.glGetUniform(program, location, ints);
            int unit = ints.get(0);
            report.samplerRechecks++;
            if (unit != sampler.unit()) {
                report.samplerMismatches++;
                QaReport.addCapped(report.problems, name + " holds " + unit + " in " + sampler.uniformName()
                        + " in frame " + frame + " instead of " + sampler.unit());
            }
        }
    }

    private static String describe(float[] values) {
        StringBuilder text = new StringBuilder();
        for (FrameUniform uniform : UNIFORMS) {
            float value = values[uniform.ordinal()];
            if (Float.isNaN(value)) {
                continue;
            }
            text.append(text.length() == 0 ? "" : " ").append(uniform.uniformName()).append('=');
            text.append(uniform.integer() ? String.valueOf((int) value) : String.format(Locale.ROOT, "%.6f", value));
        }
        return text.length() == 0 ? Arrays.toString(values) : text.toString();
    }
}
