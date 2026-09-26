// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.diagnostics;

import io.github.silentcall2598.focalis.config.ConfigSection;
import io.github.silentcall2598.focalis.feature.Feature;
import io.github.silentcall2598.focalis.feature.FeatureContext;
import io.github.silentcall2598.focalis.render.lifecycle.RenderPhase;
import io.github.silentcall2598.focalis.render.lifecycle.RenderStage;
import org.apache.logging.log4j.Logger;

import java.util.Locale;
import java.util.concurrent.TimeUnit;

// Handy for checking that the lifecycle hooks fire and for rough frame time baselines.
public final class FrameStatsFeature extends Feature {

    public static final String ID = "frame_stats";

    private int reportIntervalSeconds;

    public FrameStatsFeature() {
        super(ID, "Periodically logs frame timing and render stage counts. Diagnostic only. It does not"
                + " change rendering.", false);
    }

    @Override
    protected void loadConfig(ConfigSection config) {
        reportIntervalSeconds = config.getInt("reportIntervalSeconds", 10, 1, 3600, "Seconds between reports.");
    }

    @Override
    protected void setup(FeatureContext context) {
        Recorder recorder = new Recorder(context.logger(), TimeUnit.SECONDS.toNanos(reportIntervalSeconds));
        context.addRenderListener(RenderStage.FRAME, recorder::onFrame);
        context.addRenderListener(RenderStage.WORLD, recorder::onWorld);
    }

    // Client thread only. Nothing is allocated per frame.
    private static final class Recorder {

        private final Logger logger;
        private final long intervalNanos;

        private boolean started;
        private long intervalStart;
        private long frameStart;

        private int frames;
        private int frameGaps;
        private long frameGapSum;
        private long frameGapMax;
        private long renderSum;
        private long renderMax;
        private int worldPasses;

        Recorder(Logger logger, long intervalNanos) {
            this.logger = logger;
            this.intervalNanos = intervalNanos;
        }

        void onFrame(RenderStage stage, RenderPhase phase, float partialTicks) {
            long now = System.nanoTime();
            if (phase == RenderPhase.START) {
                if (started) {
                    long gap = now - frameStart;
                    frameGaps++;
                    frameGapSum += gap;
                    frameGapMax = Math.max(frameGapMax, gap);
                } else {
                    started = true;
                    intervalStart = now;
                }
                frameStart = now;
                return;
            }

            if (!started) {
                return;
            }
            // CPU time between RenderTickEvent START and END. The GPU may still be working on the frame.
            long render = now - frameStart;
            frames++;
            renderSum += render;
            renderMax = Math.max(renderMax, render);

            if (now - intervalStart >= intervalNanos) {
                report(now - intervalStart);
                intervalStart = now;
            }
        }

        void onWorld(RenderStage stage, RenderPhase phase, float partialTicks) {
            worldPasses++;
        }

        private void report(long elapsedNanos) {
            double seconds = elapsedNanos / 1e9;
            logger.info(String.format(Locale.ROOT,
                    "%d frames in %.1f s (%.1f FPS); frame time avg %.2f ms, max %.2f ms;"
                            + " client render time avg %.2f ms, max %.2f ms; world passes %d",
                    frames, seconds, frames / seconds,
                    millis(frameGapSum, frameGaps), frameGapMax / 1e6,
                    millis(renderSum, frames), renderMax / 1e6,
                    worldPasses));

            frames = 0;
            frameGaps = 0;
            frameGapSum = 0;
            frameGapMax = 0;
            renderSum = 0;
            renderMax = 0;
            worldPasses = 0;
        }

        private static double millis(long totalNanos, int count) {
            return count == 0 ? 0 : totalNanos / 1e6 / count;
        }
    }
}
