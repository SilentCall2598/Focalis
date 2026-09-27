// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.render.lifecycle;

/** What exactly a stage is drawing. The START and END of one stage always carry the same kind. */
public enum RenderDrawKind {
    /** The stage has no finer classification. */
    DEFAULT,
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
