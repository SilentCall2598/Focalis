// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.shader.routing;

/** How a program role turned out in one program folder of a pack. */
public enum ResolutionState {
    /** An enabled program from the role's fallback chain exists, whether or not it loaded cleanly. */
    RESOLVED,
    /** The role is understood, but the folder has none of the programs that could draw it. */
    MISSING,
    /** The folder has programs that could draw the role, but the pack configuration disabled all of them. */
    DISABLED,
    /** The role doesn't draw anything, so there is nothing to look for. */
    NOT_APPLICABLE,
    /** The render context behind the role isn't precise enough to pick a pack program. */
    NEEDS_MORE_CONTEXT
}
