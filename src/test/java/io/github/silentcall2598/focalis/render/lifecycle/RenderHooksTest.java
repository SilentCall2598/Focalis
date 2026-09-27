// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.render.lifecycle;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

// RenderHooks is global and can only be installed once, so the whole life of it is one test.
class RenderHooksTest {

    @Test
    void doesNothingUntilInstalledThenReportsWorldStartOnly() {
        RenderHooks.worldPassStart(0.5F);
        RenderHooks.worldPassEnd();
        assertEquals(HookAvailability.State.UNKNOWN, RenderHooks.worldStart().state());

        RenderLifecycle lifecycle = new RenderLifecycle();
        RenderHooks.install(lifecycle);
        List<String> calls = new ArrayList<>();
        lifecycle.register(RenderStage.WORLD, "test", (stage, phase, partialTicks) ->
                calls.add(stage + "/" + phase + "/" + partialTicks));

        RenderHooks.worldPassStart(0.25F);
        RenderHooks.worldPassEnd();
        RenderHooks.worldPassStart(0.75F);

        assertTrue(lifecycle.isDispatched(RenderStage.WORLD));
        assertTrue(RenderHooks.worldStart().isAvailable());
        assertEquals(Arrays.asList("WORLD/START/0.25", "WORLD/START/0.75"), calls);
        assertThrows(IllegalStateException.class, () -> RenderHooks.install(new RenderLifecycle()));
    }
}
