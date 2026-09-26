// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.feature;

import io.github.silentcall2598.focalis.compat.CompatibilityReport;
import io.github.silentcall2598.focalis.config.FocalisConfig;
import io.github.silentcall2598.focalis.render.lifecycle.RenderLifecycle;
import io.github.silentcall2598.focalis.render.lifecycle.RenderPhase;
import io.github.silentcall2598.focalis.render.lifecycle.RenderStage;
import net.minecraftforge.common.config.Configuration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FeatureManagerTest {

    private static final CompatibilityReport NO_MODS = CompatibilityReport.of(Collections.<String>emptyList(), false);

    private RenderLifecycle lifecycle;
    private FeatureManager manager;
    private Configuration configuration;
    private List<String> calls;

    @BeforeEach
    void setUp() {
        lifecycle = new RenderLifecycle();
        lifecycle.markDispatched(RenderStage.FRAME);
        manager = new FeatureManager(lifecycle);
        configuration = new Configuration();
        calls = new ArrayList<>();
    }

    @Test
    void disabledFeatureIsNeitherCheckedNorSetUp() {
        manager.register(new TestFeature("off", false));

        initialize(NO_MODS);

        assertEquals(FeatureState.DISABLED, manager.status("off").state());
        assertTrue(calls.isEmpty());
    }

    @Test
    void configToggleOverridesDefault() {
        manager.register(new TestFeature("off_by_default", false));
        configuration.get("features.off_by_default", "enabled", false).set(true);

        initialize(NO_MODS);

        assertEquals(FeatureState.ACTIVE, manager.status("off_by_default").state());
    }

    @Test
    void enabledFeatureIsSetUpAndReceivesRenderStages() {
        manager.register(new TestFeature("on", true));

        initialize(NO_MODS);
        lifecycle.dispatch(RenderStage.FRAME, RenderPhase.START, 0F);

        assertEquals(FeatureState.ACTIVE, manager.status("on").state());
        assertEquals(Arrays.asList("on:setup", "on:FRAME"), calls);
    }

    @Test
    void unavailableFeatureIsNotSetUp() {
        manager.register(new TestFeature("needs_no_conflict", true) {
            @Override
            protected Availability checkAvailability(CompatibilityReport compat) {
                return compat.isModLoaded("conflicting") ? Availability.unavailable("conflicting is installed")
                        : Availability.AVAILABLE;
            }
        });

        initialize(CompatibilityReport.of(Collections.singletonList("conflicting"), false));

        FeatureStatus status = manager.status("needs_no_conflict");
        assertEquals(FeatureState.UNAVAILABLE, status.state());
        assertEquals("conflicting is installed", status.detail());
        assertTrue(calls.isEmpty());
    }

    @Test
    void failedSetupIsCleanedUpWithoutAffectingOtherFeatures() {
        manager.register(new TestFeature("broken", true) {
            @Override
            protected void setup(FeatureContext context) {
                super.setup(context);
                throw new IllegalStateException("setup failed");
            }
        });
        manager.register(new TestFeature("healthy", true));

        initialize(NO_MODS);
        lifecycle.dispatch(RenderStage.FRAME, RenderPhase.START, 0F);

        assertEquals(FeatureState.FAILED, manager.status("broken").state());
        assertEquals(FeatureState.ACTIVE, manager.status("healthy").state());
        assertEquals(Arrays.asList("broken:setup", "broken:cleanup", "healthy:setup", "healthy:FRAME"), calls);
    }

    @Test
    void renderFailureCleansUpOnlyTheFailingFeatureOnce() {
        manager.register(new TestFeature("crashes_while_rendering", true) {
            @Override
            protected void setup(FeatureContext context) {
                context.addRenderListener(RenderStage.FRAME, (stage, phase, partialTicks) -> {
                    throw new IllegalStateException("render failed");
                });
            }
        });
        manager.register(new TestFeature("healthy", true));

        initialize(NO_MODS);
        lifecycle.dispatch(RenderStage.FRAME, RenderPhase.START, 0F);
        lifecycle.dispatch(RenderStage.FRAME, RenderPhase.END, 0F);

        assertEquals(FeatureState.FAILED, manager.status("crashes_while_rendering").state());
        assertEquals(FeatureState.ACTIVE, manager.status("healthy").state());
        assertEquals(Arrays.asList("healthy:setup", "crashes_while_rendering:cleanup", "healthy:FRAME",
                "healthy:FRAME"), calls);
    }

    @Test
    void disableCleansUpOnceAndDetachesListeners() {
        manager.register(new TestFeature("on", true));
        initialize(NO_MODS);

        manager.disable("on");
        manager.disable("on");
        lifecycle.dispatch(RenderStage.FRAME, RenderPhase.START, 0F);

        assertEquals(FeatureState.DISABLED, manager.status("on").state());
        assertEquals(Arrays.asList("on:setup", "on:cleanup"), calls);
    }

    @Test
    void failingCleanupDuringDisableIsContainedAndMarksFeatureFailed() {
        manager.register(new TestFeature("bad_cleanup", true) {
            @Override
            protected void cleanup() {
                super.cleanup();
                throw new IllegalStateException("cleanup failed");
            }
        });
        initialize(NO_MODS);

        assertDoesNotThrow(() -> manager.disable("bad_cleanup"));
        manager.disable("bad_cleanup");
        lifecycle.dispatch(RenderStage.FRAME, RenderPhase.START, 0F);

        assertEquals(FeatureState.FAILED, manager.status("bad_cleanup").state());
        assertEquals(Arrays.asList("bad_cleanup:setup", "bad_cleanup:cleanup"), calls);
    }

    @Test
    void failingCleanupDuringFailureIsContained() {
        manager.register(new TestFeature("broken_twice", true) {
            @Override
            protected void setup(FeatureContext context) {
                throw new IllegalStateException("setup failed");
            }

            @Override
            protected void cleanup() {
                throw new IllegalStateException("cleanup failed");
            }
        });
        manager.register(new TestFeature("healthy", true));

        initialize(NO_MODS);

        FeatureStatus status = manager.status("broken_twice");
        assertEquals(FeatureState.FAILED, status.state());
        assertTrue(status.detail().contains("setup failed"));
        assertEquals(FeatureState.ACTIVE, manager.status("healthy").state());
    }

    @Test
    void contextRejectsListenersOnceTheFeatureHasFailed() {
        FeatureContext[] saved = new FeatureContext[1];
        manager.register(new TestFeature("keeps_context", true) {
            @Override
            protected void setup(FeatureContext context) {
                saved[0] = context;
                context.addRenderListener(RenderStage.FRAME, (stage, phase, partialTicks) -> {
                    throw new IllegalStateException("render failed");
                });
            }
        });
        initialize(NO_MODS);
        lifecycle.dispatch(RenderStage.FRAME, RenderPhase.START, 0F);

        assertThrows(IllegalStateException.class, () -> saved[0].addRenderListener(RenderStage.FRAME,
                (stage, phase, partialTicks) -> calls.add("late listener")));
        lifecycle.dispatch(RenderStage.FRAME, RenderPhase.START, 0F);

        assertEquals(Arrays.asList("keeps_context:cleanup"), calls);
    }

    @Test
    void rejectsDuplicateAndMalformedIds() {
        manager.register(new TestFeature("same", true));

        assertThrows(IllegalArgumentException.class, () -> manager.register(new TestFeature("same", true)));
        assertThrows(IllegalArgumentException.class, () -> new TestFeature("Not-Valid", true));
    }

    @Test
    void rejectsRegistrationAfterInitialization() {
        initialize(NO_MODS);

        assertThrows(IllegalStateException.class, () -> manager.register(new TestFeature("late", true)));
    }

    private void initialize(CompatibilityReport compat) {
        manager.initialize(new FocalisConfig(configuration), compat);
    }

    private class TestFeature extends Feature {

        TestFeature(String id, boolean enabledByDefault) {
            super(id, "Test feature.", enabledByDefault);
        }

        @Override
        protected void setup(FeatureContext context) {
            calls.add(id() + ":setup");
            context.addRenderListener(RenderStage.FRAME, (stage, phase, partialTicks) -> calls.add(id() + ":" + stage));
        }

        @Override
        protected void cleanup() {
            calls.add(id() + ":cleanup");
        }
    }
}
