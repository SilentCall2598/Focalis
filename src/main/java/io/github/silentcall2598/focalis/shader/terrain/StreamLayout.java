// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.shader.terrain;

import org.lwjgl.opengl.GL11;

/**
 * Display lists use floats for the normal and tangent, since drivers may convert normalized bytes in a list with the
 * older (2c+1)/255 rule, which has no exact 0.
 */
public final class StreamLayout {

    public static final String NORMAL = "gl_Normal";
    public static final String ENTITY = "mc_Entity";
    public static final String MID_TEX_COORD = "mc_midTexCoord";
    public static final String TANGENT = "at_tangent";

    // The generic locations the linker binds the names to. NVIDIA aliases 0, 2, 3 and 8 to 15 with built-in
    // attributes and refuses those at link time, while 1, 6 and 7 are free there.
    public static final int TANGENT_LOCATION = 1;
    public static final int ENTITY_LOCATION = 6;
    public static final int MID_TEX_COORD_LOCATION = 7;

    public static final int MAX_ENTITY_ID = Short.MAX_VALUE;

    public static final StreamLayout BUFFER = new StreamLayout("buffer", 20, GL11.GL_BYTE, 4, 8, GL11.GL_BYTE, 16, -1);

    public static final StreamLayout BUFFER_COLOR = new StreamLayout("buffer with colors", 24, GL11.GL_BYTE, 4, 8,
            GL11.GL_BYTE, 16, 20);

    public static final StreamLayout LIST = new StreamLayout("list", 40, GL11.GL_FLOAT, 12, 16, GL11.GL_FLOAT, 24,
            -1);

    private final String name;
    private final int stride;
    private final int vectorType;
    private final int entityOffset;
    private final int midTexCoordOffset;
    private final int tangentType;
    private final int tangentOffset;
    private final int colorOffset;

    private StreamLayout(String name, int stride, int vectorType, int entityOffset, int midTexCoordOffset,
            int tangentType, int tangentOffset, int colorOffset) {
        this.name = name;
        this.stride = stride;
        this.vectorType = vectorType;
        this.entityOffset = entityOffset;
        this.midTexCoordOffset = midTexCoordOffset;
        this.tangentType = tangentType;
        this.tangentOffset = tangentOffset;
        this.colorOffset = colorOffset;
    }

    public int stride() {
        return stride;
    }

    public int normalType() {
        return vectorType;
    }

    public int entityOffset() {
        return entityOffset;
    }

    public int midTexCoordOffset() {
        return midTexCoordOffset;
    }

    public int tangentType() {
        return tangentType;
    }

    public int tangentOffset() {
        return tangentOffset;
    }

    public int colorOffset() {
        return colorOffset;
    }

    public boolean hasColors() {
        return colorOffset >= 0;
    }

    public boolean forBuffers() {
        return this != LIST;
    }

    public static short entityId(int id) {
        return id >= 0 && id <= MAX_ENTITY_ID ? (short) id : (short) -1;
    }

    // A unit vector component as a normalized signed byte. -1 goes in as -128, which reads back as exactly -1 under
    // both the c/127 rule and the older (2c+1)/255 one. 127 is exactly 1 under both.
    static byte snorm8(double value) {
        long scaled = Math.round(value * 127.0);
        if (scaled >= 127) {
            return 127;
        }
        if (scaled <= -127) {
            return -128;
        }
        return (byte) scaled;
    }

    @Override
    public String toString() {
        return name;
    }
}
