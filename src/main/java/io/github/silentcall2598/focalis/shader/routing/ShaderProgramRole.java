// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.shader.routing;

/**
 * What a shader program is for, in Focalis's own terms. These are not shaderpack program or file names. Which pack
 * program fills a role is decided separately.
 */
public enum ShaderProgramRole {
    /**
     * Nothing at this boundary is for a Focalis program to draw, like a whole frame or world pass, or vanilla's entity
     * outlines, which use vanilla's own shaders.
     */
    NONE,
    /** Something is drawn here, but its context isn't understood well enough to give it a role. */
    UNCLASSIFIED,
    /** The surface sky around the sun and moon, with its sunrise glow and stars. */
    SKY_BASIC,
    /** The sun and moon. */
    SKY_TEXTURED,
    TERRAIN_SOLID,
    TERRAIN_CUTOUT_MIPPED,
    TERRAIN_CUTOUT,
    TERRAIN_TRANSLUCENT,
    /** Both Forge entity render passes. */
    ENTITIES,
    PARTICLES_LIT,
    PARTICLES_NORMAL,
    WEATHER,
    CLOUDS,
    HAND
}
