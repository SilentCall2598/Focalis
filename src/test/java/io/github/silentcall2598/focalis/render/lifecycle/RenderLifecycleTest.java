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
        lifecycle.setFailureHandler((owner, stage, phase, kind, error) ->
                failures.add(owner + "@" + stage + "/" + phase + "/" + kind));
    }

    @Test
    void dispatchesOnlyToListenersOfTheStageInRegistrationOrder() {
        lifecycle.register(RenderStage.FRAME, "a", (stage, phase, kind, ticks) -> calls.add("a:" + phase));
        lifecycle.register(RenderStage.WORLD, "b", (stage, phase, kind, ticks) -> calls.add("b:" + phase));
        lifecycle.register(RenderStage.FRAME, "c", (stage, phase, kind, ticks) -> calls.add("c:" + phase));

        lifecycle.dispatch(RenderStage.FRAME, RenderPhase.START, 0.5F);

        assertEquals(Arrays.asList("a:START", "c:START"), calls);
    }

    @Test
    void listenerGetsBothPhasesOfItsStage() {
        lifecycle.register(RenderStage.WORLD, "a", (stage, phase, kind, ticks) -> calls.add(stage + ":" + phase));

        lifecycle.dispatch(RenderStage.WORLD, RenderPhase.START, 0F);
        lifecycle.dispatch(RenderStage.WORLD, RenderPhase.END, 0F);

        assertEquals(Arrays.asList("WORLD:START", "WORLD:END"), calls);
    }

    @Test
    void dispatchWithoutKindDeliversDefault() {
        lifecycle.register(RenderStage.FRAME, "a", (stage, phase, kind, ticks) -> calls.add(phase + ":" + kind));

        lifecycle.dispatch(RenderStage.FRAME, RenderPhase.START, 0F);

        assertEquals(Arrays.asList("START:DEFAULT"), calls);
    }

    @Test
    void startAndEndCarryTheExactKind() {
        lifecycle.markDispatched(RenderStage.TERRAIN);
        lifecycle.register(RenderStage.TERRAIN, "a", (stage, phase, kind, ticks) ->
                calls.add(stage + ":" + phase + ":" + kind + ":" + ticks));

        lifecycle.dispatch(RenderStage.TERRAIN, RenderPhase.START, RenderDrawKind.TERRAIN_CUTOUT_MIPPED, 0.25F);
        lifecycle.dispatch(RenderStage.TERRAIN, RenderPhase.END, RenderDrawKind.TERRAIN_CUTOUT_MIPPED, 0.25F);

        assertEquals(Arrays.asList("TERRAIN:START:TERRAIN_CUTOUT_MIPPED:0.25",
                "TERRAIN:END:TERRAIN_CUTOUT_MIPPED:0.25"), calls);
    }

    @Test
    void failureHandlerGetsTheKindTheListenerFailedIn() {
        lifecycle.markDispatched(RenderStage.ENTITIES);
        lifecycle.register(RenderStage.ENTITIES, "picky", (stage, phase, kind, ticks) -> {
            if (kind == RenderDrawKind.ENTITY_PASS_1) {
                throw new IllegalStateException("boom");
            }
        });

        lifecycle.dispatch(RenderStage.ENTITIES, RenderPhase.START, RenderDrawKind.ENTITY_PASS_0, 0F);
        lifecycle.dispatch(RenderStage.ENTITIES, RenderPhase.START, RenderDrawKind.ENTITY_PASS_1, 0F);

        assertEquals(Arrays.asList("picky@ENTITIES/START/ENTITY_PASS_1"), failures);
    }

    @Test
    void listenerAddedDuringDispatchWaitsForTheNextOne() {
        lifecycle.register(RenderStage.FRAME, "a", (stage, phase, kind, ticks) -> {
            calls.add("a");
            if (calls.size() == 1) {
                lifecycle.register(RenderStage.FRAME, "late", (s, p, k, t) -> calls.add("late"));
            }
        });

        lifecycle.dispatch(RenderStage.FRAME, RenderPhase.START, 0F);
        lifecycle.dispatch(RenderStage.FRAME, RenderPhase.END, 0F);

        assertEquals(Arrays.asList("a", "a", "late"), calls);
    }

    @Test
    void failingListenerDetachesOnlyItsOwner() {
        lifecycle.register(RenderStage.FRAME, "broken", (stage, phase, kind, ticks) -> {
            throw new IllegalStateException("boom");
        });
        lifecycle.register(RenderStage.WORLD, "broken", (stage, phase, kind, ticks) -> calls.add("broken:world"));
        lifecycle.register(RenderStage.FRAME, "healthy", (stage, phase, kind, ticks) -> calls.add("healthy:" + phase));

        lifecycle.dispatch(RenderStage.FRAME, RenderPhase.START, 0F);
        lifecycle.dispatch(RenderStage.FRAME, RenderPhase.END, 0F);
        lifecycle.dispatch(RenderStage.WORLD, RenderPhase.END, 0F);

        assertEquals(Arrays.asList("broken@FRAME/START/DEFAULT"), failures);
        assertEquals(Arrays.asList("healthy:START", "healthy:END"), calls);
    }

    @Test
    void linkageErrorsAreContainedLikeExceptions() {
        lifecycle.register(RenderStage.FRAME, "missing-dependency", (stage, phase, kind, ticks) -> {
            throw new NoClassDefFoundError("some/optional/Mod");
        });

        lifecycle.dispatch(RenderStage.FRAME, RenderPhase.START, 0F);

        assertEquals(Arrays.asList("missing-dependency@FRAME/START/DEFAULT"), failures);
    }

    @Test
    void listenerOfOwnerThatFailedEarlierInSameDispatchIsSkipped() {
        lifecycle.register(RenderStage.FRAME, "owner", (stage, phase, kind, ticks) -> {
            throw new IllegalStateException("boom");
        });
        lifecycle.register(RenderStage.FRAME, "owner", (stage, phase, kind, ticks) -> calls.add("second"));

        lifecycle.dispatch(RenderStage.FRAME, RenderPhase.START, 0F);

        assertTrue(calls.isEmpty());
    }

    @Test
    void reportsWhichStagesAreDispatched() {
        assertTrue(lifecycle.isDispatched(RenderStage.FRAME));
        assertFalse(lifecycle.isDispatched(RenderStage.TERRAIN));
    }
}
