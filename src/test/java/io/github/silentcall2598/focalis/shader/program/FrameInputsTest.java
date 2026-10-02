// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.shader.program;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FrameInputsTest {

    private static final long SECOND = 1_000_000_000L;
    // Any nanoTime origin works, including a negative one.
    private static final long START = -5 * SECOND;

    private final FrameInputs frame = new FrameInputs();

    @Test
    void nothingIsCapturedAtFirst() {
        assertFalse(frame.captured());
        assertEquals(0, frame.sequence());
    }

    @Test
    void theFirstFrameStartsEverythingAtZero() {
        frame.capture(START, 854, 480);

        assertTrue(frame.captured());
        assertEquals(1, frame.sequence());
        assertEquals(0, frame.frameCounter());
        assertEquals(0f, frame.frameTime());
        assertEquals(0f, frame.frameTimeCounter());
        assertEquals(854f, frame.viewWidth());
        assertEquals(480f, frame.viewHeight());
        assertEquals(854f / 480f, frame.aspectRatio());
    }

    @Test
    void timesAreSecondsBetweenFrameStarts() {
        frame.capture(START, 854, 480);
        frame.capture(START + 16_000_000L, 854, 480);
        frame.capture(START + 50_000_000L, 854, 480);

        assertEquals(2, frame.frameCounter());
        assertEquals(3, frame.sequence());
        assertEquals(0.034f, frame.frameTime(), 1e-6f);
        assertEquals(0.05f, frame.frameTimeCounter(), 1e-6f);
    }

    @Test
    void theFrameCounterStartsAgainAfter720719() {
        for (int i = 0; i < FrameInputs.FRAME_COUNTER_PERIOD; i++) {
            frame.capture(START + i, 1, 1);
        }
        assertEquals(720719, frame.frameCounter());

        frame.capture(START + FrameInputs.FRAME_COUNTER_PERIOD, 1, 1);

        assertEquals(0, frame.frameCounter());
        // The sequence keeps going, so a program last updated 720720 frames ago still gets new values.
        assertEquals(FrameInputs.FRAME_COUNTER_PERIOD + 1, frame.sequence());
    }

    @Test
    void theRunTimeStartsAgainEveryHour() {
        frame.capture(START, 1, 1);
        frame.capture(START + 3599 * SECOND + SECOND / 2, 1, 1);
        assertEquals(3599.5f, frame.frameTimeCounter(), 1e-3f);

        frame.capture(START + 3600 * SECOND, 1, 1);
        assertEquals(0f, frame.frameTimeCounter());
        assertEquals(0.5f, frame.frameTime(), 1e-6f);

        frame.capture(START + 7202 * SECOND, 1, 1);
        assertEquals(2f, frame.frameTimeCounter(), 1e-3f);
        // A long stall isn't shortened.
        assertEquals(3602f, frame.frameTime(), 1e-3f);
    }

    @Test
    void aClockGoingBackwardsCountsAsNoTime() {
        frame.capture(START, 1, 1);
        frame.capture(START + 2 * SECOND, 1, 1);

        frame.capture(START + SECOND, 1, 1);

        assertEquals(0f, frame.frameTime());
        assertEquals(2f, frame.frameTimeCounter(), 1e-6f);
        frame.capture(START + 3 * SECOND, 1, 1);
        assertEquals(1f, frame.frameTime(), 1e-6f);
        assertEquals(3f, frame.frameTimeCounter(), 1e-6f);
    }

    @Test
    void resizingChangesTheViewOnTheNextCapture() {
        frame.capture(START, 854, 480);
        frame.capture(START + 1, 1920, 1080);

        assertEquals(1920f, frame.viewWidth());
        assertEquals(1080f, frame.viewHeight());
        assertEquals(16f / 9f, frame.aspectRatio(), 1e-6f);

        frame.capture(START + 2, 600, 900);
        assertEquals(600f / 900f, frame.aspectRatio());
    }

    @Test
    void anUnusableSizeKeepsTheLastOne() {
        frame.capture(START, 854, 480);

        frame.capture(START + 1, 0, 480);
        assertEquals(854f, frame.viewWidth());
        frame.capture(START + 2, 854, -1);
        assertEquals(480f, frame.viewHeight());
        assertEquals(854f / 480f, frame.aspectRatio());
    }

    @Test
    void withoutAnyUsableSizeTheViewIsOnePixel() {
        frame.capture(START, 0, 0);

        assertEquals(1f, frame.viewWidth());
        assertEquals(1f, frame.viewHeight());
        assertEquals(1f, frame.aspectRatio());
        assertTrue(Float.isFinite(frame.aspectRatio()));
    }
}
