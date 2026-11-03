// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.render.lifecycle;

/**
 * Logical render stages that Focalis hooks as needed, and only hooked stages get events. A stage can happen several
 * times in one world pass or not at all, and the stages inside a world pass don't follow one fixed order. Listeners
 * can only rely on START and END being balanced.
 */
public enum RenderStage {
    FRAME,
    WORLD,
    /**
     * Vanilla setting up the camera at the start of each world pass, once per pass. At its END the pass's projection
     * and model-view are in place and nothing has drawn with them yet. The draw kind says which world pass it is.
     */
    CAMERA,
    SKY,
    TERRAIN,
    ENTITIES,
    /**
     * Vanilla's whole outline block inside the pass 0 ENTITIES stage, from clearing the outline framebuffer until
     * Minecraft's framebuffer is bound again. Vanilla binds its own shader programs in there.
     */
    ENTITY_OUTLINES,
    BLOCK_DAMAGE,
    PARTICLES,
    TRANSLUCENT,
    WEATHER,
    CLOUDS,
    HAND,
    GUI
}
