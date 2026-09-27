// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.render.lifecycle;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RenderLifecycleTest {

    private RenderLifecycle lifecycle;
    private List<String> calls;
    private List<String> failures;

    @BeforeEach
    void setUp() {
        lifecycle = new RenderLifecycle();
        lifecycle.markDispatched(RenderStage.FRAME);
        lifecycle.markDispatched(RenderStage.WORLD);
        calls = new ArrayList<>();
        failures = new ArrayList<>();
        lifecycle.setFailureHandler((owner, stage, phase, error) -> failures.add(owner + "@" + stage + "/" + phase));
    }

    @Test
    void dispatchesOnlyToListenersOfTheStageInRegistrationOrder() {
        lifecycle.register(RenderStage.FRAME, "a", (stage, phase, partialTicks) -> calls.add("a:" + phase));
        lifecycle.register(RenderStage.WORLD, "b", (stage, phase, partialTicks) -> calls.add("b:" + phase));
        lifecycle.register(RenderStage.FRAME, "c", (stage, phase, partialTicks) -> calls.add("c:" + phase));

        lifecycle.dispatch(RenderStage.FRAME, RenderPhase.START, 0.5F);

        assertEquals(Arrays.asList("a:START", "c:START"), calls);
    }

    @Test
    void listenerGetsBothPhasesOfItsStage() {
        lifecycle.register(RenderStage.WORLD, "a", (stage, phase, partialTicks) -> calls.add(stage + ":" + phase));

        lifecycle.dispatch(RenderStage.WORLD, RenderPhase.START, 0F);
        lifecycle.dispatch(RenderStage.WORLD, RenderPhase.END, 0F);

        assertEquals(Arrays.asList("WORLD:START", "WORLD:END"), calls);
    }

    @Test
    void listenerAddedDuringDispatchWaitsForTheNextOne() {
        lifecycle.register(RenderStage.FRAME, "a", (stage, phase, partialTicks) -> {
            calls.add("a");
            if (calls.size() == 1) {
                lifecycle.register(RenderStage.FRAME, "late", (s, p, t) -> calls.add("late"));
            }
        });

        lifecycle.dispatch(RenderStage.FRAME, RenderPhase.START, 0F);
        lifecycle.dispatch(RenderStage.FRAME, RenderPhase.END, 0F);

        assertEquals(Arrays.asList("a", "a", "late"), calls);
    }

    @Test
    void failingListenerDetachesOnlyItsOwner() {
        lifecycle.register(RenderStage.FRAME, "broken", (stage, phase, partialTicks) -> {
            throw new IllegalStateException("boom");
        });
        lifecycle.register(RenderStage.WORLD, "broken", (stage, phase, partialTicks) -> calls.add("broken:world"));
        lifecycle.register(RenderStage.FRAME, "healthy", (stage, phase, partialTicks) -> calls.add("healthy:" + phase));

        lifecycle.dispatch(RenderStage.FRAME, RenderPhase.START, 0F);
        lifecycle.dispatch(RenderStage.FRAME, RenderPhase.END, 0F);
        lifecycle.dispatch(RenderStage.WORLD, RenderPhase.END, 0F);

        assertEquals(Arrays.asList("broken@FRAME/START"), failures);
        assertEquals(Arrays.asList("healthy:START", "healthy:END"), calls);
    }

    @Test
    void linkageErrorsAreContainedLikeExceptions() {
        lifecycle.register(RenderStage.FRAME, "missing-dependency", (stage, phase, partialTicks) -> {
            throw new NoClassDefFoundError("some/optional/Mod");
        });

        lifecycle.dispatch(RenderStage.FRAME, RenderPhase.START, 0F);

        assertEquals(Arrays.asList("missing-dependency@FRAME/START"), failures);
    }

    @Test
    void listenerOfOwnerThatFailedEarlierInSameDispatchIsSkipped() {
        lifecycle.register(RenderStage.FRAME, "owner", (stage, phase, partialTicks) -> {
            throw new IllegalStateException("boom");
        });
        lifecycle.register(RenderStage.FRAME, "owner", (stage, phase, partialTicks) -> calls.add("second"));

        lifecycle.dispatch(RenderStage.FRAME, RenderPhase.START, 0F);

        assertTrue(calls.isEmpty());
    }

    @Test
    void reportsWhichStagesAreDispatched() {
        assertTrue(lifecycle.isDispatched(RenderStage.FRAME));
        assertFalse(lifecycle.isDispatched(RenderStage.TERRAIN));
    }
}
