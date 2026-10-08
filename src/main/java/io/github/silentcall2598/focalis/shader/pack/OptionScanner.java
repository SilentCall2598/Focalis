// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.shader.pack;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Finds the options of a pack while it loads and writes chosen values back into the lines that define them. Only
 * the documented forms count, and a value is applied by rewriting its definition line in place so every line keeps
 * its number.
 */
final class OptionScanner {

    // The const names the format documents. Anything else declared const is plain GLSL.
    private static final Set<String> CONST_NAMES = new HashSet<>(Arrays.asList(
            "shadowMapResolution", "shadowDistance", "shadowDistanceRenderMul", "shadowIntervalSize",
            "generateShadowMipmap", "generateShadowColorMipmap", "shadowHardwareFiltering",
            "shadowHardwareFiltering0", "shadowHardwareFiltering1", "shadowtex0Mipmap", "shadowtexMipmap",
            "shadowtex1Mipmap", "shadowcolor0Mipmap", "shadowColor0Mipmap", "shadowcolor1Mipmap",
            "shadowColor1Mipmap", "shadowtex0Nearest", "shadowtexNearest", "shadow0MinMagNearest",
            "shadowtex1Nearest", "shadow1MinMagNearest", "shadowcolor0Nearest", "shadowColor0Nearest",
            "shadowColor0MinMagNearest", "shadowcolor1Nearest", "shadowColor1Nearest", "shadowColor1MinMagNearest",
            "wetnessHalflife", "drynessHalflife", "eyeBrightnessHalflife", "centerDepthHalflife", "sunPathRotation",
            "ambientOcclusionLevel", "superSamplingLevel", "noiseTextureResolution"));

    private static final Pattern SWITCH_ON = Pattern.compile("^(\\s*)(#\\s*define\\s+([A-Za-z_]\\w*))\\s*(//.*)?$");
    private static final Pattern SWITCH_OFF =
            Pattern.compile("^(\\s*)(//\\s*)(#\\s*define\\s+([A-Za-z_]\\w*))\\s*(//.*)?$");
    private static final Pattern VALUE = Pattern.compile(
            "^\\s*#\\s*define\\s+([A-Za-z_]\\w*)\\s+([^\\s/]+)\\s*//[^\\[]*\\[([^\\]]*)\\].*$");
    private static final Pattern CONST = Pattern.compile(
            "^\\s*const\\s+(?:int|float|bool)\\s+([A-Za-z_]\\w*)\\s*=\\s*([^;/]*?)\\s*;"
                    + "\\s*//[^\\[]*\\[([^\\]]*)\\].*$");
    private static final Pattern IFDEF = Pattern.compile("^\\s*#\\s*(?:ifdef|ifndef)\\s+([A-Za-z_]\\w*)");

    // One line that looks like an option. A switch only counts once some program tests it.
    private static final class Candidate {

        final String name;
        final ShaderOption.Kind kind;
        final String value;
        final List<String> allowed;
        final SourceLocation location;

        Candidate(String name, ShaderOption.Kind kind, String value, List<String> allowed, SourceLocation location) {
            this.name = name;
            this.kind = kind;
            this.value = value;
            this.allowed = allowed;
            this.location = location;
        }
    }

    // What one pack file holds. Each file is only scanned once however many programs include it.
    private static final class FileScan {

        final List<Candidate> candidates = new ArrayList<>();
        final Set<String> tested = new HashSet<>();
    }

    private final IncludeResolver.FileText files;
    private final Map<ShaderPath, FileScan> scans = new TreeMap<>();
    // Switch lines some program tests with #ifdef or #ifndef.
    private final Set<SourceLocation> testedSwitches = new HashSet<>();

    OptionScanner(IncludeResolver.FileText files) {
        this.files = files;
    }

    /**
     * Adds one expanded .vsh or .fsh source. The format tests a switch against the same file, which only makes
     * sense for the expanded program since packs keep their options in included files.
     */
    void addProgram(ResolvedSource source) throws IOException {
        Set<String> tested = new HashSet<>();
        List<FileScan> contributing = new ArrayList<>();
        for (ShaderPath file : source.files()) {
            FileScan scan = scan(file);
            tested.addAll(scan.tested);
            contributing.add(scan);
        }
        for (FileScan scan : contributing) {
            for (Candidate candidate : scan.candidates) {
                if (candidate.kind == ShaderOption.Kind.SWITCH && tested.contains(candidate.name)) {
                    testedSwitches.add(candidate.location);
                }
            }
        }
    }

    private FileScan scan(ShaderPath file) throws IOException {
        FileScan scan = scans.get(file);
        if (scan != null) {
            return scan;
        }
        scan = new FileScan();
        scans.put(file, scan);
        String text = files.read(file);
        if (text == null) {
            return scan;
        }
        CommentScanner comments = new CommentScanner();
        String[] lines = text.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            String line = stripReturn(lines[i]);
            CommentScanner.ScannedLine scanned = comments.scan(line);
            if (scanned.code().indexOf('#') >= 0) {
                Matcher ifdef = IFDEF.matcher(scanned.code());
                if (ifdef.find()) {
                    scan.tested.add(ifdef.group(1));
                }
            }
            // A line that starts inside a block comment is commented out, whatever it looks like.
            if (scanned.startedInComment() || scanned.openCommentStart() >= 0) {
                continue;
            }
            Candidate candidate = match(line, new SourceLocation(file, i + 1));
            if (candidate != null) {
                scan.candidates.add(candidate);
            }
        }
        return scan;
    }

    @Nullable
    private static Candidate match(String line, SourceLocation location) {
        Matcher on = SWITCH_ON.matcher(line);
        if (on.matches()) {
            return new Candidate(on.group(3), ShaderOption.Kind.SWITCH, "true", ShaderOptions.SWITCH_VALUES,
                    location);
        }
        Matcher off = SWITCH_OFF.matcher(line);
        if (off.matches()) {
            return new Candidate(off.group(4), ShaderOption.Kind.SWITCH, "false", ShaderOptions.SWITCH_VALUES,
                    location);
        }
        Matcher value = VALUE.matcher(line);
        if (value.matches()) {
            return new Candidate(value.group(1), ShaderOption.Kind.VALUE, value.group(2),
                    allowed(value.group(3), value.group(2)), location);
        }
        Matcher constant = CONST.matcher(line);
        if (constant.matches() && CONST_NAMES.contains(constant.group(1)) && !constant.group(2).isEmpty()) {
            return new Candidate(constant.group(1), ShaderOption.Kind.CONST, constant.group(2),
                    allowed(constant.group(3), constant.group(2)), location);
        }
        return null;
    }

    // The default joins the list when the pack left it out.
    private static List<String> allowed(String list, String defaultValue) {
        List<String> values = new ArrayList<>();
        for (String value : list.trim().split("\\s+")) {
            if (!value.isEmpty() && !values.contains(value)) {
                values.add(value);
            }
        }
        if (!values.contains(defaultValue)) {
            values.add(defaultValue);
        }
        return Collections.unmodifiableList(values);
    }

    /** Groups what was found into options and reports the ambiguous ones. */
    ShaderOptions finish(List<PackIssue> issues) {
        Map<String, List<Candidate>> byName = new TreeMap<>();
        for (FileScan scan : scans.values()) {
            for (Candidate candidate : scan.candidates) {
                if (candidate.kind != ShaderOption.Kind.SWITCH || testedSwitches.contains(candidate.location)) {
                    byName.computeIfAbsent(candidate.name, key -> new ArrayList<>()).add(candidate);
                }
            }
        }
        Map<String, ShaderOption> options = new LinkedHashMap<>();
        for (Map.Entry<String, List<Candidate>> entry : byName.entrySet()) {
            List<Candidate> candidates = entry.getValue();
            Candidate first = candidates.get(0);
            boolean ambiguous = false;
            List<SourceLocation> locations = new ArrayList<>();
            for (Candidate candidate : candidates) {
                ambiguous |= candidate.kind != first.kind || !candidate.value.equals(first.value);
                locations.add(candidate.location);
            }
            if (ambiguous) {
                issues.add(new PackIssue(first.location, "Option " + entry.getKey() + " is defined differently in "
                        + locations + ", so it stays as written"));
            }
            options.put(entry.getKey(), new ShaderOption(entry.getKey(), first.kind, first.value, first.allowed,
                    Collections.unmodifiableList(locations), ambiguous));
        }
        return new ShaderOptions(Collections.unmodifiableMap(options));
    }

    /**
     * Rewrites the definition lines in one file's text for the given option values, keyed by line number. Line
     * endings and every other line are kept exactly.
     */
    static String rewrite(String text, Map<Integer, OptionValue> changes) {
        String[] lines = text.split("\n", -1);
        for (Map.Entry<Integer, OptionValue> change : changes.entrySet()) {
            int index = change.getKey() - 1;
            String line = lines[index];
            boolean carriageReturn = line.endsWith("\r");
            String rewritten = rewriteLine(stripReturn(line), change.getValue());
            lines[index] = carriageReturn ? rewritten + "\r" : rewritten;
        }
        return String.join("\n", lines);
    }

    /** The value an option gets written as on one of its lines. */
    static final class OptionValue {

        final ShaderOption.Kind kind;
        final String value;

        OptionValue(ShaderOption.Kind kind, String value) {
            this.kind = kind;
            this.value = value;
        }
    }

    private static String rewriteLine(String line, OptionValue change) {
        switch (change.kind) {
            case SWITCH: {
                boolean on = change.value.equals("true");
                Matcher onMatch = SWITCH_ON.matcher(line);
                if (onMatch.matches()) {
                    return on ? line : line.substring(0, onMatch.start(2)) + "//" + line.substring(onMatch.start(2));
                }
                Matcher offMatch = SWITCH_OFF.matcher(line);
                if (offMatch.matches()) {
                    return on ? line.substring(0, offMatch.start(2)) + line.substring(offMatch.start(3)) : line;
                }
                break;
            }
            case VALUE: {
                Matcher value = VALUE.matcher(line);
                if (value.matches()) {
                    return replace(line, value.start(2), value.end(2), change.value);
                }
                break;
            }
            case CONST: {
                Matcher constant = CONST.matcher(line);
                if (constant.matches()) {
                    return replace(line, constant.start(2), constant.end(2), change.value);
                }
                break;
            }
            default:
                break;
        }
        // The scan found this line from the same text, so it always matches again.
        throw new IllegalStateException("Option line no longer matches: " + line);
    }

    private static String replace(String line, int start, int end, String value) {
        return line.substring(0, start) + value + line.substring(end);
    }

    private static String stripReturn(String line) {
        return line.endsWith("\r") ? line.substring(0, line.length() - 1) : line;
    }
}
