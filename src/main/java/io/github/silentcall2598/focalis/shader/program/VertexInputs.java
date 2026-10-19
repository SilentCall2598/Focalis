// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.shader.program;

import io.github.silentcall2598.focalis.shader.terrain.StreamLayout;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;

import javax.annotation.Nullable;
import java.util.List;
import java.util.function.ToIntFunction;

/**
 * The vertex inputs of a linked program that only the chunk attribute stream supplies, which are gl_Normal for terrain
 * and mc_Entity, mc_midTexCoord and at_tangent. Also whether the driver kept the locations they were bound to, since
 * the stream points its arrays at exactly those. Immutable.
 */
public final class VertexInputs {

    /** A program that reads none of them. */
    public static final VertexInputs NONE = new VertexInputs(false, false, false, false, null);

    private final boolean normal;
    private final boolean entity;
    private final boolean midTexCoord;
    private final boolean tangent;
    @Nullable
    private final String problem;

    private VertexInputs(boolean normal, boolean entity, boolean midTexCoord, boolean tangent,
            @Nullable String problem) {
        this.normal = normal;
        this.entity = entity;
        this.midTexCoord = midTexCoord;
        this.tangent = tangent;
        this.problem = problem;
    }

    /**
     * Reads the active attributes of a program linked with the stream locations bound.
     *
     * @param locations the location the driver gives a generic attribute name, -1 when it isn't active
     * @param refused why the driver wouldn't link with the locations bound, or null when it did. The program was then
     *     linked without them, so it works for draws that don't need the stream.
     */
    static VertexInputs read(List<ActiveAttribute> active, ToIntFunction<String> locations, @Nullable String refused) {
        boolean normal = false;
        boolean entity = false;
        boolean midTexCoord = false;
        boolean tangent = false;
        String problem = refused;
        for (ActiveAttribute attribute : active) {
            int expected;
            switch (attribute.name) {
                case StreamLayout.NORMAL:
                    normal = true;
                    continue;
                case StreamLayout.ENTITY:
                    entity = true;
                    expected = StreamLayout.ENTITY_LOCATION;
                    break;
                case StreamLayout.MID_TEX_COORD:
                    midTexCoord = true;
                    expected = StreamLayout.MID_TEX_COORD_LOCATION;
                    break;
                case StreamLayout.TANGENT:
                    tangent = true;
                    expected = StreamLayout.TANGENT_LOCATION;
                    break;
                default:
                    continue;
            }
            if (problem != null) {
                continue;
            }
            // The stream feeds float data. An integer attribute would read undefined values from it.
            if (!isFloatType(attribute.type) || attribute.size != 1) {
                problem = "it declares " + attribute + ", and the stream only feeds float, vec2, vec3 or vec4";
                continue;
            }
            int location = locations.applyAsInt(attribute.name);
            if (location != expected) {
                problem = "the driver put " + attribute.name + " at location " + location + " instead of " + expected;
            }
        }
        if (!normal && !entity && !midTexCoord && !tangent && problem == null) {
            return NONE;
        }
        return new VertexInputs(normal, entity, midTexCoord, tangent, problem);
    }

    private static boolean isFloatType(int type) {
        return type == GL11.GL_FLOAT || type == GL20.GL_FLOAT_VEC2 || type == GL20.GL_FLOAT_VEC3
                || type == GL20.GL_FLOAT_VEC4;
    }

    public boolean normal() {
        return normal;
    }

    public boolean entity() {
        return entity;
    }

    public boolean midTexCoord() {
        return midTexCoord;
    }

    public boolean tangent() {
        return tangent;
    }

    /** Whether terrain needs the stream to draw this program with meaningful inputs. */
    public boolean needsStream() {
        return normal || entity || midTexCoord || tangent;
    }

    /** Whether the program reads attributes nothing but the chunk stream supplies. */
    public boolean readsStreamAttributes() {
        return entity || midTexCoord || tangent;
    }

    /** Why the stream can't feed this program, or null when it can. */
    @Nullable
    public String problem() {
        return problem;
    }

    @Override
    public String toString() {
        StringBuilder text = new StringBuilder("[");
        if (normal) {
            text.append("gl_Normal ");
        }
        if (entity) {
            text.append("mc_Entity ");
        }
        if (midTexCoord) {
            text.append("mc_midTexCoord ");
        }
        if (tangent) {
            text.append("at_tangent ");
        }
        String flags = text.toString().trim();
        return flags + "]" + (problem == null ? "" : ", " + problem);
    }
}
