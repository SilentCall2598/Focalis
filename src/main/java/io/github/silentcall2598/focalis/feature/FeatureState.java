// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.feature;

public enum FeatureState {
    /** Not initialized yet. */
    PENDING,
    /** Turned off in the config, or turned off at runtime. */
    DISABLED,
    /** Enabled but declined to start, for example because of a conflicting mod. */
    UNAVAILABLE,
    ACTIVE,
    /** Threw during setup or at runtime, and has been detached and cleaned up. */
    FAILED
}
