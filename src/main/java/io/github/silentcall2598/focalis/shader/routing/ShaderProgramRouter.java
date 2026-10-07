// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.shader.routing;

import io.github.silentcall2598.focalis.render.lifecycle.RenderDrawKind;
import io.github.silentcall2598.focalis.render.lifecycle.RenderStage;

/**
 * Turns a render stage and draw kind into the program role that should draw it. Pure and allocation free. A
 * combination it doesn't expect is {@link ShaderProgramRole#UNCLASSIFIED} instead of a guess, and it never returns
 * null.
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
            // Vanilla draws the outlines with its own shaders.
            case ENTITY_OUTLINES:
                return only(kind, RenderDrawKind.DEFAULT, ShaderProgramRole.NONE);
            // Setting up the camera draws nothing, whichever world pass it's for.
            case CAMERA:
                return ShaderProgramRole.NONE;
            case SKY:
                if (kind == RenderDrawKind.SKY_BASIC) {
                    return ShaderProgramRole.SKY_BASIC;
                }
                // A DEFAULT sky is one Focalis can't classify, like a Forge sky renderer or the End.
                return only(kind, RenderDrawKind.SKY_TEXTURED, ShaderProgramRole.SKY_TEXTURED);
            case TERRAIN:
                if (kind == RenderDrawKind.TERRAIN_SOLID) {
                    return ShaderProgramRole.TERRAIN_SOLID;
                }
                if (kind == RenderDrawKind.TERRAIN_CUTOUT_MIPPED) {
                    return ShaderProgramRole.TERRAIN_CUTOUT_MIPPED;
                }
                // A layer some mod added shows up as DEFAULT and stays unclassified.
                return only(kind, RenderDrawKind.TERRAIN_CUTOUT, ShaderProgramRole.TERRAIN_CUTOUT);
            case TRANSLUCENT:
                return only(kind, RenderDrawKind.TERRAIN_TRANSLUCENT, ShaderProgramRole.TERRAIN_TRANSLUCENT);
            case ENTITIES:
                // The Forge pass is about draw order, not a different kind of program.
                return kind == RenderDrawKind.ENTITY_PASS_0 || kind == RenderDrawKind.ENTITY_PASS_1
                        ? ShaderProgramRole.ENTITIES : ShaderProgramRole.UNCLASSIFIED;
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

    private static ShaderProgramRole only(RenderDrawKind kind, RenderDrawKind expected, ShaderProgramRole role) {
        return kind == expected ? role : ShaderProgramRole.UNCLASSIFIED;
    }
}
