// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.shader.routing;

/**
 * What a shader program is for, in Focalis's own terms. These are not shaderpack program or file names. Which pack
 * program fills a role is decided separately.
 */
public enum ShaderProgramRole {
    /** The boundary isn't something that gets drawn, like a whole frame or world pass. */
    NONE,
    /** Something is drawn here, but its context isn't understood well enough to give it a role. */
    UNCLASSIFIED,
    SKY,
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
