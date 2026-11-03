// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.shader.program;

import javax.annotation.Nullable;

/** Matrices are stored column by column like OpenGL keeps them, and the getters hand out copies. */
public final class CameraSnapshot {

    private final long sequence;
    private final long world;
    private final int lane;
    private final long frame;
    @Nullable
    private final String historyBreak;
    private final double x;
    private final double y;
    private final double z;
    private final double previousX;
    private final double previousY;
    private final double previousZ;
    // Shared with the next snapshot of the lane as its previous values, which is safe since nobody writes them.
    final float[] modelView;
    final float[] projection;
    final float[] previousModelView;
    final float[] previousProjection;
    @Nullable
    final float[] modelViewInverse;
    @Nullable
    final float[] projectionInverse;

    CameraSnapshot(long sequence, long world, int lane, long frame, @Nullable String historyBreak, double x, double y,
            double z, float[] modelView, float[] projection, @Nullable CameraSnapshot previous, boolean sameFrame) {
        this.sequence = sequence;
        this.world = world;
        this.lane = lane;
        this.frame = frame;
        this.historyBreak = historyBreak;
        this.x = x;
        this.y = y;
        this.z = z;
        this.modelView = modelView;
        this.projection = projection;
        float[] inverse = new float[Matrix4.SIZE];
        modelViewInverse = Matrix4.invert(modelView, inverse) ? inverse : null;
        inverse = new float[Matrix4.SIZE];
        projectionInverse = Matrix4.invert(projection, inverse) ? inverse : null;
        // Only values are kept, never the earlier snapshot itself, so old captures don't pile up in a chain.
        if (previous == null) {
            previousX = x;
            previousY = y;
            previousZ = z;
            previousModelView = modelView;
            previousProjection = projection;
        } else if (sameFrame) {
            previousX = previous.previousX;
            previousY = previous.previousY;
            previousZ = previous.previousZ;
            previousModelView = previous.previousModelView;
            previousProjection = previous.previousProjection;
        } else {
            previousX = previous.x;
            previousY = previous.y;
            previousZ = previous.z;
            previousModelView = previous.modelView;
            previousProjection = previous.projection;
        }
    }

    /** Counts the client worlds and dimensions the session's captures were taken in, from 1. */
    public long world() {
        return world;
    }

    public long sequence() {
        return sequence;
    }

    public int lane() {
        return lane;
    }

    public long frame() {
        return frame;
    }

    public boolean continuesHistory() {
        return historyBreak == null;
    }

    @Nullable
    public String historyBreak() {
        return historyBreak;
    }

    public double x() {
        return x;
    }

    public double y() {
        return y;
    }

    public double z() {
        return z;
    }

    public double previousX() {
        return previousX;
    }

    public double previousY() {
        return previousY;
    }

    public double previousZ() {
        return previousZ;
    }

    float[] modelView() {
        return modelView.clone();
    }

    float[] projection() {
        return projection.clone();
    }

    float[] previousModelView() {
        return previousModelView.clone();
    }

    float[] previousProjection() {
        return previousProjection.clone();
    }

    public boolean modelViewInvertible() {
        return modelViewInverse != null;
    }

    public boolean projectionInvertible() {
        return projectionInverse != null;
    }

    @Nullable
    float[] modelViewInverse() {
        return modelViewInverse == null ? null : modelViewInverse.clone();
    }

    @Nullable
    float[] projectionInverse() {
        return projectionInverse == null ? null : projectionInverse.clone();
    }
}
