// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.shader.pack;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * The {@code alphaTest.<program>} and {@code blend.<program>} directives of one configuration, keyed by the pack
 * program name like {@code gbuffers_water}. A role that falls back to another program gets that program's
 * directives, not the ones of the program it would have liked. {@code blend.<program>.<buffer>} needs per-buffer
 * blending, which Focalis can't do yet, so those are only kept with their source line. Immutable.
 */
public final class ProgramStates {

    public static final ProgramStates NONE = new ProgramStates(Collections.<String, AlphaTest>emptyMap(),
            Collections.<String, Blend>emptyMap(), Collections.<BufferBlend>emptyList());

    static final String ALPHA_TEST = "alphaTest.";
    static final String BLEND = "blend.";
    private static final String OFF = "off";
    private static final Pattern PROGRAM = Pattern.compile("[A-Za-z_][\\w-]*");
    private static final Pattern BUFFER = Pattern.compile("colortex(\\d|1[0-5])|gcolor|gdepth|gnormal|composite"
            + "|gaux[1-4]");
    private static final Pattern NUMBER = Pattern.compile("[-+]?(\\d+\\.?\\d*|\\.\\d+)([eE][-+]?\\d+)?");

    // Spelled the way the format documents them, GL_ALWAYS included.
    private static final Map<String, Integer> FUNCTIONS = new LinkedHashMap<>();
    private static final Map<String, Integer> FACTORS = new LinkedHashMap<>();

    static {
        FUNCTIONS.put("NEVER", 0x0200);
        FUNCTIONS.put("LESS", 0x0201);
        FUNCTIONS.put("EQUAL", 0x0202);
        FUNCTIONS.put("LEQUAL", 0x0203);
        FUNCTIONS.put("GREATER", 0x0204);
        FUNCTIONS.put("NOTEQUAL", 0x0205);
        FUNCTIONS.put("GEQUAL", 0x0206);
        FUNCTIONS.put("GL_ALWAYS", 0x0207);
        FACTORS.put("ZERO", 0);
        FACTORS.put("ONE", 1);
        FACTORS.put("SRC_COLOR", 0x0300);
        FACTORS.put("ONE_MINUS_SRC_COLOR", 0x0301);
        FACTORS.put("DST_COLOR", 0x0306);
        FACTORS.put("ONE_MINUS_DST_COLOR", 0x0307);
        FACTORS.put("SRC_ALPHA", 0x0302);
        FACTORS.put("ONE_MINUS_SRC_ALPHA", 0x0303);
        FACTORS.put("DST_ALPHA", 0x0304);
        FACTORS.put("ONE_MINUS_DST_ALPHA", 0x0305);
        FACTORS.put("SRC_ALPHA_SATURATE", 0x0308);
    }

    /** One program's alpha test. Disabled means {@code off}, and the function and reference are then unused. */
    public static final class AlphaTest {

        private final boolean enabled;
        private final int function;
        private final float reference;
        private final SourceLocation location;

        AlphaTest(boolean enabled, int function, float reference, SourceLocation location) {
            this.enabled = enabled;
            this.function = function;
            this.reference = reference;
            this.location = location;
        }

        public boolean enabled() {
            return enabled;
        }

        /** The GL comparison like GL_GREATER. */
        public int function() {
            return function;
        }

        public float reference() {
            return reference;
        }

        public SourceLocation location() {
            return location;
        }

        @Override
        public String toString() {
            return enabled ? "0x" + Integer.toHexString(function) + " " + reference : OFF;
        }
    }

    /** A blend mode with separate color and alpha factors, or blending turned off. */
    public static final class Blend {

        private final boolean enabled;
        private final int sourceColor;
        private final int destinationColor;
        private final int sourceAlpha;
        private final int destinationAlpha;
        private final SourceLocation location;

        Blend(boolean enabled, int sourceColor, int destinationColor, int sourceAlpha, int destinationAlpha,
                SourceLocation location) {
            this.enabled = enabled;
            this.sourceColor = sourceColor;
            this.destinationColor = destinationColor;
            this.sourceAlpha = sourceAlpha;
            this.destinationAlpha = destinationAlpha;
            this.location = location;
        }

        public boolean enabled() {
            return enabled;
        }

        public int sourceColor() {
            return sourceColor;
        }

        public int destinationColor() {
            return destinationColor;
        }

        public int sourceAlpha() {
            return sourceAlpha;
        }

        public int destinationAlpha() {
            return destinationAlpha;
        }

        public SourceLocation location() {
            return location;
        }

        @Override
        public String toString() {
            return enabled ? sourceColor + " " + destinationColor + " " + sourceAlpha + " " + destinationAlpha : OFF;
        }
    }

    /** A blend mode for one color buffer of a program. Kept for when Focalis can blend per buffer. */
    public static final class BufferBlend {

        private final String program;
        private final String buffer;
        private final Blend blend;

        BufferBlend(String program, String buffer, Blend blend) {
            this.program = program;
            this.buffer = buffer;
            this.blend = blend;
        }

        public String program() {
            return program;
        }

        /** The buffer as written, like {@code colortex4} or {@code gaux1}. */
        public String buffer() {
            return buffer;
        }

        public Blend blend() {
            return blend;
        }

        /** What Focalis still needs before it can apply this. */
        public String requires() {
            return "per-buffer blending";
        }
    }

    private final Map<String, AlphaTest> alphaTests;
    private final Map<String, Blend> blends;
    private final List<BufferBlend> bufferBlends;

    private ProgramStates(Map<String, AlphaTest> alphaTests, Map<String, Blend> blends,
            List<BufferBlend> bufferBlends) {
        this.alphaTests = alphaTests;
        this.blends = blends;
        this.bufferBlends = bufferBlends;
    }

    /**
     * Reads the directives from properties that already went through the preprocessor. A malformed one is reported
     * and left out, so its program keeps whatever state it would have had without it.
     */
    static ProgramStates read(ShaderPath file, Map<String, ShaderProperties.Property> properties,
            List<PackIssue> issues) {
        Map<String, AlphaTest> alphaTests = new LinkedHashMap<>();
        Map<String, Blend> blends = new LinkedHashMap<>();
        List<BufferBlend> bufferBlends = new ArrayList<>();
        for (Map.Entry<String, ShaderProperties.Property> entry : properties.entrySet()) {
            String key = entry.getKey();
            ShaderProperties.Property property = entry.getValue();
            SourceLocation here = new SourceLocation(file, property.line);
            if (key.startsWith(ALPHA_TEST)) {
                String program = key.substring(ALPHA_TEST.length());
                if (!PROGRAM.matcher(program).matches()) {
                    issues.add(new PackIssue(here, key + " is ignored, '" + program + "' isn't a program name"));
                    continue;
                }
                AlphaTest test = alphaTest(key, property.value, here, issues);
                if (test != null) {
                    alphaTests.put(program, test);
                }
            } else if (key.startsWith(BLEND)) {
                String target = key.substring(BLEND.length());
                int dot = target.indexOf('.');
                String program = dot < 0 ? target : target.substring(0, dot);
                String buffer = dot < 0 ? null : target.substring(dot + 1);
                if (!PROGRAM.matcher(program).matches()) {
                    issues.add(new PackIssue(here, key + " is ignored, '" + program + "' isn't a program name"));
                    continue;
                }
                if (buffer != null && !BUFFER.matcher(buffer).matches()) {
                    issues.add(new PackIssue(here, key + " is ignored, '" + buffer + "' isn't a color buffer"));
                    continue;
                }
                Blend blend = blend(key, property.value, here, issues);
                if (blend == null) {
                    continue;
                }
                if (buffer == null) {
                    blends.put(program, blend);
                } else {
                    bufferBlends.add(new BufferBlend(program, buffer, blend));
                    issues.add(new PackIssue(here, key + " isn't applied yet, it needs per-buffer blending"));
                }
            }
        }
        if (alphaTests.isEmpty() && blends.isEmpty() && bufferBlends.isEmpty()) {
            return NONE;
        }
        return new ProgramStates(Collections.unmodifiableMap(alphaTests), Collections.unmodifiableMap(blends),
                Collections.unmodifiableList(bufferBlends));
    }

    @Nullable
    private static AlphaTest alphaTest(String key, String value, SourceLocation here, List<PackIssue> issues) {
        String[] words = value.trim().split("\\s+");
        if (words.length == 1 && words[0].equals(OFF)) {
            return new AlphaTest(false, 0, 0, here);
        }
        if (words.length != 2) {
            issues.add(new PackIssue(here, key + " is ignored, it needs 'off' or a function and a reference"));
            return null;
        }
        Integer function = FUNCTIONS.get(words[0]);
        if (function == null) {
            issues.add(new PackIssue(here, key + " is ignored, '" + words[0] + "' isn't one of "
                    + FUNCTIONS.keySet()));
            return null;
        }
        if (!NUMBER.matcher(words[1]).matches()) {
            issues.add(new PackIssue(here, key + " is ignored, '" + words[1] + "' isn't a number"));
            return null;
        }
        return new AlphaTest(true, function, Float.parseFloat(words[1]), here);
    }

    @Nullable
    private static Blend blend(String key, String value, SourceLocation here, List<PackIssue> issues) {
        String[] words = value.trim().split("\\s+");
        if (words.length == 1 && words[0].equals(OFF)) {
            return new Blend(false, 0, 0, 0, 0, here);
        }
        if (words.length != 4) {
            issues.add(new PackIssue(here, key + " is ignored, it needs 'off' or four blend factors"));
            return null;
        }
        int[] factors = new int[4];
        for (int i = 0; i < 4; i++) {
            Integer factor = FACTORS.get(words[i]);
            if (factor == null) {
                issues.add(new PackIssue(here, key + " is ignored, '" + words[i] + "' isn't one of "
                        + FACTORS.keySet()));
                return null;
            }
            factors[i] = factor;
        }
        return new Blend(true, factors[0], factors[1], factors[2], factors[3], here);
    }

    /** The alpha test the pack sets for a program, or null when it leaves it alone. */
    @Nullable
    public AlphaTest alphaTest(String program) {
        return alphaTests.get(program);
    }

    /** The blend mode the pack sets for a whole program, or null when it leaves it alone. */
    @Nullable
    public Blend blend(String program) {
        return blends.get(program);
    }

    /** Every alpha test by program name, in file order. Read only. */
    public Map<String, AlphaTest> alphaTests() {
        return alphaTests;
    }

    /** Every program-wide blend mode by program name, in file order. Read only. */
    public Map<String, Blend> blends() {
        return blends;
    }

    /** The per-buffer blend modes, in file order. None of them is applied yet. Read only. */
    public List<BufferBlend> bufferBlends() {
        return bufferBlends;
    }
}
