// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.render.lifecycle;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HookAvailabilityTest {

    @Test
    void startsUnknown() {
        HookAvailability hook = new HookAvailability();

        assertEquals(HookAvailability.State.UNKNOWN, hook.state());
        assertFalse(hook.isAvailable());
    }

    @Test
    void firstFireMakesItAvailableAndOnlyReportsOnce() {
        HookAvailability hook = new HookAvailability();

        assertTrue(hook.hookFired());
        assertFalse(hook.hookFired());
        assertFalse(hook.hookExpected());
        assertEquals(HookAvailability.State.AVAILABLE, hook.state());
    }

    @Test
    void expectedBeforeAnyFireMakesItUnavailableOnce() {
        HookAvailability hook = new HookAvailability();

        assertTrue(hook.hookExpected());
        assertFalse(hook.hookExpected());
        assertEquals(HookAvailability.State.UNAVAILABLE, hook.state());
    }

    @Test
    void lateFireStillCountsAsAvailable() {
        HookAvailability hook = new HookAvailability();
        hook.hookExpected();

        assertTrue(hook.hookFired());
        assertTrue(hook.isAvailable());
    }
}
