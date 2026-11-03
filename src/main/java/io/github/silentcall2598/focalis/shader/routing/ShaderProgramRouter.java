// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.shader.routing;

import io.github.silentcall2598.focalis.render.lifecycle.RenderDrawKind;
import io.github.silentcall2598.focalis.render.lifecycle.RenderStage;

/**
 * Pure and allocation free. A combination it doesn't expect is {@link ShaderProgramRole#UNCLASSIFIED} instead of a
 * guess, and it never returns null.
 */
public final class ShaderProgramRouter {

    private ShaderProgramRouter() {
    }

    public static ShaderProgramRole route(RenderStage stage, RenderDrawKind kind) {
        if (stage == null || kind == null) {
            return ShaderProgramRole.UNCLASSIFIED;
        }
        switch (stage) {
            case FRAME:
            case WORLD:
            case GUI:
            case ENTITY_OUTLINES:
                return only(kind, RenderDrawKind.DEFAULT, ShaderProgramRole.NONE);
            case CAMERA:
                return ShaderProgramRole.NONE;
            case SKY:
                if (kind == RenderDrawKind.SKY_BASIC) {
                    return ShaderProgramRole.SKY_BASIC;
                }
                return only(kind, RenderDrawKind.SKY_TEXTURED, ShaderProgramRole.SKY_TEXTURED);
            case TERRAIN:
                if (kind == RenderDrawKind.TERRAIN_SOLID) {
                    return ShaderProgramRole.TERRAIN_SOLID;
                }
                if (kind == RenderDrawKind.TERRAIN_CUTOUT_MIPPED) {
                    return ShaderProgramRole.TERRAIN_CUTOUT_MIPPED;
                }
                return only(kind, RenderDrawKind.TERRAIN_CUTOUT, ShaderProgramRole.TERRAIN_CUTOUT);
            case TRANSLUCENT:
                return only(kind, RenderDrawKind.TERRAIN_TRANSLUCENT, ShaderProgramRole.TERRAIN_TRANSLUCENT);
            case ENTITIES:
                // The Forge pass is about draw order, not a different kind of program.
                if (kind == RenderDrawKind.ENTITY_PASS_0 || kind == RenderDrawKind.ENTITY_PASS_1
                        || kind == RenderDrawKind.ENTITY) {
                    return ShaderProgramRole.ENTITIES;
                }
                return special(kind);
            case BLOCK_DAMAGE:
                return only(kind, RenderDrawKind.DEFAULT, ShaderProgramRole.DAMAGED_BLOCK);
            case PARTICLES:
                if (kind == RenderDrawKind.PARTICLES_LIT) {
                    return ShaderProgramRole.PARTICLES_LIT;
                }
                return only(kind, RenderDrawKind.PARTICLES_NORMAL, ShaderProgramRole.PARTICLES_NORMAL);
            case WEATHER:
                return only(kind, RenderDrawKind.DEFAULT, ShaderProgramRole.WEATHER);
            case CLOUDS:
                return only(kind, RenderDrawKind.DEFAULT, ShaderProgramRole.CLOUDS);
            case HAND:
                return only(kind, RenderDrawKind.DEFAULT, ShaderProgramRole.HAND);
            default:
                return ShaderProgramRole.UNCLASSIFIED;
        }
    }

    private static ShaderProgramRole special(RenderDrawKind kind) {
        switch (kind) {
            case BLOCK_ENTITY:
                return ShaderProgramRole.BLOCK_ENTITIES;
            case DAMAGED_BLOCK_ENTITY:
                return ShaderProgramRole.DAMAGED_BLOCK;
            // The legacy format has one program for the glint on armor and on held items.
            case ARMOR_GLINT:
            case ITEM_GLINT:
                return ShaderProgramRole.ARMOR_GLINT;
            case EYES:
                return ShaderProgramRole.EYES;
            case BEACON_BEAM:
                return ShaderProgramRole.BEACON_BEAM;
            default:
                return ShaderProgramRole.UNCLASSIFIED;
        }
    }

    private static ShaderProgramRole only(RenderDrawKind kind, RenderDrawKind expected, ShaderProgramRole role) {
        return kind == expected ? role : ShaderProgramRole.UNCLASSIFIED;
    }
}
