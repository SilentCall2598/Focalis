// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.render.lifecycle;

/**
 * Logical render stages that Focalis hooks as needed, and only hooked stages get events. A stage can happen several
 * times in one world pass or not at all, and the stages inside a world pass don't follow one fixed order. Listeners
 * can only rely on START and END being balanced.
 */
public enum RenderStage {
    /** One whole client frame, including the world, hand, HUD and screens. */
    FRAME,
    /** One whole world pass, from the start of EntityRenderer.renderWorldPass to right before the hand. */
    WORLD,
    /** Sky, sun, moon and stars. Skipped below 4 chunks of render distance. */
    SKY,
    /** Solid and cutout chunk layers, one pair per layer with the layer as draw kind. */
    TERRAIN,
    /**
     * RenderGlobal.renderEntities, with entities and tile entities. It runs twice per world pass, for Forge render
     * pass 0 and again after translucent terrain for pass 1, and the draw kind tells them apart.
     */
    ENTITIES,
    /** Lit particles and normal particles, one pair each with its own draw kind. */
    PARTICLES,
    /** The translucent chunk layer, draw kind TERRAIN_TRANSLUCENT. */
    TRANSLUCENT,
    /** Rain and snow. It runs every world pass, also in clear weather. */
    WEATHER,
    /** Clouds when they are on. Before terrain below cloud height, after translucent terrain above it. */
    CLOUDS,
    /** First-person hand and held items. It comes after WORLD END. */
    HAND,
    /** In-game HUD and screens. */
    GUI
}
