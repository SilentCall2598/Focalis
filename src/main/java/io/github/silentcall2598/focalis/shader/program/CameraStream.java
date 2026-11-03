// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.shader.program;

import javax.annotation.Nullable;
import java.util.Objects;

/**
 * The world and the entity the camera follows are compared by identity, since Minecraft replaces them when it changes
 * worlds, dimensions or the followed entity. Holds both strongly, so whoever keeps one has to drop it once the world
 * is gone.
 */
public final class CameraStream {

    private final Object world;
    private final int dimension;
    private final Object viewEntity;
    private final int perspective;
    private final int width;
    private final int height;

    /**
     * @param perspective Minecraft's view mode, 0 for first person, 1 for third person behind and 2 for front
     * @param width the width the projection was set up for, in pixels
     */
    public CameraStream(Object world, int dimension, Object viewEntity, int perspective, int width, int height) {
        this.world = Objects.requireNonNull(world, "world");
        this.dimension = dimension;
        this.viewEntity = Objects.requireNonNull(viewEntity, "viewEntity");
        this.perspective = perspective;
        this.width = width;
        this.height = height;
    }

    public boolean sameWorld(@Nullable CameraStream earlier) {
        return earlier != null && world == earlier.world && dimension == earlier.dimension;
    }

    @Nullable
    public String breakFrom(@Nullable CameraStream earlier) {
        if (earlier == null) {
            return "first capture";
        }
        if (world != earlier.world) {
            return "world replaced";
        }
        if (dimension != earlier.dimension) {
            return "dimension " + earlier.dimension + " -> " + dimension;
        }
        if (viewEntity != earlier.viewEntity) {
            return "camera entity replaced";
        }
        if (perspective != earlier.perspective) {
            return "perspective " + earlier.perspective + " -> " + perspective;
        }
        if (width != earlier.width || height != earlier.height) {
            return "view size " + earlier.width + "x" + earlier.height + " -> " + width + "x" + height;
        }
        return null;
    }
}
