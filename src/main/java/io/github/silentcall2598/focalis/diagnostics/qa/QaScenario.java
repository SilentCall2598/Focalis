// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.diagnostics.qa;

import io.github.silentcall2598.focalis.shader.ShaderFeature;
import net.minecraft.client.gui.GuiMainMenu;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * The QA scenarios. Each one drives the client through its steps and then judges the run. The checks every scenario
 * shares, like GL errors and state mismatches, are added by {@link QaProbe}.
 */
public enum QaScenario {

    VANILLA_BASELINE("vanilla-baseline",
            "Shaders off. Focalis has to leave the world-end GL state alone and cause no GL errors.") {
        @Override
        List<QaStep> steps() {
            List<QaStep> steps = enterWorld();
            steps.add(screenshot("vanilla-world"));
            steps.addAll(hudShot("vanilla-f3", false, true));
            steps.add(QaStep.waitTicks(100));
            return steps;
        }

        @Override
        void evaluate(QaReport r) {
            r.check("shaders-disabled", "DISABLED".equals(r.featureState(SHADERS)),
                    "shaders feature is " + r.featureState(SHADERS));
            r.check("post-pass-never-ran", r.postPass.renderedFrames == 0 && r.postPass.captures.isEmpty()
                            && r.postPass.programsBuilt.isEmpty(),
                    r.postPass.renderedFrames + " rendered frames, " + r.postPass.captures.size() + " captures");
        }
    },

    POST_PROCESS_SMOKE("post-process-smoke",
            "Test pack on. The pass has to run on every world frame through camera turns, F3, F1 and GUI screens.") {
        @Override
        List<QaStep> steps() {
            List<QaStep> steps = enterWorld();
            steps.add(screenshot("post-smoke-world"));
            for (int angle = 90; angle <= 360; angle += 90) {
                steps.add(turn(90, 10));
                steps.add(QaStep.waitTicks(4));
                steps.add(screenshot("post-smoke-turn-" + angle));
            }
            steps.addAll(hudShot("post-smoke-f3", false, true));
            steps.addAll(hudShot("post-smoke-f1", true, false));
            steps.addAll(screenShot("post-smoke-inventory", "inventory"));
            steps.addAll(screenShot("post-smoke-pause", "pause"));
            steps.addAll(screenShot("post-smoke-chat", "chat"));
            steps.add(QaStep.waitTicks(100));
            return steps;
        }

        @Override
        void evaluate(QaReport r) {
            checkShadersActive(r);
            checkSingleProgram(r);
            checkCaptureSizes(r);
            checkEveryFrameRendered(r);
            r.check("screenshots", r.screenshots.size() == 10, r.screenshots.size() + " of 10 saved");
        }
    },

    POST_PROCESS_STATE_RESTORE("post-process-state-restore",
            "Changes the GL state before the pass in several ways, then checks the pass handed each one back.") {
        @Override
        List<QaStep> steps() {
            List<QaStep> steps = enterWorld();
            steps.add(QaStep.action("start-state-changes", QaProbe::startPerturbation));
            steps.add(QaStep.until("state-changes-done", 2400, QaProbe::perturbationDone));
            steps.add(QaStep.waitTicks(4));
            steps.add(screenshot("post-state-after"));
            return steps;
        }

        @Override
        void evaluate(QaReport r) {
            checkShadersActive(r);
            Map<String, Integer> counts = r.perturbations;
            boolean covered = counts != null && counts.size() == QaProbe.PERTURBATION_KINDS;
            if (counts != null) {
                for (int count : counts.values()) {
                    covered &= count >= 20;
                }
            }
            r.check("every-state-change-exercised", covered, "per kind " + counts);
            int otherTarget = r.postPass.skips.getOrDefault("other-target", 0);
            int drawChanged = counts == null ? -1 : counts.getOrDefault("draw-framebuffer-0", -1);
            r.check("skips-only-when-drawing-elsewhere", otherTarget == drawChanged
                            && r.postPass.skips.size() <= 1,
                    "skips " + r.postPass.skips + ", frames with another draw framebuffer " + drawChanged);
            r.check("pass-ran-otherwise", r.postPass.renderedFrames + otherTarget == r.frames.world
                            && r.postPass.idleFrames == 0,
                    r.postPass.renderedFrames + " rendered + " + otherTarget + " skipped of " + r.frames.world);
        }
    },

    POST_PROCESS_HIGH_TEXTURE_UNIT("post-process-high-texture-unit",
            "Leaves a texture unit of 8 or more active before the pass and checks GL and GlStateManager agree after.") {
        @Override
        List<QaStep> steps() {
            List<QaStep> steps = enterWorld();
            steps.add(QaStep.action("start-unit-checks", QaProbe::startHighUnitChecks));
            steps.add(QaStep.until("unit-checks-done", 2400, QaProbe::highUnitChecksDone));
            steps.add(QaStep.waitTicks(4));
            steps.add(screenshot("post-high-unit-after"));
            return steps;
        }

        @Override
        void evaluate(QaReport r) {
            checkShadersActive(r);
            QaReport.HighTextureUnit units = r.highTextureUnit;
            r.check("unit-checks-ran", units != null && units.checks == QaProbe.HIGH_UNIT_CHECKS,
                    (units == null ? 0 : units.checks) + " of " + QaProbe.HIGH_UNIT_CHECKS);
            r.check("unit-checks-passed", units != null && units.failures == 0,
                    (units == null ? "none" : units.failures) + " failed");
            r.check("high-units-covered", units != null && units.highestUnitChecked >= 8,
                    "highest unit " + (units == null ? "none" : units.highestUnitChecked));
            checkEveryFrameRendered(r);
        }
    },

    POST_PROCESS_RESIZE("post-process-resize",
            "Resizes the window several times. Each new size has to give exactly one new capture of that size.") {
        @Override
        List<QaStep> steps() {
            List<QaStep> steps = enterWorld();
            int[][] sizes = {{1280, 720}, {1000, 600}, {640, 360}, {1600, 900}};
            for (int[] size : sizes) {
                steps.add(resize(size[0], size[1]));
                steps.add(screenshot("post-resize-" + size[0] + "x" + size[1]));
            }
            steps.add(resize(-1, -1));
            steps.add(screenshot("post-resize-restored"));
            steps.add(fullscreenRoundTrip());
            steps.add(QaStep.waitTicks(20));
            return steps;
        }

        @Override
        void evaluate(QaReport r) {
            checkShadersActive(r);
            checkCaptureSizes(r);
            r.check("old-captures-released", r.resources.failures.isEmpty() && r.resources.namesChecked > 0,
                    r.resources.namesChecked + " old names checked, " + r.resources.reusedNames
                            + " handed out again, failures " + r.resources.failures);
            checkEveryFrameRendered(r);
        }
    },

    POST_PROCESS_FAILURE("post-process-failure",
            "Throws inside the pass after its state changes. The feature has to fail cleanly and hand the state back.") {
        @Override
        List<QaStep> steps() {
            List<QaStep> steps = enterWorld();
            steps.add(QaStep.action("arm-failure", QaProbe::armFailure));
            steps.add(QaStep.until("failure-happened", 200, QaProbe::failureObserved));
            steps.add(QaStep.waitTicks(40));
            steps.add(screenshot("post-failure-after"));
            steps.add(QaStep.waitTicks(40));
            return steps;
        }

        @Override
        boolean expectsFeatureFailure() {
            return true;
        }

        @Override
        void evaluate(QaReport r) {
            QaReport.InjectedFailure failure = r.injectedFailure;
            r.check("feature-failed", failure != null && failure.featureFailed && failure.featureDetail != null
                            && failure.featureDetail.contains(QaProbe.INJECTED_FAILURE),
                    failure == null ? "no failure injected" : String.valueOf(failure.featureDetail));
            r.check("state-restored-on-failure", failure != null && failure.stateRestored,
                    "boundary on the failing frame");
            r.check("resources-released", failure != null && failure.resourcesReleased,
                    "capture framebuffer, textures and program deleted");
            r.check("pass-stopped", failure != null && failure.renderedAfterFailure == 0,
                    (failure == null ? "?" : failure.renderedAfterFailure) + " frames rendered after the failure");
        }
    },

    POST_PROCESS_BAD_PACK("post-process-bad-pack",
            "Selects a pack that doesn't compile. Rendering has to stay vanilla without failing the feature.") {
        @Override
        List<QaStep> steps() {
            List<QaStep> steps = enterWorld();
            steps.add(screenshot("post-bad-pack-world"));
            steps.add(QaStep.waitTicks(60));
            return steps;
        }

        @Override
        void evaluate(QaReport r) {
            checkShadersActive(r);
            String stopped = r.postPass.stopped;
            r.check("stopped-with-compile-error", stopped != null && stopped.contains("failed to compile"),
                    String.valueOf(stopped));
            r.check("nothing-created", r.postPass.programsBuilt.isEmpty() && r.postPass.captures.isEmpty()
                            && r.postPass.renderedFrames == 0,
                    r.postPass.programsBuilt.size() + " programs, " + r.postPass.captures.size() + " captures, "
                            + r.postPass.renderedFrames + " rendered frames");
        }
    },

    WORLD_RELOAD("world-reload",
            "Leaves and rejoins the world twice. The pass has to keep running without recreating its capture.") {
        @Override
        List<QaStep> steps() {
            List<QaStep> steps = enterWorld();
            steps.add(screenshot("reload-1"));
            for (int session = 2; session <= 3; session++) {
                steps.add(leaveWorld());
                steps.add(QaStep.waitTicks(20));
                steps.add(joinWorld());
                steps.add(QaStep.waitTicks(20));
                steps.add(screenshot("reload-" + session));
            }
            steps.add(QaStep.waitTicks(20));
            return steps;
        }

        @Override
        void evaluate(QaReport r) {
            checkShadersActive(r);
            r.check("three-sessions", r.worldSessions == 3, r.worldSessions + " world sessions");
            r.check("capture-kept", r.postPass.captures.size() == 1, r.postPass.captures.size() + " captures");
            boolean everySession = r.postPass.renderedPerSession.size() == 3;
            for (int rendered : r.postPass.renderedPerSession) {
                everySession &= rendered > 0;
            }
            r.check("pass-ran-every-session", everySession, "rendered per session " + r.postPass.renderedPerSession);
            r.check("no-skips", r.postPass.skips.isEmpty(), "skips " + r.postPass.skips);
        }
    },

    WORLD_LIFECYCLE("world-lifecycle",
            "Test pack on. Every WORLD START from the Mixin hook needs one WORLD END and the pass only runs at END,"
                    + " also behind a screen and after a rejoin.") {
        @Override
        List<QaStep> steps() {
            List<QaStep> steps = enterWorld();
            steps.add(screenshot("lifecycle-world"));
            steps.addAll(screenShot("lifecycle-pause", "pause"));
            steps.add(leaveWorld());
            steps.add(QaStep.waitTicks(20));
            steps.add(joinWorld());
            steps.add(QaStep.waitTicks(20));
            steps.add(screenshot("lifecycle-rejoined"));
            steps.add(QaStep.waitTicks(60));
            return steps;
        }

        @Override
        void evaluate(QaReport r) {
            checkShadersActive(r);
            QaReport.WorldPhases phases = r.worldPhases;
            // frames.world counts world passes at END, so a START that counted too would break this.
            r.check("pairs-match-world-passes", phases.pairs == r.frames.world,
                    phases.pairs + " start/end pairs, " + r.frames.world + " world passes");
            boolean everySession = phases.pairsPerSession.size() == 2;
            for (int pairs : phases.pairsPerSession) {
                everySession &= pairs > 0;
            }
            r.check("pairs-every-session", everySession, "pairs per session " + phases.pairsPerSession);
            checkEveryFrameRendered(r);
            r.check("screenshots", r.screenshots.size() == 3, r.screenshots.size() + " of 3 saved");
        }
    },

    RENDER_STAGES("render-stages",
            "Test pack on, in rain next to an entity, first below and then above cloud height. Every precise stage has"
                    + " to fire with balanced pairs and vanilla's count per world pass.") {
        @Override
        List<QaStep> steps() {
            List<QaStep> steps = enterWorld();
            steps.add(QaStep.action("command /weather rain", probe -> probe.command("/weather rain")));
            steps.add(QaStep.action("command /summon", probe -> probe.command("/summon pig ~2 ~ ~2 {NoAI:1b}")));
            steps.add(QaStep.waitTicks(40));
            steps.add(screenshot("stages-below-clouds"));
            steps.add(QaStep.action("fly", QaProbe::fly));
            steps.add(QaStep.action("command /tp", probe -> probe.command("/tp @p ~ 140 ~")));
            steps.add(QaStep.waitTicks(40));
            steps.add(screenshot("stages-above-clouds"));
            steps.add(QaStep.waitTicks(20));
            return steps;
        }

        @Override
        void evaluate(QaReport r) {
            checkShadersActive(r);
            // Vanilla draws each of these exactly once per world pass here. Anything else means a hook fires twice,
            // didn't apply, or reports the wrong kind.
            List<String> expected = Arrays.asList("SKY/DEFAULT", "TERRAIN/TERRAIN_SOLID",
                    "TERRAIN/TERRAIN_CUTOUT_MIPPED", "TERRAIN/TERRAIN_CUTOUT", "TRANSLUCENT/TERRAIN_TRANSLUCENT",
                    "ENTITIES/ENTITY_PASS_0", "ENTITIES/ENTITY_PASS_1", "PARTICLES/PARTICLES_LIT",
                    "PARTICLES/PARTICLES_NORMAL", "WEATHER/DEFAULT", "CLOUDS/DEFAULT", "HAND/DEFAULT");
            Map<String, Integer> pairs = new TreeMap<>();
            boolean everyPass = true;
            for (String kind : expected) {
                QaReport.StageCounts counts = r.renderStages.kinds.get(kind);
                int seen = counts == null ? 0 : counts.pairs;
                pairs.put(kind, seen);
                everyPass &= counts != null && seen == r.frames.world && counts.maxPerWorldPass == 1;
            }
            r.check("draw-kinds-every-world-pass", everyPass,
                    "pairs " + pairs + " over " + r.frames.world + " world passes");
            List<String> unexpected = new ArrayList<>(r.renderStages.kinds.keySet());
            unexpected.removeAll(expected);
            r.check("no-unexpected-draw-kinds", unexpected.isEmpty(), "unexpected " + unexpected);
            checkShaderRoles(r);
            checkEveryFrameRendered(r);
            r.check("screenshots", r.screenshots.size() == 2, r.screenshots.size() + " of 2 saved");
        }
    };

    static final String SHADERS = ShaderFeature.ID;

    private static final List<String> SETUP_COMMANDS = Arrays.asList("/gamerule sendCommandFeedback false",
            "/gamerule doDaylightCycle false", "/gamerule doWeatherCycle false", "/gamerule doMobSpawning false",
            "/time set 6000", "/weather clear", "/tp @p ~ ~ ~ 90 15");

    final String id;
    final String description;

    QaScenario(String id, String description) {
        this.id = id;
        this.description = description;
    }

    abstract List<QaStep> steps();

    abstract void evaluate(QaReport report);

    boolean expectsFeatureFailure() {
        return false;
    }

    @Nullable
    static QaScenario byId(String id) {
        for (QaScenario scenario : values()) {
            if (scenario.id.equals(id)) {
                return scenario;
            }
        }
        return null;
    }

    static List<String> ids() {
        List<String> ids = new ArrayList<>();
        for (QaScenario scenario : values()) {
            ids.add(scenario.id);
        }
        return ids;
    }

    // Every scenario starts in the same creative world with a fixed seed, time and weather.
    private static List<QaStep> enterWorld() {
        List<QaStep> steps = new ArrayList<>();
        steps.add(QaStep.until("main-menu", 2400, probe -> probe.mc().currentScreen instanceof GuiMainMenu));
        steps.add(joinWorld());
        for (String command : SETUP_COMMANDS) {
            steps.add(QaStep.action("command " + command, probe -> probe.command(command)));
        }
        steps.add(QaStep.waitTicks(20));
        steps.add(QaStep.action("clear-chat", QaProbe::clearChat));
        steps.add(QaStep.waitTicks(40));
        return steps;
    }

    private static QaStep joinWorld() {
        return new QaStep("join-world", 2400) {
            @Override
            boolean tick(QaProbe probe, int ticks) {
                if (ticks == 0) {
                    probe.launchWorld();
                    return false;
                }
                return probe.inWorld() && probe.ticksInWorld() >= 60;
            }
        };
    }

    // Players can only quit through the pause menu, so the server is always paused first. The probe does the same,
    // since a run that quit straight from a running game once hung and that path isn't one players ever take.
    private static QaStep leaveWorld() {
        return new QaStep("leave-world", 600) {
            @Override
            boolean tick(QaProbe probe, int ticks) {
                if (ticks == 0) {
                    probe.openScreen("pause");
                    return false;
                }
                if (ticks == 10) {
                    probe.leaveWorld();
                    return false;
                }
                return ticks > 10 && probe.mc().world == null && probe.mc().currentScreen instanceof GuiMainMenu;
            }
        };
    }

    private static QaStep screenshot(String name) {
        return QaStep.action("screenshot " + name, probe -> probe.screenshot(name));
    }

    private static List<QaStep> hudShot(String name, boolean hideGui, boolean debugScreen) {
        return Arrays.asList(QaStep.action("hud hide=" + hideGui + " debug=" + debugScreen,
                        probe -> probe.setHud(hideGui, debugScreen)),
                QaStep.waitTicks(4), screenshot(name),
                QaStep.action("hud reset", probe -> probe.setHud(false, false)));
    }

    private static List<QaStep> screenShot(String name, String screen) {
        return Arrays.asList(QaStep.action("open " + screen, probe -> probe.openScreen(screen)),
                QaStep.waitTicks(6), screenshot(name),
                QaStep.action("close " + screen, probe -> probe.openScreen("none")), QaStep.waitTicks(4));
    }

    private static QaStep turn(float degrees, int overTicks) {
        return new QaStep("turn " + degrees, overTicks + 1) {
            @Override
            boolean tick(QaProbe probe, int ticks) {
                if (ticks >= overTicks) {
                    return true;
                }
                probe.turn(degrees / overTicks);
                return false;
            }
        };
    }

    // A width of -1 means the size the window had when the run started.
    private static QaStep resize(int width, int height) {
        return new QaStep(width < 0 ? "resize back" : "resize " + width + "x" + height, 600) {
            private int targetWidth;
            private int targetHeight;
            private int matchedAtFrame = -1;

            @Override
            boolean tick(QaProbe probe, int ticks) {
                if (ticks == 0) {
                    targetWidth = width < 0 ? probe.initialWidth() : width;
                    targetHeight = height < 0 ? probe.initialHeight() : height;
                    probe.requestResize(targetWidth, targetHeight);
                }
                if (!probe.displayIs(targetWidth, targetHeight)) {
                    return false;
                }
                if (matchedAtFrame < 0) {
                    matchedAtFrame = probe.worldFrames();
                }
                if (probe.worldFrames() - matchedAtFrame < 30) {
                    return false;
                }
                probe.clearRequest();
                probe.expectCaptureSize(targetWidth, targetHeight);
                return true;
            }
        };
    }

    // Fullscreen takes over the whole screen, so the runner only allows it when asked to.
    private static QaStep fullscreenRoundTrip() {
        return new QaStep("fullscreen round trip", 1200) {
            private int phase;
            private int width;
            private int height;
            private int changedAtFrame = -1;

            @Override
            boolean tick(QaProbe probe, int ticks) {
                if (!probe.fullscreenAllowed()) {
                    probe.note("Fullscreen was skipped because the run didn't allow it");
                    return true;
                }
                if (phase == 0 || phase == 2) {
                    width = probe.mc().displayWidth;
                    height = probe.mc().displayHeight;
                    changedAtFrame = -1;
                    probe.toggleFullscreen();
                    phase++;
                    return false;
                }
                if (probe.displayIs(width, height)) {
                    return false;
                }
                if (changedAtFrame < 0) {
                    changedAtFrame = probe.worldFrames();
                }
                if (probe.worldFrames() - changedAtFrame < 30) {
                    return false;
                }
                probe.expectCaptureSize(probe.mc().displayWidth, probe.mc().displayHeight);
                if (phase == 1) {
                    probe.screenshot("post-resize-fullscreen");
                    phase = 2;
                    return false;
                }
                return true;
            }
        };
    }

    // What the shader router makes of one world pass here. Each role at most this often per pass and exactly this
    // often on average means every single pass had exactly this many.
    private static void checkShaderRoles(QaReport r) {
        Map<String, Integer> perPass = new TreeMap<>();
        for (String role : Arrays.asList("SKY", "TERRAIN_SOLID", "TERRAIN_CUTOUT_MIPPED", "TERRAIN_CUTOUT",
                "TERRAIN_TRANSLUCENT", "PARTICLES_LIT", "PARTICLES_NORMAL", "WEATHER", "CLOUDS", "HAND")) {
            perPass.put(role, 1);
        }
        perPass.put("ENTITIES", 2);
        Map<String, Integer> counts = new TreeMap<>();
        boolean everyPass = true;
        for (Map.Entry<String, Integer> role : perPass.entrySet()) {
            QaReport.RoleCounts seen = r.shaderRoutes.roles.get(role.getKey());
            counts.put(role.getKey(), seen == null ? 0 : seen.count);
            everyPass &= seen != null && seen.count == role.getValue() * r.frames.world
                    && seen.maxPerWorldPass == role.getValue();
        }
        r.check("shader-roles-every-world-pass", everyPass,
                "roles " + counts + " over " + r.frames.world + " world passes, per pass " + perPass);
        List<String> other = new ArrayList<>(r.shaderRoutes.roles.keySet());
        other.removeAll(perPass.keySet());
        r.check("no-unclassified-shader-roles", other.isEmpty(), "other roles " + other);
    }

    private static void checkShadersActive(QaReport r) {
        r.check("shaders-active", "ACTIVE".equals(r.featureState(SHADERS)),
                "shaders feature is " + r.featureState(SHADERS));
    }

    private static void checkSingleProgram(QaReport r) {
        r.check("program-built-once", r.postPass.programsBuilt.size() == 1,
                r.postPass.programsBuilt.size() + " programs built");
    }

    private static void checkCaptureSizes(QaReport r) {
        List<String> actual = new ArrayList<>();
        for (QaReport.Capture capture : r.postPass.captures) {
            actual.add(capture.width + "x" + capture.height);
        }
        r.check("capture-per-size", actual.equals(r.postPass.expectedCaptureSizes),
                "captures " + actual + ", expected " + r.postPass.expectedCaptureSizes);
    }

    private static void checkEveryFrameRendered(QaReport r) {
        r.check("pass-every-world-frame", r.postPass.renderedFrames == r.frames.world
                        && r.postPass.idleFrames == 0 && r.postPass.skips.isEmpty(),
                r.postPass.renderedFrames + " of " + r.frames.world + " world frames, skips " + r.postPass.skips);
    }
}
