// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.render.lifecycle;

/**
 * Whether a precise Mixin hook works in this session. A hook can't report that it never applied, so a later point
 * that it always comes before decides that it is unavailable.
 */
public final class HookAvailability {

    public enum State {
        UNKNOWN,
        AVAILABLE,
        UNAVAILABLE
    }

    private volatile State state = State.UNKNOWN;

    public State state() {
        return state;
    }

    public boolean isAvailable() {
        return state == State.AVAILABLE;
    }

    // True the first time the hook fires, so the caller logs it once.
    boolean hookFired() {
        if (state == State.AVAILABLE) {
            return false;
        }
        state = State.AVAILABLE;
        return true;
    }

    // Called where the hook should already have fired. True only when that decides it is unavailable.
    boolean hookExpected() {
        if (state != State.UNKNOWN) {
            return false;
        }
        state = State.UNAVAILABLE;
        return true;
    }
}
