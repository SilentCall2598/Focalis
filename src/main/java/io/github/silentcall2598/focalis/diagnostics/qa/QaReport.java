// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.diagnostics.qa;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Everything a QA run records, written as probe.json. Field names are the JSON keys. Lists of individual events are
 * capped so a bad run can't produce a huge file, while the counters always stay exact.
 */
final class QaReport {

    static final int MAX_ENTRIES = 50;

    final int schema = 1;
    final String scenario;
    final String description;
    String status = "starting";
    @Nullable
    String step;
    boolean inWorld;
    @Nullable
    Request request;
    boolean completed;
    @Nullable
    String result;
    @Nullable
    String failure;
    final List<Check> checks = new ArrayList<>();
    final Environment environment = new Environment();
    final Frames frames = new Frames();
    int worldSessions;
    final WorldPhases worldPhases = new WorldPhases();
    final RenderStages renderStages = new RenderStages();
    final ShaderRoutes shaderRoutes = new ShaderRoutes();
    final PostPass postPass = new PostPass();
    final List<FeatureEntry> features = new ArrayList<>();
    final GlErrors glErrors = new GlErrors();
    final Boundary boundary = new Boundary();
    final Resources resources = new Resources();
    final WorldTarget worldTarget = new WorldTarget();
    final WorldPrograms worldPrograms = new WorldPrograms();
    @Nullable
    Map<String, Integer> perturbations;
    @Nullable
    HighTextureUnit highTextureUnit;
    @Nullable
    InjectedFailure injectedFailure;
    final List<Screenshot> screenshots = new ArrayList<>();
    // The average color of the middle of the screen at named moments, as red, green and blue from 0 to 1.
    final Map<String, double[]> centerColors = new TreeMap<>();
    final List<StepRecord> steps = new ArrayList<>();
    final List<String> notes = new ArrayList<>();
    final List<String> probeErrors = new ArrayList<>();

    QaReport(QaScenario scenario) {
        this.scenario = scenario.id;
        this.description = scenario.description;
    }

    void check(String name, boolean passed, String detail) {
        checks.add(new Check(name, passed, detail));
    }

    @Nullable
    String featureState(String featureId) {
        for (FeatureEntry feature : features) {
            if (feature.id.equals(featureId)) {
                return feature.state;
            }
        }
        return null;
    }

    static <T> void addCapped(List<T> list, T entry) {
        if (list.size() < MAX_ENTRIES) {
            list.add(entry);
        }
    }

    static final class Request {
        final int id;
        final String action;
        final int width;
        final int height;

        Request(int id, String action, int width, int height) {
            this.id = id;
            this.action = action;
            this.width = width;
            this.height = height;
        }
    }

    static final class Check {
        final String name;
        final boolean passed;
        final String detail;

        Check(String name, boolean passed, String detail) {
            this.name = name;
            this.passed = passed;
            this.detail = detail;
        }
    }

    static final class Environment {
        String focalisVersion;
        final String minecraft = "1.12.2";
        String glRenderer;
        String glVersion;
        int initialWidth;
        int initialHeight;
        boolean fullscreenAllowed;
        String startedAt;
        String finishedAt;
    }

    static final class Frames {
        int total;
        int world;
        double averageFrameMs;
        double maxFrameMs;
        double averageFocalisWorldEndMs;
        double maxFocalisWorldEndMs;
    }

    // WORLD START comes from the Mixin hook and WORLD END from Forge's RenderWorldLastEvent. Counts are per world
    // pass, which can happen more than once in a displayed frame.
    static final class WorldPhases {
        @Nullable
        String startHook;
        int starts;
        int ends;
        int pairs;
        // A START while the previous one still waited for its END.
        int repeatedStarts;
        int endsWithoutStart;
        // Post passes rendered anywhere but a WORLD END, or more than once in the same one.
        int passesOutsideEnd;
        int repeatedPasses;
        final List<Integer> pairsPerSession = new ArrayList<>();
        final List<PhaseProblem> problems = new ArrayList<>();
    }

    static final class PhaseProblem {
        final int frame;
        final int starts;
        final int ends;
        final String problem;

        PhaseProblem(int frame, int starts, int ends, String problem) {
            this.frame = frame;
            this.starts = starts;
            this.ends = ends;
            this.problem = problem;
        }
    }

    // The precise stages the Mixins wrap inside each world pass, keyed by stage name, and again by stage and draw
    // kind, like TERRAIN/TERRAIN_SOLID.
    static final class RenderStages {
        final Map<String, StageCounts> stages = new TreeMap<>();
        final Map<String, StageCounts> kinds = new TreeMap<>();
        // START still open when its frame or world pass ended.
        int unmatchedStarts;
        int endsWithoutStart;
        // END for a stage that wasn't the innermost open one.
        int badNesting;
        // START for a stage that was already open, which points at a duplicate hook.
        int repeatedStarts;
        int outsideWorld;
        int outsideFrame;
        // HAND comes after WORLD END, so a HAND inside an open world pass is misplaced.
        int handBeforeWorldEnd;
        // END with another draw kind than the START it closes.
        int kindMismatches;
        final List<StageProblem> problems = new ArrayList<>();

        int problemCount() {
            return unmatchedStarts + endsWithoutStart + badNesting + repeatedStarts + outsideWorld + outsideFrame
                    + handBeforeWorldEnd + kindMismatches;
        }
    }

    static final class StageCounts {
        int starts;
        int ends;
        int pairs;
        int maxPerWorldPass;
    }

    static final class StageProblem {
        final int frame;
        final String stage;
        final String kind;
        final String problem;

        StageProblem(int frame, String stage, String kind, String problem) {
            this.frame = frame;
            this.stage = stage;
            this.kind = kind;
            this.problem = problem;
        }
    }

    // Shader program roles the precise stage starts route to, keyed by role name.
    static final class ShaderRoutes {
        final Map<String, RoleCounts> roles = new TreeMap<>();
    }

    static final class RoleCounts {
        int count;
        int maxPerWorldPass;
    }

    static final class PostPass {
        int renderedFrames;
        int idleFrames;
        final Map<String, Integer> skips = new TreeMap<>();
        final List<Integer> renderedPerSession = new ArrayList<>();
        final List<Integer> programsBuilt = new ArrayList<>();
        final List<Capture> captures = new ArrayList<>();
        final List<String> expectedCaptureSizes = new ArrayList<>();
        @Nullable
        String stopped;
    }

    static final class Capture {
        final int frame;
        final int framebuffer;
        final int colorTexture;
        final int depthTexture;
        final int width;
        final int height;
        final String depth;

        Capture(int frame, int framebuffer, int colorTexture, int depthTexture, int width, int height, String depth) {
            this.frame = frame;
            this.framebuffer = framebuffer;
            this.colorTexture = colorTexture;
            this.depthTexture = depthTexture;
            this.width = width;
            this.height = height;
            this.depth = depth;
        }
    }

    static final class FeatureEntry {
        final String id;
        final String state;
        @Nullable
        final String detail;

        FeatureEntry(String id, String state, @Nullable String detail) {
            this.id = id;
            this.state = state;
            this.detail = detail;
        }
    }

    static final class GlErrors {
        int beforeFocalis;
        int duringFocalis;
        final List<GlError> samples = new ArrayList<>();
    }

    static final class GlError {
        final int frame;
        final String where;
        final String code;

        GlError(int frame, String where, int code) {
            this.frame = frame;
            this.where = where;
            this.code = "0x" + Integer.toHexString(code);
        }
    }

    static final class Boundary {
        int framesChecked;
        int mismatchFrames;
        final List<Mismatch> mismatches = new ArrayList<>();
    }

    static final class Mismatch {
        final int frame;
        final String context;
        final List<String> differences;

        Mismatch(int frame, String context, List<String> differences) {
            this.frame = frame;
            this.context = context;
            this.differences = differences;
        }
    }

    static final class Resources {
        int namesChecked;
        // GL only hands out names that are free, so a name the new capture got again proves the old object is gone.
        int reusedNames;
        final List<String> failures = new ArrayList<>();
    }

    // What the world target did, if its feature ran.
    static final class WorldTarget {
        int redirectedPasses;
        final Map<String, Integer> skips = new TreeMap<>();
        final List<Capture> targets = new ArrayList<>();
        // The draw framebuffer when translucent terrain starts, which is after vanilla rebinds its framebuffer
        // for entity outlines. It has to be the target in a redirected pass and Minecraft's otherwise.
        int drawSamples;
        int drawMismatches;
        final List<String> drawMismatchSamples = new ArrayList<>();
        final Resources replaced = new Resources();
        @Nullable
        String stopped;
    }

    // What the world programs did, if their feature ran.
    static final class WorldPrograms {
        int builds;
        // Builds per program folder, like shaders or shaders/world-1. Each folder is built once per session.
        final Map<String, Integer> folderBuilds = new TreeMap<>();
        // Every role with a program in each built folder.
        final Map<String, Map<String, BuiltProgram>> folderRoles = new TreeMap<>();
        // Each time a world pass picked other programs, the dimension and the folder it used.
        final List<String> selections = new ArrayList<>();
        // Every role with a program in the folder selected last.
        final Map<String, BuiltProgram> roles = new TreeMap<>();
        // Stage STARTs where a scope opened, and per draw kind how often its role's program really was current.
        int scopes;
        final Map<String, Integer> bound = new TreeMap<>();
        // The same per dimension.
        final Map<String, Integer> scopesByDimension = new TreeMap<>();
        final Map<String, Integer> boundByDimension = new TreeMap<>();
        int bindMismatches;
        // Right after every stage END, the program from before its START has to be back.
        int restores;
        int restoreMismatches;
        // Sun and moon scopes that put the sky's own program back when they ended.
        int nestedSkyRestores;
        // The draw framebuffer whenever a program was bound. The world target in a redirected pass.
        int drawSamples;
        int drawMismatches;
        // HAND comes after the world pass, so no scope may be open and no Focalis program current there.
        int handChecks;
        int handProblems;
        // Vanilla's entity outlines bind their own programs. When they start, the program from before the
        // outermost Focalis scope has to be back, and when they end the entities program has to be bound again.
        int outlineStarts;
        int outlineProblems;
        int outlineResumes;
        int outlineResumeProblems;
        // Chests drawn inside a bound ENTITIES scope, some of them after the outlines, have to draw with its program.
        int blockEntitySamples;
        int blockEntitySamplesAfterOutlines;
        int blockEntityMismatches;
        // Right after each renderer returned inside a bound ENTITIES scope. Renderers may leave any program bound,
        // and the ENTITIES program has to be current again once the features had their turn.
        int entityRendererChecks;
        int blockEntityRendererChecks;
        int entityRenderersLeftOther;
        int blockEntityRenderersLeftOther;
        int rendererMismatches;
        // Programs the probe's own pig and chest renderers deliberately left bound.
        int entityLeaksInjected;
        int blockEntityLeaksInjected;
        // Stages without a scope that still had a Focalis program current.
        int unboundLeaks;
        // The sampler units each built program holds, read back from GL, like "texture=0 lightmap=1".
        final Map<String, String> samplers = new TreeMap<>();
        int samplerChecks;
        int samplerMismatches;
        // The active texture unit and its binding have to be the same right after a Focalis stage START or END as
        // right before it.
        int textureStateChecks;
        int textureStateChanges;
        // While entities and chests draw in a bound scope, unit 1 has to hold Minecraft's lightmap.
        int lightmapChecks;
        int lightmapMismatches;
        // Right after a sheep or a chest drew in a bound scope, unit 0 has to hold its own texture.
        int ownTextureChecks;
        int ownTextureMismatches;
        final List<String> problems = new ArrayList<>();
        @Nullable
        String stopped;
    }

    static final class BuiltProgram {
        final String name;
        final int id;

        BuiltProgram(String name, int id) {
            this.name = name;
            this.id = id;
        }
    }

    static final class HighTextureUnit {
        int checks;
        int failures;
        int highestUnitChecked;
        final List<UnitCheck> results = new ArrayList<>();
    }

    static final class UnitCheck {
        final int frame;
        final int startUnit;
        final boolean passed;
        final String detail;

        UnitCheck(int frame, int startUnit, boolean passed, String detail) {
            this.frame = frame;
            this.startUnit = startUnit;
            this.passed = passed;
            this.detail = detail;
        }
    }

    static final class InjectedFailure {
        int frame = -1;
        boolean featureFailed;
        @Nullable
        String featureDetail;
        boolean stateRestored;
        boolean resourcesReleased;
        int renderedAfterFailure;
    }

    static final class Screenshot {
        final String name;
        final String file;
        final int frame;
        final int width;
        final int height;

        Screenshot(String name, String file, int frame, int width, int height) {
            this.name = name;
            this.file = file;
            this.frame = frame;
            this.width = width;
            this.height = height;
        }
    }

    static final class StepRecord {
        final String name;
        final int tick;
        final int worldFrame;

        StepRecord(String name, int tick, int worldFrame) {
            this.name = name;
            this.tick = tick;
            this.worldFrame = worldFrame;
        }
    }
}
