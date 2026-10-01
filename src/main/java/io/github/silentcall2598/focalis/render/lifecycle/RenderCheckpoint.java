// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.render.lifecycle;

/**
 * A point where one renderer Focalis doesn't control just returned. Only the outermost renderer counts, so one that
 * draws another entity or block entity inside itself isn't interrupted. These come once per entity or block entity,
 * so they are far more frequent than stages and carry nothing else. They happen anywhere, also outside the world pass.
 */
public enum RenderCheckpoint {
    /** An entity renderer returned, including multipass and outline draws. */
    ENTITY_RENDERED,
    /** A block entity renderer returned, including damaged block overlays and fast renderers filling their batch. */
    BLOCK_ENTITY_RENDERED
}
