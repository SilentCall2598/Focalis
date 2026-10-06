// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.diagnostics.qa;

import io.github.silentcall2598.focalis.render.target.WorldTargetFeature;
import io.github.silentcall2598.focalis.shader.ShaderFeature;
import io.github.silentcall2598.focalis.shader.WorldProgramsFeature;
import net.minecraft.client.gui.GuiMainMenu;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
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
            steps.add(fullscreenRoundTrip("post-resize-fullscreen"));
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
            "Test pack on, in rain next to an entity and in view of a glowing one, first below and then above cloud"
                    + " height. Every precise stage has to fire with balanced pairs and vanilla's count per world"
                    + " pass.") {
        @Override
        List<QaStep> steps() {
            return stageTour("stages");
        }

        @Override
        void evaluate(QaReport r) {
            checkShadersActive(r);
            // Vanilla draws each of these exactly once per world pass here, except the sun and moon inside the sky.
            // Anything else means a hook fires twice, didn't apply, or reports the wrong kind.
            Map<String, Integer> perPass = kindsPerPass();
            Map<String, Integer> pairs = new TreeMap<>();
            boolean everyPass = true;
            for (Map.Entry<String, Integer> kind : perPass.entrySet()) {
                QaReport.StageCounts counts = r.renderStages.kinds.get(kind.getKey());
                int seen = counts == null ? 0 : counts.pairs;
                pairs.put(kind.getKey(), seen);
                everyPass &= counts != null && seen == kind.getValue() * r.frames.world
                        && counts.maxPerWorldPass == kind.getValue();
            }
            r.check("draw-kinds-every-world-pass", everyPass,
                    "pairs " + pairs + " over " + r.frames.world + " world passes, per pass " + perPass);
            List<String> unexpected = new ArrayList<>(r.renderStages.kinds.keySet());
            unexpected.removeAll(perPass.keySet());
            unexpected.remove(OUTLINES);
            r.check("no-unexpected-draw-kinds", unexpected.isEmpty(), "unexpected " + unexpected);
            checkOutlinesSeen(r);
            checkShaderRoles(r);
            checkEveryFrameRendered(r);
            r.check("screenshots", r.screenshots.size() == 2, r.screenshots.size() + " of 2 saved");
        }
    },

    WORLD_TARGET_FAILURE("world-target-failure",
            "World target on. Throws inside the copy back at the end of a world pass. The feature has to fail"
                    + " cleanly, put Minecraft's framebuffer back and stop redirecting.") {
        @Override
        List<QaStep> steps() {
            List<QaStep> steps = enterWorld();
            steps.add(screenshot("world-target-before-failure"));
            steps.add(QaStep.action("arm-world-target-failure", QaProbe::armWorldTargetFailure));
            steps.add(QaStep.until("failure-happened", 200, QaProbe::failureObserved));
            steps.add(QaStep.waitTicks(40));
            steps.add(screenshot("world-target-after-failure"));
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
            QaReport.WorldTarget world = r.worldTarget;
            r.check("feature-failed", failure != null && failure.featureFailed && failure.featureDetail != null
                            && failure.featureDetail.contains(QaProbe.INJECTED_FAILURE),
                    failure == null ? "no failure injected" : String.valueOf(failure.featureDetail));
            r.check("framebuffers-restored-on-failure", failure != null && failure.stateRestored,
                    "boundary on the failing world pass");
            r.check("target-released", failure != null && failure.resourcesReleased,
                    "framebuffer and both textures deleted");
            r.check("redirect-stopped", failure != null && failure.frame > 0 && failure.renderedAfterFailure == 0
                            && world.redirectedPasses == failure.frame,
                    world.redirectedPasses + " passes redirected, failure in pass "
                            + (failure == null ? "?" : failure.frame));
            r.check("vanilla-after-failure", world.drawSamples > 0 && world.drawMismatches == 0,
                    world.drawMismatches + " of " + world.drawSamples + " sampled world passes drew elsewhere");
            r.check("screenshots", r.screenshots.size() == 2, r.screenshots.size() + " of 2 saved");
        }
    },

    WORLD_PROGRAM_BINDING("world-program-binding",
            "World target and world programs on, with the same tour as render-stages. Every world stage has to have"
                    + " its role's program current while vanilla draws it and get the previous one back at its END.") {
        @Override
        List<QaStep> steps() {
            return stageTour("programs");
        }

        @Override
        boolean injectsRendererLeaks() {
            return true;
        }

        @Override
        void evaluate(QaReport r) {
            checkFeatureActive(r, WORLD_PROGRAMS);
            checkFeatureActive(r, WorldTargetFeature.ID);
            QaReport.WorldPrograms programs = r.worldPrograms;
            // Every stage the tour draws, minus HAND, which is never bound.
            Map<String, Integer> perPass = kindsPerPass();
            perPass.remove("HAND/DEFAULT");
            boolean everyPass = r.frames.world > 0;
            for (Map.Entry<String, Integer> kind : perPass.entrySet()) {
                everyPass &= programs.bound.getOrDefault(kind.getKey(), 0) == kind.getValue() * r.frames.world;
            }
            r.check("bound-every-world-pass", everyPass && programs.bound.keySet().equals(perPass.keySet()),
                    "bound " + programs.bound + " over " + r.frames.world + " world passes, per pass " + perPass);
            r.check("sun-and-moon-restore-sky", programs.nestedSkyRestores == 2 * r.frames.world,
                    programs.nestedSkyRestores + " sky programs put back over " + r.frames.world + " world passes");
            r.check("hand-checked-every-pass", programs.handChecks == r.frames.world,
                    programs.handChecks + " HAND starts over " + r.frames.world + " world passes");
            checkOutlinesSeen(r);
            QaReport.StageCounts outlines = r.renderStages.kinds.get(OUTLINES);
            int outlinePairs = outlines == null ? 0 : outlines.pairs;
            r.check("outlines-left-to-vanilla", programs.outlineStarts == outlinePairs && outlinePairs > 0
                            && programs.outlineProblems == 0,
                    programs.outlineProblems + " of " + programs.outlineStarts + " outline starts kept a Focalis"
                            + " program");
            r.check("entities-program-after-outlines", programs.outlineResumes == outlinePairs
                            && programs.outlineResumeProblems == 0,
                    programs.outlineResumeProblems + " of " + programs.outlineResumes + " outline ends didn't bind"
                            + " the entities program again");
            r.check("chests-drawn-with-entities-program", programs.blockEntitySamplesAfterOutlines > 0
                            && programs.blockEntityMismatches == 0,
                    programs.blockEntityMismatches + " of " + programs.blockEntitySamples + " chest draws had another"
                            + " program, " + programs.blockEntitySamplesAfterOutlines + " of them after the outlines");
            r.check("renderer-leaks-injected", programs.entityLeaksInjected > 0
                            && programs.blockEntityLeaksInjected > 0,
                    programs.entityLeaksInjected + " pig and " + programs.blockEntityLeaksInjected + " chest renderers"
                            + " left another program bound");
            r.check("program-back-after-renderers", programs.entityRendererChecks > 0
                            && programs.blockEntityRendererChecks > 0 && programs.rendererMismatches == 0
                            && programs.entityRenderersLeftOther >= programs.entityLeaksInjected
                            && programs.blockEntityRenderersLeftOther >= programs.blockEntityLeaksInjected,
                    programs.rendererMismatches + " of " + programs.entityRendererChecks + " entity and "
                            + programs.blockEntityRendererChecks + " block entity renderers returned without the"
                            + " ENTITIES program, " + programs.entityRenderersLeftOther + " and "
                            + programs.blockEntityRenderersLeftOther + " had left another one");
            checkTestPackPrograms(r);
            r.check("screenshots", r.screenshots.size() == 2, r.screenshots.size() + " of 2 saved");
        }
    },

    WORLD_PROGRAM_FAILURE("world-program-failure",
            "World target and world programs on. Throws right after the sun's scope opened inside the sky's. The"
                    + " feature has to fail cleanly, unwind both scopes, delete its programs and never bind again.") {
        @Override
        List<QaStep> steps() {
            List<QaStep> steps = enterWorld();
            steps.add(screenshot("world-programs-before-failure"));
            steps.add(QaStep.action("arm-world-program-failure", QaProbe::armWorldProgramFailure));
            steps.add(QaStep.until("failure-happened", 200, QaProbe::failureObserved));
            steps.add(QaStep.waitTicks(40));
            steps.add(screenshot("world-programs-after-failure"));
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
            QaReport.WorldPrograms programs = r.worldPrograms;
            r.check("feature-failed", failure != null && failure.featureFailed && failure.featureDetail != null
                            && failure.featureDetail.contains(QaProbe.INJECTED_FAILURE),
                    failure == null ? "no failure injected" : String.valueOf(failure.featureDetail));
            r.check("program-restored-on-failure", failure != null && failure.stateRestored,
                    "current program right after the failing START");
            r.check("programs-released", failure != null && failure.resourcesReleased,
                    programs.roles.size() + " roles, every program deleted");
            r.check("bound-before-failure", programs.builds == 1 && programs.scopes > 0,
                    programs.builds + " builds, " + programs.scopes + " scopes");
            r.check("binding-stopped", failure != null && failure.frame > 0 && failure.renderedAfterFailure == 0
                            && programs.unboundLeaks == 0 && programs.bindMismatches == 0
                            && programs.restoreMismatches == 0 && programs.handProblems == 0,
                    (failure == null ? "?" : failure.renderedAfterFailure) + " scopes after the failure, problems "
                            + programs.problems);
            checkFeatureActive(r, WorldTargetFeature.ID);
            r.check("screenshots", r.screenshots.size() == 2, r.screenshots.size() + " of 2 saved");
        }
    },

    WORLD_PROGRAM_BAD_PACK("world-program-bad-pack",
            "World target and world programs on, with a pack where no program compiles. Rendering has to stay"
                    + " vanilla without failing the feature, the folder is built only once, and nothing is ever"
                    + " bound.") {
        @Override
        List<QaStep> steps() {
            List<QaStep> steps = enterWorld();
            steps.add(screenshot("world-programs-bad-pack"));
            steps.add(QaStep.waitTicks(60));
            return steps;
        }

        @Override
        boolean expectsUnusableWorldPrograms() {
            return true;
        }

        @Override
        void evaluate(QaReport r) {
            checkFeatureActive(r, WORLD_PROGRAMS);
            checkFeatureActive(r, WorldTargetFeature.ID);
            QaReport.WorldPrograms programs = r.worldPrograms;
            r.check("built-once-without-programs", programs.stopped == null
                            && programs.folderBuilds.equals(Collections.singletonMap("shaders", 1))
                            && programs.folderRoles.equals(Collections.singletonMap("shaders",
                                    Collections.<String, QaReport.BuiltProgram>emptyMap())),
                    "builds per folder " + programs.folderBuilds + ", roles " + programs.folderRoles
                            + (programs.stopped == null ? "" : ", stopped: " + programs.stopped));
            r.check("nothing-bound", programs.scopes > 0 && programs.bound.isEmpty() && programs.roles.isEmpty()
                            && programs.bindMismatches == 0 && programs.unboundLeaks == 0,
                    programs.scopes + " scopes, bound " + programs.bound + ", " + programs.bindMismatches
                            + " with the wrong program, " + programs.unboundLeaks
                            + " Focalis programs without a scope");
        }
    },

    WORLD_PROGRAM_DIMENSIONS("world-program-dimensions",
            "World programs on, with the focalis-dimension-routes folder pack. Goes through the overworld, Nether and"
                    + " End, back and rejoins. Each dimension has to use its own world folder or the main one, and each"
                    + " folder is built only once.") {
        @Override
        List<QaStep> steps() {
            return dimensionTour("dimensions");
        }

        @Override
        void evaluate(QaReport r) {
            checkDimensionPrograms(r, false);
        }
    },

    WORLD_PROGRAM_DIMENSIONS_ZIP("world-program-dimensions-zip",
            "Like world-program-dimensions, with the same pack as a ZIP that also has an empty world1 folder entry."
                    + " The End then has to draw the normal way instead of using the main folder.") {
        @Override
        List<QaStep> steps() {
            return dimensionTour("dimensions-zip");
        }

        @Override
        void evaluate(QaReport r) {
            checkDimensionPrograms(r, true);
        }
    },

    WORLD_PROGRAM_SAMPLERS("world-program-samplers",
            "World target and world programs on, with the focalis-world-samplers pack, whose lit programs draw the"
                    + " texture Minecraft bound times the lightmap. A red wool wall, a white sheep and a chest have to"
                    + " show their own colors at the middle of the screen, and the wall has to darken at night.") {
        @Override
        List<QaStep> steps() {
            return samplerScene("samplers");
        }

        @Override
        void evaluate(QaReport r) {
            checkFeatureActive(r, WORLD_PROGRAMS);
            checkFeatureActive(r, WorldTargetFeature.ID);
            checkSamplerColors(r);
            QaReport.WorldPrograms programs = r.worldPrograms;
            Map<String, String> held = new TreeMap<>();
            for (String lit : Arrays.asList("gbuffers_terrain", "gbuffers_water", "gbuffers_entities",
                    "gbuffers_textured_lit")) {
                held.put(lit, "texture=0 lightmap=1");
            }
            for (String unlit : Arrays.asList("gbuffers_textured", "gbuffers_skytextured", "gbuffers_clouds")) {
                held.put(unlit, "texture=0 lightmap=-");
            }
            // Declares the lightmap without using it. Drivers usually drop it, but they don't have to. Its
            // gbuffers_basic twin is never picked, every role has something closer.
            held.put("gbuffers_skybasic", "texture=- lightmap=-");
            Map<String, String> built = new TreeMap<>(programs.samplers);
            built.keySet().retainAll(held.keySet());
            built.replace("gbuffers_skybasic", "texture=- lightmap=1", "texture=- lightmap=-");
            r.check("samplers-held-by-each-program", built.equals(held) && programs.samplerMismatches == 0,
                    "held " + programs.samplers);
            r.check("sheep-and-chest-drew-with-own-texture-and-lightmap", programs.ownTextureChecks > 0
                            && programs.lightmapChecks > 0 && programs.ownTextureMismatches == 0
                            && programs.lightmapMismatches == 0,
                    programs.ownTextureMismatches + " of " + programs.ownTextureChecks + " without their own"
                            + " texture, " + programs.lightmapMismatches + " of " + programs.lightmapChecks
                            + " without the lightmap");
            r.check("screenshots", r.screenshots.size() == 4, r.screenshots.size() + " of 4 saved");
        }
    },

    WORLD_PROGRAM_FRAME_INPUTS("world-program-frame-inputs",
            "World programs on, with focalis-world-routes, whose programs use every view and frame uniform. What GL"
                    + " holds has to match the probe's own frame clock and the surface drawn into, through resizes,"
                    + " fullscreen, anaglyph, a pause screen, the Nether and a rejoin.") {
        @Override
        List<QaStep> steps() {
            List<QaStep> steps = enterWorld();
            steps.add(QaStep.action("command /summon", probe -> probe.command("/summon pig ~2 ~ ~2 {NoAI:1b}")));
            steps.add(QaStep.action("command /setblock chest", probe -> probe.command(
                    "/setblock ~-3 ~ ~1 minecraft:chest")));
            steps.add(QaStep.waitTicks(40));
            steps.add(screenshot("frame-inputs-start"));
            // Another aspect ratio, then the first aspect ratio at another size.
            for (int[] size : FRAME_INPUT_SIZES) {
                steps.add(resize(size[0], size[1]));
                steps.add(screenshot("frame-inputs-" + size[0] + "x" + size[1]));
            }
            steps.add(resize(-1, -1));
            steps.add(fullscreenRoundTrip("frame-inputs-fullscreen"));
            steps.add(QaStep.action("anaglyph on", probe -> probe.setAnaglyph(true)));
            steps.add(QaStep.waitTicks(40));
            steps.add(screenshot("frame-inputs-anaglyph"));
            steps.add(QaStep.action("anaglyph off", probe -> probe.setAnaglyph(false)));
            steps.add(QaStep.action("open pause", probe -> probe.openScreen("pause")));
            steps.add(QaStep.waitTicks(60));
            steps.add(screenshot("frame-inputs-pause"));
            steps.add(QaStep.action("close pause", probe -> probe.openScreen("none")));
            steps.add(QaStep.waitTicks(20));
            steps.addAll(changeDimension(-1, "0 100 0"));
            steps.addAll(changeDimension(0, "0 120 0"));
            steps.add(leaveWorld());
            steps.add(QaStep.waitTicks(40));
            steps.add(joinWorld());
            steps.add(QaStep.waitTicks(40));
            steps.add(screenshot("frame-inputs-rejoined"));
            return steps;
        }

        @Override
        void evaluate(QaReport r) {
            checkFeatureActive(r, WORLD_PROGRAMS);
            QaReport.FrameUniforms frame = r.frameUniforms;
            r.check("frame-uniforms-read-back", frame.readbacks >= 1000 && frame.mismatches == 0
                            && frame.inconsistencies == 0,
                    frame.mismatches + " of " + frame.readbacks + " readbacks wrong, " + frame.inconsistencies
                            + " of " + frame.consistencyChecks + " differed within a frame, problems "
                            + frame.problems);
            List<String> expected = new ArrayList<>();
            expected.add(r.environment.initialWidth + "x" + r.environment.initialHeight);
            for (int[] size : FRAME_INPUT_SIZES) {
                expected.add(size[0] + "x" + size[1]);
            }
            int sizes = expected.size() + (r.environment.fullscreenAllowed ? 1 : 0);
            r.check("frame-uniforms-follow-resizes", frame.viewSizes.containsAll(expected)
                            && frame.viewSizes.size() >= sizes,
                    "read " + frame.viewSizes + ", expected " + expected
                            + (r.environment.fullscreenAllowed ? " and the fullscreen size" : ""));
            r.check("frame-uniforms-shared-by-programs", frame.framesWithSeveralPrograms > 0,
                    frame.framesWithSeveralPrograms + " frames read from two or more programs");
            r.check("frame-uniforms-shared-by-world-passes", frame.framesWithSeveralPasses > 0,
                    frame.framesWithSeveralPasses + " frames read in two world passes");
            r.check("frame-counter-and-time-advanced", frame.lastCounter - frame.firstCounter > 500
                            && frame.lastTimeCounter - frame.firstTimeCounter > 10,
                    "frameCounter " + frame.firstCounter + " to " + frame.lastCounter + ", frameTimeCounter "
                            + frame.firstTimeCounter + " to " + frame.lastTimeCounter + ", deviations up to "
                            + frame.maxFrameTimeDeviationMs + " and " + frame.maxTimeCounterDeviationMs + " ms");
            Map<String, Integer> dimensions = r.worldPrograms.scopesByDimension;
            r.check("frame-uniforms-across-dimensions-and-rejoin", dimensions.containsKey("-1")
                            && dimensions.containsKey("0") && frame.maxReadbackGapFrames > 20,
                    "scopes per dimension " + dimensions + ", longest gap without readbacks "
                            + frame.maxReadbackGapFrames + " frames");
            r.check("samplers-kept-after-frame-updates", frame.samplerRechecks > 0 && frame.samplerMismatches == 0,
                    frame.samplerMismatches + " of " + frame.samplerRechecks + " wrong");
            int shots = r.environment.fullscreenAllowed ? 8 : 7;
            r.check("screenshots", r.screenshots.size() == shots, r.screenshots.size() + " of " + shots + " saved");
        }
    },

    SAMPLERS_VANILLA_REFERENCE("samplers-vanilla-reference",
            "The world-program-samplers scene with Focalis rendering nothing. Vanilla has to pass the same color"
                    + " checks, so they are known to mean something.") {
        @Override
        List<QaStep> steps() {
            return samplerScene("samplers-vanilla");
        }

        @Override
        void evaluate(QaReport r) {
            checkSamplerColors(r);
            r.check("screenshots", r.screenshots.size() == 4, r.screenshots.size() + " of 4 saved");
        }
    };

    static final String SHADERS = ShaderFeature.ID;
    static final String WORLD_PROGRAMS = WorldProgramsFeature.ID;
    private static final String OUTLINES = "ENTITY_OUTLINES/DEFAULT";

    private static final int[][] FRAME_INPUT_SIZES = {{1280, 720}, {1000, 750}, {1600, 900}};
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

    boolean expectsUnusableWorldPrograms() {
        return false;
    }

    // The probe's pig and chest renderers then leave other programs bound, like mod renderers with their own shaders.
    boolean injectsRendererLeaks() {
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
    private static QaStep fullscreenRoundTrip(String screenshot) {
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
                    probe.screenshot(screenshot);
                    phase = 2;
                    return false;
                }
                return true;
            }
        };
    }

    // In rain next to an entity and in view of a glowing one, first below and then above cloud height, so every
    // precise stage draws.
    private static List<QaStep> stageTour(String screenshots) {
        List<QaStep> steps = enterWorld();
        steps.add(QaStep.action("command /weather rain", probe -> probe.command("/weather rain")));
        steps.add(QaStep.action("command /summon", probe -> probe.command("/summon pig ~2 ~ ~2 {NoAI:1b}")));
        // Glowing makes vanilla draw entity outlines, which rebinds its framebuffer in the middle of the pass.
        steps.add(QaStep.action("command /summon glowing", probe -> probe.command(
                "/summon pig ~-4 ~ ~ {NoAI:1b,Glowing:1b}")));
        // Block entities draw after the outlines inside the same ENTITIES stage.
        steps.add(QaStep.action("command /setblock chest", probe -> probe.command(
                "/setblock ~-3 ~ ~1 minecraft:chest")));
        // Not next to the first one, which would make a double chest, so the two draw one after the other.
        steps.add(QaStep.action("command /setblock second chest", probe -> probe.command(
                "/setblock ~-3 ~ ~3 minecraft:chest")));
        steps.add(QaStep.action("command /setblock sign", probe -> probe.command(
                "/setblock ~-3 ~ ~-1 minecraft:standing_sign 12 replace {Text2:\"{\\\"text\\\":\\\"Focalis\\\"}\"}")));
        steps.add(QaStep.waitTicks(40));
        steps.add(screenshot(screenshots + "-below-clouds"));
        steps.add(QaStep.action("fly", QaProbe::fly));
        steps.add(QaStep.action("command /tp", probe -> probe.command("/tp @p ~ 140 ~")));
        steps.add(QaStep.waitTicks(40));
        steps.add(screenshot(screenshots + "-above-clouds"));
        steps.add(QaStep.waitTicks(20));
        return steps;
    }

    // Up at y 150, facing south and slightly down, on a stone floor with a red wool wall in front. Nothing shades it
    // up there, since new players spawn at a random spot near the world spawn. Then a white sheep and a chest at the
    // middle of the view, and the wall again at midnight. The HUD is hidden for every sample.
    private static List<QaStep> samplerScene(String prefix) {
        List<QaStep> steps = enterWorld();
        steps.add(QaStep.action("fly", QaProbe::fly));
        for (String command : Arrays.asList("/tp @p ~ 150 ~ 0 15", "/fill ~-3 ~-1 ~-1 ~3 ~-1 ~3 minecraft:stone",
                "/fill ~-3 ~-1 ~4 ~3 ~4 ~4 minecraft:wool 14")) {
            steps.add(QaStep.action("command " + command, probe -> probe.command(command)));
        }
        steps.add(QaStep.action("hide hud", probe -> probe.setHud(true, false)));
        steps.add(QaStep.waitTicks(30));
        steps.add(sample(prefix, "wall-day"));
        steps.add(QaStep.action("command /summon sheep", probe -> probe.command(
                "/summon sheep ~ ~ ~2.5 {NoAI:1b,Color:0b,Rotation:[90f,0f]}")));
        steps.add(QaStep.waitTicks(30));
        steps.add(sample(prefix, "sheep-day"));
        for (String command : Arrays.asList("/kill @e[type=sheep]", "/kill @e[type=item]",
                "/setblock ~ ~1 ~2 minecraft:chest 2")) {
            steps.add(QaStep.action("command " + command, probe -> probe.command(command)));
        }
        steps.add(QaStep.waitTicks(30));
        steps.add(sample(prefix, "chest-day"));
        for (String command : Arrays.asList("/setblock ~ ~1 ~2 minecraft:air", "/time set 18000")) {
            steps.add(QaStep.action("command " + command, probe -> probe.command(command)));
        }
        steps.add(QaStep.waitTicks(40));
        steps.add(sample(prefix, "wall-night"));
        steps.add(QaStep.action("hud reset", probe -> probe.setHud(false, false)));
        steps.add(QaStep.waitTicks(10));
        return steps;
    }

    private static QaStep sample(String prefix, String name) {
        return QaStep.action("sample " + name, probe -> {
            probe.sampleCenter(name);
            probe.screenshot(prefix + "-" + name);
        });
    }

    // Wool is red with little green and blue, the sheep's wool white, the chest's wood brown. At night the wall keeps
    // its color but loses most of its light, which only the lightmap can do.
    private static void checkSamplerColors(QaReport r) {
        double[] wall = r.centerColors.get("wall-day");
        double[] sheep = r.centerColors.get("sheep-day");
        double[] chest = r.centerColors.get("chest-day");
        double[] night = r.centerColors.get("wall-night");
        if (wall == null || sheep == null || chest == null || night == null) {
            r.check("center-colors-sampled", false, "sampled " + r.centerColors.keySet());
            return;
        }
        r.check("wall-shows-red-wool", wall[0] > 0.3 && wall[1] < 0.45 * wall[0] && wall[2] < 0.45 * wall[0],
                color(wall));
        double sheepMin = Math.min(sheep[0], Math.min(sheep[1], sheep[2]));
        double sheepMax = Math.max(sheep[0], Math.max(sheep[1], sheep[2]));
        r.check("sheep-shows-white-wool", sheepMin > 0.45 && sheepMax < 1.35 * sheepMin, color(sheep));
        r.check("chest-shows-wood", chest[0] > 0.2 && chest[1] > 0.5 * chest[0] && chest[1] < chest[0]
                && chest[2] < 0.8 * chest[0], color(chest));
        r.check("night-darkens-wall", luminance(night) < 0.6 * luminance(wall) && night[0] > night[1]
                && night[0] > night[2], color(night) + " at night, " + color(wall) + " by day");
    }

    private static double luminance(double[] color) {
        return 0.2126 * color[0] + 0.7152 * color[1] + 0.0722 * color[2];
    }

    private static String color(double[] color) {
        return String.format(Locale.ROOT, "rgb %.3f %.3f %.3f", color[0], color[1], color[2]);
    }

    // Overworld, Nether, End, overworld again and a rejoin, flying so nothing depends on where the player lands.
    private static List<QaStep> dimensionTour(String screenshots) {
        List<QaStep> steps = enterWorld();
        steps.add(QaStep.action("fly", QaProbe::fly));
        steps.add(QaStep.waitTicks(20));
        steps.add(screenshot(screenshots + "-overworld"));
        steps.addAll(changeDimension(-1, "0 100 0"));
        steps.add(screenshot(screenshots + "-nether"));
        steps.addAll(changeDimension(1, "0 100 0"));
        steps.add(screenshot(screenshots + "-end"));
        steps.addAll(changeDimension(0, "0 120 0"));
        steps.add(screenshot(screenshots + "-overworld-again"));
        steps.add(leaveWorld());
        steps.add(QaStep.waitTicks(20));
        steps.add(joinWorld());
        steps.add(QaStep.action("fly", QaProbe::fly));
        steps.add(QaStep.waitTicks(20));
        steps.add(screenshot(screenshots + "-rejoined"));
        steps.add(QaStep.waitTicks(20));
        return steps;
    }

    private static List<QaStep> changeDimension(int dimension, String position) {
        String command = "/forge setdimension @p " + dimension + " " + position;
        Integer target = dimension;
        return Arrays.asList(QaStep.action("command " + command, probe -> probe.command(command)),
                QaStep.until("in dimension " + dimension, 1200, probe -> target.equals(probe.dimension())
                        && probe.inWorld() && probe.ticksInWorld() >= 60),
                QaStep.action("fly", QaProbe::fly), QaStep.waitTicks(20));
    }

    // world0 replaces the main folder completely, so the overworld sky has no program even though the main folder
    // has gbuffers_basic. world-1 doesn't compile and the Nether doesn't fall back to the main folder either.
    private static void checkDimensionPrograms(QaReport r, boolean emptyEndFolder) {
        checkFeatureActive(r, WORLD_PROGRAMS);
        QaReport.WorldPrograms programs = r.worldPrograms;
        String end = emptyEndFolder ? "shaders/world1" : "shaders";
        List<String> selections = Arrays.asList("0 shaders/world0", "-1 shaders/world-1", "1 " + end,
                "0 shaders/world0");
        r.check("folder-per-dimension", programs.selections.equals(selections),
                "selections " + programs.selections + ", expected " + selections);
        Map<String, Integer> builds = new TreeMap<>();
        for (String folder : Arrays.asList("shaders/world0", "shaders/world-1", end)) {
            builds.put(folder, 1);
        }
        r.check("each-folder-built-once", programs.folderBuilds.equals(builds) && programs.stopped == null,
                "builds per folder " + programs.folderBuilds + ", expected " + builds);
        r.check("five-world-sessions", r.worldSessions == 5, r.worldSessions + " world sessions");

        Map<String, String> world0 = new TreeMap<>();
        for (String role : Arrays.asList("SKY_TEXTURED", "TERRAIN_SOLID", "TERRAIN_CUTOUT_MIPPED", "TERRAIN_CUTOUT",
                "TERRAIN_TRANSLUCENT", "PARTICLES_LIT", "PARTICLES_NORMAL", "WEATHER", "CLOUDS", "HAND")) {
            world0.put(role, "world0/gbuffers_textured");
        }
        world0.put("ENTITIES", "world0/gbuffers_entities");
        r.check("override-replaces-main-folder", world0.equals(roleNames(programs, "shaders/world0"))
                        && count(programs.boundByDimension, "0") > 0,
                "world0 roles " + roleNames(programs, "shaders/world0") + ", " + count(programs.boundByDimension, "0")
                        + " bound overworld scopes");
        r.check("broken-override-draws-vanilla", roleNames(programs, "shaders/world-1").isEmpty()
                        && count(programs.scopesByDimension, "-1") > 0 && count(programs.boundByDimension, "-1") == 0,
                count(programs.boundByDimension, "-1") + " of " + count(programs.scopesByDimension, "-1")
                        + " Nether scopes bound, world-1 roles " + roleNames(programs, "shaders/world-1"));
        if (emptyEndFolder) {
            r.check("empty-override-draws-vanilla", roleNames(programs, end).isEmpty()
                            && count(programs.scopesByDimension, "1") > 0 && count(programs.boundByDimension, "1") == 0,
                    count(programs.boundByDimension, "1") + " of " + count(programs.scopesByDimension, "1")
                            + " End scopes bound");
        } else {
            Map<String, String> main = new TreeMap<>();
            main.put("SKY_BASIC", "gbuffers_basic");
            for (String role : Arrays.asList("SKY_TEXTURED", "ENTITIES", "PARTICLES_LIT", "PARTICLES_NORMAL",
                    "WEATHER", "CLOUDS", "HAND")) {
                main.put(role, "gbuffers_textured");
            }
            for (String role : Arrays.asList("TERRAIN_SOLID", "TERRAIN_CUTOUT_MIPPED", "TERRAIN_CUTOUT",
                    "TERRAIN_TRANSLUCENT")) {
                main.put(role, "gbuffers_terrain");
            }
            r.check("no-override-uses-main-folder", main.equals(roleNames(programs, end))
                            && count(programs.boundByDimension, "1") > 0,
                    "main roles " + roleNames(programs, end) + ", " + count(programs.boundByDimension, "1")
                            + " bound End scopes");
        }
        r.check("screenshots", r.screenshots.size() == 5, r.screenshots.size() + " of 5 saved");
    }

    private static Map<String, String> roleNames(QaReport.WorldPrograms programs, String folder) {
        Map<String, String> names = new TreeMap<>();
        Map<String, QaReport.BuiltProgram> roles = programs.folderRoles.get(folder);
        if (roles != null) {
            for (Map.Entry<String, QaReport.BuiltProgram> role : roles.entrySet()) {
                names.put(role.getKey(), role.getValue().name);
            }
        }
        return names;
    }

    private static int count(Map<String, Integer> counts, String key) {
        return counts.getOrDefault(key, 0);
    }

    // How often each stage and draw kind fires in one world pass of the stage tour.
    private static Map<String, Integer> kindsPerPass() {
        Map<String, Integer> perPass = new TreeMap<>();
        for (String kind : Arrays.asList("SKY/SKY_BASIC", "TERRAIN/TERRAIN_SOLID", "TERRAIN/TERRAIN_CUTOUT_MIPPED",
                "TERRAIN/TERRAIN_CUTOUT", "TRANSLUCENT/TERRAIN_TRANSLUCENT", "ENTITIES/ENTITY_PASS_0",
                "ENTITIES/ENTITY_PASS_1", "PARTICLES/PARTICLES_LIT", "PARTICLES/PARTICLES_NORMAL", "WEATHER/DEFAULT",
                "CLOUDS/DEFAULT", "HAND/DEFAULT")) {
            perPass.put(kind, 1);
        }
        perPass.put("SKY/SKY_TEXTURED", 2);
        return perPass;
    }

    // The focalis-world-routes pack has no weather or hand program, so those fall back to gbuffers_textured_lit
    // and share it with lit particles. Its gbuffers_basic is never picked since every role has something closer.
    private static void checkTestPackPrograms(QaReport r) {
        Map<String, String> expected = new TreeMap<>();
        expected.put("SKY_BASIC", "gbuffers_skybasic");
        expected.put("SKY_TEXTURED", "gbuffers_skytextured");
        expected.put("TERRAIN_SOLID", "gbuffers_terrain");
        expected.put("TERRAIN_CUTOUT_MIPPED", "gbuffers_terrain");
        expected.put("TERRAIN_CUTOUT", "gbuffers_terrain");
        expected.put("TERRAIN_TRANSLUCENT", "gbuffers_water");
        expected.put("ENTITIES", "gbuffers_entities");
        expected.put("PARTICLES_LIT", "gbuffers_textured_lit");
        expected.put("PARTICLES_NORMAL", "gbuffers_textured");
        expected.put("WEATHER", "gbuffers_textured_lit");
        expected.put("CLOUDS", "gbuffers_clouds");
        expected.put("HAND", "gbuffers_textured_lit");
        Map<String, String> actual = new TreeMap<>();
        Map<String, Integer> idsByName = new TreeMap<>();
        boolean shared = true;
        for (Map.Entry<String, QaReport.BuiltProgram> role : r.worldPrograms.roles.entrySet()) {
            QaReport.BuiltProgram program = role.getValue();
            actual.put(role.getKey(), program.name);
            Integer id = idsByName.putIfAbsent(program.name, program.id);
            shared &= id == null || id == program.id;
        }
        r.check("test-pack-programs", actual.equals(expected) && shared && idsByName.size() == 8,
                "roles " + actual + ", " + idsByName.size() + " programs " + idsByName);
    }

    // What the shader router makes of one world pass here. Each role at most this often per pass and exactly this
    // often on average means every single pass had exactly this many.
    private static void checkShaderRoles(QaReport r) {
        Map<String, Integer> perPass = new TreeMap<>();
        for (String role : Arrays.asList("SKY_BASIC", "TERRAIN_SOLID", "TERRAIN_CUTOUT_MIPPED", "TERRAIN_CUTOUT",
                "TERRAIN_TRANSLUCENT", "PARTICLES_LIT", "PARTICLES_NORMAL", "WEATHER", "CLOUDS", "HAND")) {
            perPass.put(role, 1);
        }
        perPass.put("SKY_TEXTURED", 2);
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
        // The entity outlines are the only precise stage without a role, since vanilla draws them with its shaders.
        other.remove("NONE");
        QaReport.RoleCounts none = r.shaderRoutes.roles.get("NONE");
        QaReport.StageCounts outlines = r.renderStages.kinds.get(OUTLINES);
        int noRole = none == null ? 0 : none.count;
        int outlineStarts = outlines == null ? 0 : outlines.starts;
        r.check("no-unclassified-shader-roles", other.isEmpty() && noRole == outlineStarts,
                "other roles " + other + ", " + noRole + " without a role for " + outlineStarts + " outline starts");
    }

    // Vanilla only draws outlines while the glowing pig is in view, so they don't happen in every world pass.
    private static void checkOutlinesSeen(QaReport r) {
        QaReport.StageCounts outlines = r.renderStages.kinds.get(OUTLINES);
        r.check("entity-outlines-seen", outlines != null && outlines.pairs > 0 && outlines.maxPerWorldPass == 1,
                outlines == null ? "never" : outlines.pairs + " pairs, at most " + outlines.maxPerWorldPass
                        + " per world pass");
    }

    private static void checkShadersActive(QaReport r) {
        checkFeatureActive(r, SHADERS);
    }

    private static void checkFeatureActive(QaReport r, String feature) {
        r.check(feature.replace('_', '-') + "-active", "ACTIVE".equals(r.featureState(feature)),
                feature + " feature is " + r.featureState(feature));
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
