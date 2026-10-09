// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.shader.program;

import javax.annotation.Nullable;
import java.util.Objects;

/**
 * The world and environment values every world program of a session sees. The world values are taken once per
 * displayed frame, before its first world stage draws, so every pass of the frame shares them. The values in eye space
 * and the camera's medium belong to one world pass and are taken right after its camera. Nothing is smoothed or carried
 * over from earlier frames, so a new world or dimension shows its own values from its first frame. Client thread only.
 *
 * <p>The sun and moon follow the path vanilla's surface sky draws them on, 100 blocks out and turned by the celestial
 * angle. Dimensions that draw no sun still report the angle their world provider gives, like 0.5 in the Nether and 0
 * in the End.
 */
public final class EnvironmentInputs {

    public static final int TICKS_PER_DAY = 24000;
    /** isEyeInWater when the camera is in neither water nor lava. */
    public static final int MEDIUM_AIR = 0;
    public static final int MEDIUM_WATER = 1;
    public static final int MEDIUM_LAVA = 2;
    /** The biome precipitation types, with the numbers of the format's PPT_NONE, PPT_RAIN and PPT_SNOW. */
    public static final int PRECIPITATION_NONE = 0;
    public static final int PRECIPITATION_RAIN = 1;
    public static final int PRECIPITATION_SNOW = 2;

    // Vanilla draws the sun quad this far out and the moon quad the same distance the other way.
    private static final double SKY_DISTANCE = 100;

    // The FrameInputs sequence the world values were taken in, 0 while there are none.
    private long frame;
    private int worldTime;
    private int worldDay;
    private int moonPhase;
    private float celestialAngle;
    private float sunAngle;
    private float shadowAngle;
    private float rainStrength;
    private float skyRed;
    private float skyGreen;
    private float skyBlue;
    private int blockBrightness;
    private int skyBrightness;

    // The CameraSnapshot sequence the pass values were taken for, 0 while there are none.
    private long pass;
    private long passFrame;
    private final float[] sunPosition = new float[3];
    private final float[] moonPosition = new float[3];
    private final float[] upPosition = new float[3];
    private int medium;
    private float eyeAltitude;
    private int precipitation;

    /** Whether the world values of the displayed frame with this FrameInputs sequence were taken already. */
    public boolean hasFrame(long frame) {
        return frame != 0 && this.frame == frame;
    }

    /**
     * Takes the world values of a displayed frame.
     *
     * @param frame the FrameInputs sequence of that frame
     * @param dayTime the world's time of day in ticks, what the sky uses, not the total world time
     * @param celestialAngle the world's celestial angle at the frame's partial tick, 0 at noon in the Overworld
     * @param rainStrength the world's rain strength at the frame's partial tick
     * @param packedLight the view entity's brightness as Minecraft packs it, sky light in the high 16 bits and block
     *     light in the low ones, each light level times 16
     */
    public void captureFrame(long frame, long dayTime, int moonPhase, float celestialAngle, float rainStrength,
            float skyRed, float skyGreen, float skyBlue, int packedLight) {
        if (frame <= 0) {
            throw new IllegalArgumentException("No frame " + frame);
        }
        worldTime = (int) Math.floorMod(dayTime, TICKS_PER_DAY);
        long day = Math.floorDiv(dayTime, TICKS_PER_DAY);
        worldDay = (int) Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, day));
        this.moonPhase = moonPhase;
        this.celestialAngle = celestialAngle;
        // A quarter day after the celestial angle, so the sun rises at 0 and sets at 0.5.
        float angle = celestialAngle + 0.25f;
        float sun = angle - (float) Math.floor(angle);
        sunAngle = sun >= 1 ? 0 : sun;
        shadowAngle = sunAngle < 0.5f ? sunAngle : sunAngle - 0.5f;
        // The server can send anything, but the documented range is 0 to 1.
        this.rainStrength = Math.max(0, Math.min(1, rainStrength));
        this.skyRed = skyRed;
        this.skyGreen = skyGreen;
        this.skyBlue = skyBlue;
        blockBrightness = packedLight & 0xFFFF;
        skyBrightness = packedLight >>> 16 & 0xFFFF;
        this.frame = frame;
    }

    /**
     * Takes the values of the world pass {@code camera} belongs to, after the world values of its frame.
     *
     * @param medium {@link #MEDIUM_WATER} or {@link #MEDIUM_LAVA} when vanilla fogs the camera as being in one,
     *     otherwise {@link #MEDIUM_AIR}
     * @param precipitation one of the PRECIPITATION constants, for the biome the camera's view entity is in
     */
    public void capturePass(CameraSnapshot camera, int medium, int precipitation) {
        Objects.requireNonNull(camera, "camera");
        if (!hasFrame(camera.frame())) {
            throw new IllegalStateException("The world values of frame " + camera.frame() + " weren't taken yet");
        }
        if (medium < MEDIUM_AIR || medium > MEDIUM_LAVA) {
            throw new IllegalArgumentException("No medium " + medium);
        }
        if (precipitation < PRECIPITATION_NONE || precipitation > PRECIPITATION_SNOW) {
            throw new IllegalArgumentException("No precipitation " + precipitation);
        }
        // The sky turns -90 degrees around y and then by the celestial angle around x, both in degrees as floats.
        double radians = Math.toRadians(celestialAngle * 360.0f);
        double x = -Math.sin(radians) * SKY_DISTANCE;
        double y = Math.cos(radians) * SKY_DISTANCE;
        float[] modelView = camera.modelView;
        transform(modelView, x, y, 0, 1, sunPosition);
        transform(modelView, -x, -y, 0, 1, moonPosition);
        transform(modelView, 0, SKY_DISTANCE, 0, 0, upPosition);
        this.medium = medium;
        // The view entity's height, which is where the camera snapshot stands.
        eyeAltitude = (float) camera.y();
        this.precipitation = precipitation;
        passFrame = camera.frame();
        pass = camera.sequence();
    }

    // Column by column, like GL keeps it.
    private static void transform(float[] m, double x, double y, double z, double w, float[] into) {
        into[0] = (float) (m[0] * x + m[4] * y + m[8] * z + m[12] * w);
        into[1] = (float) (m[1] * x + m[5] * y + m[9] * z + m[13] * w);
        into[2] = (float) (m[2] * x + m[6] * y + m[10] * z + m[14] * w);
    }

    /** Whether the values of the world pass {@code camera} belongs to were taken. */
    public boolean readyFor(@Nullable CameraSnapshot camera) {
        return camera != null && pass == camera.sequence() && passFrame == camera.frame() && frame == passFrame;
    }

    /** Forgets everything, so nothing can be read until the next frame's values are taken. */
    public void clear() {
        frame = 0;
        pass = 0;
        passFrame = 0;
    }

    /** The FrameInputs sequence of the world values, 0 when there are none. */
    public long frame() {
        return frame;
    }

    /** The CameraSnapshot sequence of the pass values, 0 when there are none. */
    public long pass() {
        return pass;
    }

    /** Time of day in ticks, 0 to 23999. */
    public int worldTime() {
        return worldTime;
    }

    /** Whole days of the time of day. */
    public int worldDay() {
        return worldDay;
    }

    public int moonPhase() {
        return moonPhase;
    }

    public float celestialAngle() {
        return celestialAngle;
    }

    /** 0 to 1, 0 at sunrise, 0.25 at noon and 0.5 at sunset. */
    public float sunAngle() {
        return sunAngle;
    }

    /** sunAngle while the sun is up, and the moon's angle the same way while it isn't. 0 to 0.5. */
    public float shadowAngle() {
        return shadowAngle;
    }

    public float rainStrength() {
        return rainStrength;
    }

    public float skyRed() {
        return skyRed;
    }

    public float skyGreen() {
        return skyGreen;
    }

    public float skyBlue() {
        return skyBlue;
    }

    /** Block light at the view entity's eyes times 16, 0 to 240. */
    public int blockBrightness() {
        return blockBrightness;
    }

    /** Sky light at the view entity's eyes times 16, 0 to 240. */
    public int skyBrightness() {
        return skyBrightness;
    }

    /** Whether the sun is above the horizon, which picks the sun or the moon as the shadow light. */
    public boolean sunUp() {
        return sunAngle < 0.5f;
    }

    float[] sunPosition() {
        return sunPosition;
    }

    float[] moonPosition() {
        return moonPosition;
    }

    float[] upPosition() {
        return upPosition;
    }

    /** The Y of the view entity the pass was drawn for, as eyeAltitude. */
    public float eyeAltitude() {
        return eyeAltitude;
    }

    /** The precipitation type of the biome the pass's view entity is in, one of the PRECIPITATION constants. */
    public int precipitation() {
        return precipitation;
    }

    public int medium() {
        return medium;
    }
}
