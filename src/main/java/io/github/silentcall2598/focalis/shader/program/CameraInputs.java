// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.shader.program;

import javax.annotation.Nullable;
import java.util.Objects;

/**
 * The camera every world program of a session sees, captured once per world pass before any of its stages draws. A
 * displayed frame can have more than one world pass, like the two of anaglyph 3D, and each pass gets its own
 * snapshot. Previous values come from the same lane, which is one recurring world pass. The lanes are Minecraft's
 * world pass numbers, 0 and 1 for the two anaglyph passes and 2 for a normal one.
 *
 * <p>The projection is the one the pass draws its world with. With anaglyph 3D at 4 or more chunks of render
 * distance, vanilla sets its projection up again right before the sky, without the eye offset it set up the camera
 * with, and draws every world stage that way. So the x and y rows come from what is set when the first world stage
 * starts, and the depth rows from the camera setup, since the sky and clouds draw with their own far plane.
 *
 * <p>A lane goes on from its last capture only when that capture was in the displayed frame right before and nothing
 * in {@link CameraStream} changed since any lane's last capture. Otherwise its history starts over and the previous
 * values are the current ones. Client thread only.
 */
public final class CameraInputs {

    static final int LANES = 3;
    /** The lane of a world pass that isn't one of Minecraft's, which never keeps history. */
    public static final int NO_LANE = -1;

    // Each lane's first capture in the latest frame it had one.
    private final CameraSnapshot[] lanes = new CameraSnapshot[LANES];
    // What the lanes were captured with. Any change empties all of them, so they never hold an old world.
    @Nullable
    private CameraStream stream;
    private long sequence;
    private long world;
    // The capture of the current world pass, null until it was taken.
    @Nullable
    private CameraSnapshot current;

    /** A world pass started, and its camera isn't set up yet. */
    public void worldPassStarted() {
        current = null;
    }

    /**
     * Takes the camera of the current world pass. The arrays are copied.
     *
     * @param lane 0 to {@link #LANES} - 1, or {@link #NO_LANE}
     * @param frame the FrameInputs sequence of the current displayed frame
     * @param modelView GL_MODELVIEW_MATRIX right after the camera was set up, column by column
     * @param projection GL_PROJECTION_MATRIX at the same moment
     * @param stageProjection GL_PROJECTION_MATRIX when the pass's first world stage starts, for the x and y rows
     */
    public CameraSnapshot capture(int lane, long frame, CameraStream stream, double x, double y, double z,
            float[] modelView, float[] projection, float[] stageProjection) {
        Objects.requireNonNull(stream, "stream");
        if (lane < NO_LANE || lane >= LANES) {
            throw new IllegalArgumentException("No camera lane " + lane);
        }
        if (modelView.length != Matrix4.SIZE || projection.length != Matrix4.SIZE
                || stageProjection.length != Matrix4.SIZE) {
            throw new IllegalArgumentException("Camera matrices need 16 values");
        }
        if (!stream.sameWorld(this.stream)) {
            world++;
        }
        String streamBreak = stream.breakFrom(this.stream);
        if (streamBreak != null) {
            clearLanes();
            this.stream = stream;
        }
        CameraSnapshot earlier = lane == NO_LANE ? null : lanes[lane];
        boolean sameFrame = earlier != null && earlier.frame() == frame;
        String historyBreak = historyBreak(lane, frame, streamBreak, earlier);
        CameraSnapshot snapshot = new CameraSnapshot(++sequence, world, lane, frame, historyBreak, x, y, z,
                modelView.clone(), drawProjection(projection, stageProjection), historyBreak == null ? earlier : null,
                sameFrame);
        // Only a lane's first capture in a frame becomes its history. A later one in the same frame shares the
        // previous values of the first, so an extra pass can't take the place of the frame before.
        if (lane != NO_LANE && !sameFrame) {
            lanes[lane] = snapshot;
        }
        current = snapshot;
        return snapshot;
    }

    // Rows 0 and 1 of every column hold the field of view, the aspect ratio and anaglyph's eye offset.
    private static float[] drawProjection(float[] camera, float[] stage) {
        float[] projection = camera.clone();
        for (int column = 0; column < 4; column++) {
            projection[column * 4] = stage[column * 4];
            projection[column * 4 + 1] = stage[column * 4 + 1];
        }
        return projection;
    }

    @Nullable
    private static String historyBreak(int lane, long frame, @Nullable String streamBreak,
            @Nullable CameraSnapshot earlier) {
        if (lane == NO_LANE) {
            return "not one of Minecraft's world passes";
        }
        if (streamBreak != null) {
            return streamBreak;
        }
        if (earlier == null) {
            return "first capture of lane " + lane;
        }
        if (earlier.frame() == frame) {
            return earlier.historyBreak();
        }
        if (earlier.frame() != frame - 1) {
            return "lane " + lane + " skipped " + (frame - earlier.frame() - 1) + " frames";
        }
        return null;
    }

    /** The camera of the current world pass, or null when its camera wasn't captured. */
    @Nullable
    public CameraSnapshot current() {
        return current;
    }

    /** Forgets every capture, so whatever comes next starts with fresh history. Holds no world after this. */
    public void clear() {
        clearLanes();
        stream = null;
        current = null;
    }

    private void clearLanes() {
        for (int i = 0; i < LANES; i++) {
            lanes[i] = null;
        }
    }
}
