// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.shader.routing;

public enum ShaderProgramRole {
    /**
     * Nothing at this boundary is for a Focalis program to draw, like a whole frame or world pass, or vanilla's entity
     * outlines, which use vanilla's own shaders.
     */
    NONE,
    /** Something is drawn here, but its context isn't understood well enough to give it a role. */
    UNCLASSIFIED,
    SKY_BASIC,
    SKY_TEXTURED,
    TERRAIN_SOLID,
    TERRAIN_CUTOUT_MIPPED,
    TERRAIN_CUTOUT,
    TERRAIN_TRANSLUCENT,
    ENTITIES,
    BLOCK_ENTITIES,
    ARMOR_GLINT,
    EYES,
    BEACON_BEAM,
    DAMAGED_BLOCK,
    PARTICLES_LIT,
    PARTICLES_NORMAL,
    WEATHER,
    CLOUDS,
    HAND
}
