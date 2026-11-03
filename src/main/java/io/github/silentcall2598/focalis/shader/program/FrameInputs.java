// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.shader.program;

/**
 * One instance is shared by all programs of a session and is captured once at the start of each frame, so every
 * program and world pass in a frame reads the same values.
 */
public final class FrameInputs {

    /** frameCounter goes from 0 up to one less than this and then starts at 0 again. */
    public static final int FRAME_COUNTER_PERIOD = 720720;
    /** frameTimeCounter starts at 0 again after this many seconds. */
    public static final int TIME_COUNTER_PERIOD_SECONDS = 3600;

    private static final long TIME_COUNTER_PERIOD_NANOS = TIME_COUNTER_PERIOD_SECONDS * 1_000_000_000L;

    // Counts every capture and never wraps in practice, so it can mark which frame a program last got.
    private long sequence;
    private long originNanos;
    private long lastNanos;
    private int frameCounter;
    private float frameTime;
    private float frameTimeCounter;
    private int viewWidth = 1;
    private int viewHeight = 1;

    /** The first capture is frame 0 with a frame time of 0 and starts the run time. */
    public void capture(long nanoTime, int width, int height) {
        if (sequence == 0) {
            originNanos = nanoTime;
            lastNanos = nanoTime;
        } else {
            frameCounter = frameCounter + 1 == FRAME_COUNTER_PERIOD ? 0 : frameCounter + 1;
        }
        // nanoTime shouldn't go backwards, but if it does that frame took no time.
        long now = Math.max(nanoTime, lastNanos);
        frameTime = (float) ((now - lastNanos) / 1e9);
        frameTimeCounter = (float) (((now - originNanos) % TIME_COUNTER_PERIOD_NANOS) / 1e9);
        lastNanos = now;
        // Minecraft never reports less than 1, but anything that does keeps the last usable size.
        if (width > 0 && height > 0) {
            viewWidth = width;
            viewHeight = height;
        }
        sequence++;
    }

    public boolean captured() {
        return sequence > 0;
    }

    /** 1 for the first captured frame, then one more for every capture. */
    public long sequence() {
        return sequence;
    }

    public int frameCounter() {
        return frameCounter;
    }

    public float frameTime() {
        return frameTime;
    }

    public long runNanos() {
        return lastNanos - originNanos;
    }

    /** Seconds since the first captured frame, starting at 0 again every hour. */
    public float frameTimeCounter() {
        return frameTimeCounter;
    }

    public float viewWidth() {
        return viewWidth;
    }

    public float viewHeight() {
        return viewHeight;
    }

    public float aspectRatio() {
        return (float) viewWidth / viewHeight;
    }
}
