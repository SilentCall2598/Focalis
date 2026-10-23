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
    /** One entity drawn inside an entity render pass. */
    ENTITY,
    /** One block entity drawn inside an entity render pass, or the batch of fast block entity renderers. */
    BLOCK_ENTITY,
    /** The block breaking overlay of one block entity, drawn by its renderer with the destroy stage texture. */
    DAMAGED_BLOCK_ENTITY,
    /** The enchantment glint over worn armor or elytra, drawn inside the entity wearing it. */
    ARMOR_GLINT,
    /** The enchantment glint over an item, drawn inside whatever draws the item. */
    ITEM_GLINT,
    /** The glowing eyes of a spider, enderman or ender dragon, drawn inside the entity. */
    EYES,
    /** One segment of a beacon or end gateway beam, drawn inside its block entity. */
    BEACON_BEAM,
    PARTICLES_LIT,
    PARTICLES_NORMAL
}
