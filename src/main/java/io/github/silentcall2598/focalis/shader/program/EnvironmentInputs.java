// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.shader.program;

import net.minecraft.util.math.MathHelper;

import javax.annotation.Nullable;
import java.util.Objects;

public final class EnvironmentInputs {

    public static final int TICKS_PER_DAY = 24000;
    public static final int MEDIUM_AIR = 0;
    public static final int MEDIUM_WATER = 1;
    public static final int MEDIUM_LAVA = 2;
    public static final int PRECIPITATION_NONE = 0;
    public static final int PRECIPITATION_RAIN = 1;
    public static final int PRECIPITATION_SNOW = 2;
    public static final float NEAR = 0.05f;

    private static final double SKY_DISTANCE = 100;

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
    private float far;
    private float nightVision;
    private float blindness;
    private float screenBrightness;
    private int atlasWidth;
    private int atlasHeight;

    private long pass;
    private long passFrame;
    private final float[] sunPosition = new float[3];
    private final float[] moonPosition = new float[3];
    private final float[] upPosition = new float[3];
    private int medium;
    private float eyeAltitude;
    private int precipitation;

    public boolean hasFrame(long frame) {
        return frame != 0 && this.frame == frame;
    }

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
        float angle = celestialAngle + 0.25f;
        float sun = angle - (float) Math.floor(angle);
        sunAngle = sun >= 1 ? 0 : sun;
        shadowAngle = sunAngle < 0.5f ? sunAngle : sunAngle - 0.5f;
        this.rainStrength = Math.max(0, Math.min(1, rainStrength));
        this.skyRed = skyRed;
        this.skyGreen = skyGreen;
        this.skyBlue = skyBlue;
        blockBrightness = packedLight & 0xFFFF;
        skyBrightness = packedLight >>> 16 & 0xFFFF;
        this.frame = frame;
    }

    public void captureView(int renderDistance, float nightVision, float blindness, float screenBrightness) {
        far = renderDistance * 16f;
        this.nightVision = nightVision;
        this.blindness = blindness;
        this.screenBrightness = Math.max(0, Math.min(1, screenBrightness));
    }

    public void captureAtlas(int width, int height) {
        atlasWidth = Math.max(0, width);
        atlasHeight = Math.max(0, height);
    }

    public static float nightVision(int durationTicks, float partialTicks) {
        return durationTicks > 200 ? 1
                : 0.7f + MathHelper.sin((durationTicks - partialTicks) * (float) Math.PI * 0.2f) * 0.3f;
    }

    public static float blindness(int durationTicks) {
        return durationTicks >= 20 ? 1 : Math.max(0, durationTicks) / 20f;
    }

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
        double radians = Math.toRadians(celestialAngle * 360.0f);
        double x = -Math.sin(radians) * SKY_DISTANCE;
        double y = Math.cos(radians) * SKY_DISTANCE;
        float[] modelView = camera.modelView;
        transform(modelView, x, y, 0, 1, sunPosition);
        transform(modelView, -x, -y, 0, 1, moonPosition);
        transform(modelView, 0, SKY_DISTANCE, 0, 0, upPosition);
        this.medium = medium;
        eyeAltitude = (float) camera.y();
        this.precipitation = precipitation;
        passFrame = camera.frame();
        pass = camera.sequence();
    }

    private static void transform(float[] m, double x, double y, double z, double w, float[] into) {
        into[0] = (float) (m[0] * x + m[4] * y + m[8] * z + m[12] * w);
        into[1] = (float) (m[1] * x + m[5] * y + m[9] * z + m[13] * w);
        into[2] = (float) (m[2] * x + m[6] * y + m[10] * z + m[14] * w);
    }

    public boolean readyFor(@Nullable CameraSnapshot camera) {
        return camera != null && pass == camera.sequence() && passFrame == camera.frame() && frame == passFrame;
    }

    public void clear() {
        frame = 0;
        pass = 0;
        passFrame = 0;
    }

    public long frame() {
        return frame;
    }

    public long pass() {
        return pass;
    }

    public int worldTime() {
        return worldTime;
    }

    public int worldDay() {
        return worldDay;
    }

    public int moonPhase() {
        return moonPhase;
    }

    public float celestialAngle() {
        return celestialAngle;
    }

    public float sunAngle() {
        return sunAngle;
    }

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

    public int blockBrightness() {
        return blockBrightness;
    }

    public int skyBrightness() {
        return skyBrightness;
    }

    public float far() {
        return far;
    }

    public float nightVision() {
        return nightVision;
    }

    public float blindness() {
        return blindness;
    }

    public float screenBrightness() {
        return screenBrightness;
    }

    public int atlasWidth() {
        return atlasWidth;
    }

    public int atlasHeight() {
        return atlasHeight;
    }

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

    public float eyeAltitude() {
        return eyeAltitude;
    }

    public int precipitation() {
        return precipitation;
    }

    public int medium() {
        return medium;
    }
}
