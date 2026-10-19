// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.shader.program;

import io.github.silentcall2598.focalis.render.lifecycle.RenderDrawKind;
import io.github.silentcall2598.focalis.render.lifecycle.RenderStage;

/**
 * Steps the open program scopes aside for one draw inside a terrain stage, so that draw goes the vanilla way with the
 * program unbound and the scopes' alpha test, blend mode and textures put back. Client thread only.
 */
public interface DrawSuspension {

    /** The stage and kind have to be those of the innermost open scope, a terrain one. */
    void suspendForDraw(RenderStage stage, RenderDrawKind drawKind);

    /** Ends the step aside with the same stage and kind, binding the program and its state again. */
    void resumeAfterDraw(RenderStage stage, RenderDrawKind drawKind);
}
