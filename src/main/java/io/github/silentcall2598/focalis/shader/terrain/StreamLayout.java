// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.shader.terrain;

import org.lwjgl.opengl.GL11;

/**
 * How chunk terrain gets the attributes vanilla's block vertex format doesn't have: a stream next to vanilla's vertex
 * data, which stays exactly as vanilla built it. All four vertices of a quad get the same values. Vertex buffers use a
 * compact layout. Display lists use floats for the normal and tangent, since drivers may convert normalized bytes in a
 * list with the older (2c+1)/255 rule, which has no exact 0. Immutable.
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

    /** The largest material ID mc_Entity can carry. It's stored as a signed short. */
    public static final int MAX_ENTITY_ID = Short.MAX_VALUE;

    /**
     * For vertex buffers, 20 bytes a vertex. The normal as three normalized bytes and a pad, the ID and a 0 as two
     * shorts, the midpoint as two floats and the tangent as four normalized bytes.
     */
    public static final StreamLayout BUFFER = new StreamLayout("buffer", 20, GL11.GL_BYTE, 4, 8, GL11.GL_BYTE, 16);

    /**
     * For display lists, 40 bytes a vertex. The normal as three floats, the ID and a 0 as two shorts, the midpoint as
     * two floats and the tangent as four floats.
     */
    public static final StreamLayout LIST = new StreamLayout("list", 40, GL11.GL_FLOAT, 12, 16, GL11.GL_FLOAT, 24);

    private final String name;
    private final int stride;
    private final int vectorType;
    private final int entityOffset;
    private final int midTexCoordOffset;
    private final int tangentType;
    private final int tangentOffset;

    private StreamLayout(String name, int stride, int vectorType, int entityOffset, int midTexCoordOffset,
            int tangentType, int tangentOffset) {
        this.name = name;
        this.stride = stride;
        this.vectorType = vectorType;
        this.entityOffset = entityOffset;
        this.midTexCoordOffset = midTexCoordOffset;
        this.tangentType = tangentType;
        this.tangentOffset = tangentOffset;
    }

    /** Bytes per vertex. */
    public int stride() {
        return stride;
    }

    /** GL_BYTE or GL_FLOAT. The normal always starts at byte 0. */
    public int normalType() {
        return vectorType;
    }

    /** Where the two shorts of mc_Entity start. */
    public int entityOffset() {
        return entityOffset;
    }

    /** Where the two floats of mc_midTexCoord start. */
    public int midTexCoordOffset() {
        return midTexCoordOffset;
    }

    /** GL_BYTE, normalized, or GL_FLOAT. */
    public int tangentType() {
        return tangentType;
    }

    public int tangentOffset() {
        return tangentOffset;
    }

    /**
     * A material ID the way mc_Entity.x stores it. Unmapped stays -1. An ID the short can't hold becomes -1 too, rather
     * than wrapping around into some other ID, and the world programs log those IDs when the stream starts.
     */
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
