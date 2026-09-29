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
        lifecycle.register(RenderStage.WORLD, "test", (stage, phase, kind, ticks) ->
                calls.add(stage + "/" + phase + "/" + ticks));

        RenderHooks.worldPassStart(0.25F);
        RenderHooks.worldPassEnd();
        RenderHooks.worldPassStart(0.75F);

        assertTrue(lifecycle.isDispatched(RenderStage.WORLD));
        assertTrue(RenderHooks.worldStart().isAvailable());
        assertEquals(Arrays.asList("WORLD/START/0.25", "WORLD/START/0.75"), calls);
        assertThrows(IllegalStateException.class, () -> RenderHooks.install(new RenderLifecycle()));
    }

    @Test
    void onlyThePlainSurfaceSkyIsBasic() {
        assertEquals(RenderDrawKind.SKY_BASIC, RenderHooks.skyKind(false, false, true));
        // A custom renderer wins even in a surface dimension.
        assertEquals(RenderDrawKind.DEFAULT, RenderHooks.skyKind(true, false, true));
        assertEquals(RenderDrawKind.DEFAULT, RenderHooks.skyKind(true, false, false));
        // renderSky checks for the End before it checks for a surface world.
        assertEquals(RenderDrawKind.DEFAULT, RenderHooks.skyKind(false, true, true));
        assertEquals(RenderDrawKind.DEFAULT, RenderHooks.skyKind(false, true, false));
        assertEquals(RenderDrawKind.DEFAULT, RenderHooks.skyKind(false, false, false));
    }

    @Test
    void preciseStagesCannotBeChanged() {
        assertThrows(UnsupportedOperationException.class, () -> RenderHooks.PRECISE_STAGES.add(RenderStage.GUI));
        assertThrows(UnsupportedOperationException.class, () -> RenderHooks.PRECISE_STAGES.remove(RenderStage.SKY));
    }
}
