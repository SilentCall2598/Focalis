// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.render.lifecycle;

/**
 * What exactly a stage is drawing. The START and END of one stage always carry the same kind. A stage can open again
 * inside itself with another kind, like the sun inside the sky.
 */
public enum RenderDrawKind {
    DEFAULT,
    CAMERA_SINGLE,
    CAMERA_ANAGLYPH_FIRST,
    CAMERA_ANAGLYPH_SECOND,
    SKY_BASIC,
    SKY_TEXTURED,
    TERRAIN_SOLID,
    TERRAIN_CUTOUT_MIPPED,
    TERRAIN_CUTOUT,
    TERRAIN_TRANSLUCENT,
    ENTITY_PASS_0,
    ENTITY_PASS_1,
    ENTITY,
    BLOCK_ENTITY,
    DAMAGED_BLOCK_ENTITY,
    ARMOR_GLINT,
    ITEM_GLINT,
    EYES,
    BEACON_BEAM,
    PARTICLES_LIT,
    PARTICLES_NORMAL
}
