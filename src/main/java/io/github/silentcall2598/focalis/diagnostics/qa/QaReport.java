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
    final PostPass postPass = new PostPass();
    final List<FeatureEntry> features = new ArrayList<>();
    final GlErrors glErrors = new GlErrors();
    final Boundary boundary = new Boundary();
    final Resources resources = new Resources();
    @Nullable
    Map<String, Integer> perturbations;
    @Nullable
    HighTextureUnit highTextureUnit;
    @Nullable
    InjectedFailure injectedFailure;
    final List<Screenshot> screenshots = new ArrayList<>();
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

    // WORLD START comes from the Mixin hook and WORLD END from Forge's RenderWorldLastEvent.
    static final class WorldPhases {
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
