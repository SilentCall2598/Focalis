// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.render.lifecycle;

/**
 * Logical render stages that Focalis hooks as needed, and only hooked stages get events. They don't follow one fixed
 * order in 1.12.2, since entities render in two passes, weather draws before translucent blocks and clouds draw early
 * or late depending on camera height.
 */
public enum RenderStage {
    /** One whole client frame, including the world, hand, HUD and screens. */
    FRAME,
    /** The whole world pass, before the first-person hand. */
    WORLD,
    /** Sky, sun, moon and stars. */
    SKY,
    /** Opaque and cutout chunk geometry. */
    TERRAIN,
    /** Entities, tile entities and particles. */
    ENTITIES,
    /** Translucent chunk geometry and the translucent entity pass. */
    TRANSLUCENT,
    /** Rain and snow. */
    WEATHER,
    /** First-person hand and held items. */
    HAND,
    /** In-game HUD and screens. */
    GUI
}
