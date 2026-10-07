// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.render.lifecycle;

/**
 * What exactly a stage is drawing. The START and END of one stage always carry the same kind. A stage can open again
 * inside itself with another kind, like the sun inside the sky.
 */
public enum RenderDrawKind {
    /** The stage has no finer classification. */
    DEFAULT,
    /** The camera of a normal world pass, which Minecraft numbers 2. */
    CAMERA_SINGLE,
    /** The camera of the first world pass of an anaglyph 3D frame, number 0, which draws green and blue. */
    CAMERA_ANAGLYPH_FIRST,
    /** The camera of the second world pass of an anaglyph 3D frame, number 1, which draws red. */
    CAMERA_ANAGLYPH_SECOND,
    /** The whole vanilla surface sky. Its sun and moon draws nest inside it as {@link #SKY_TEXTURED}. */
    SKY_BASIC,
    /** The sun or moon draw of the vanilla surface sky. */
    SKY_TEXTURED,
    TERRAIN_SOLID,
    TERRAIN_CUTOUT_MIPPED,
    TERRAIN_CUTOUT,
    TERRAIN_TRANSLUCENT,
    /** Forge entity render pass 0, before translucent terrain. */
    ENTITY_PASS_0,
    /** Forge entity render pass 1, after translucent terrain. */
    ENTITY_PASS_1,
    PARTICLES_LIT,
    PARTICLES_NORMAL
}
