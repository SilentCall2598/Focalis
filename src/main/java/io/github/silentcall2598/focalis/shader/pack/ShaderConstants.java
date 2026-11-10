// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.shader.pack;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The fragment shader configuration consts of one program folder, read the way OptiFine 1.12.2 HD U G5 was observed
 * to read them (black box, 2026-11-10): from the fragment shaders of the programs it loads, after preprocessing, as
 * code or inside block comments, as {@code const float}. Declarations in vertex shaders, after {@code //}, in a branch
 * that isn't taken or of another type don't count.
 */
public final class ShaderConstants {

    public enum Name {
        SUN_PATH_ROTATION("sunPathRotation", 0.0F, null),
        AMBIENT_OCCLUSION_LEVEL("ambientOcclusionLevel", 1.0F, null),
        WETNESS_HALFLIFE("wetnessHalflife", 600.0F, "WETNESSHL"),
        DRYNESS_HALFLIFE("drynessHalflife", 200.0F, "DRYNESSHL"),
        EYE_BRIGHTNESS_HALFLIFE("eyeBrightnessHalflife", 10.0F, null),
        CENTER_DEPTH_HALFLIFE("centerDepthHalflife", 1.0F, null);

        private final String constName;
        private final float defaultValue;
        @Nullable
        private final String commentForm;

        Name(String constName, float defaultValue, @Nullable String commentForm) {
            this.constName = constName;
            this.defaultValue = defaultValue;
            this.commentForm = commentForm;
        }

        public String constName() {
            return constName;
        }

        public float defaultValue() {
            return defaultValue;
        }
    }

    private static final Name[] NAMES = Name.values();

    public static final ShaderConstants DEFAULTS = new ShaderConstants(defaults(), new SourceLocation[NAMES.length]);

    // The programs in G5's shaders.txt program table that it loads; files with other names aren't programs to it.
    private static final Pattern LOADED_PROGRAM = Pattern.compile("gbuffers_(basic|textured|textured_lit|skybasic"
            + "|skytextured|clouds|terrain|damagedblock|block|beaconbeam|entities|entities_glowing|armor_glint"
            + "|spidereyes|hand|weather|water|hand_water)|(deferred|composite)([1-9]|1[0-5])?|final|shadow");
    private static final Pattern DIRECTIVE = Pattern.compile("^#\\s*(define|undef|ifdef|ifndef|if|elif|else|endif)"
            + "\\b\\s*(.*)$");
    private static final Pattern NAME = Pattern.compile("[A-Za-z_]\\w*");
    private static final Pattern MACRO = Pattern.compile("^([A-Za-z_]\\w*)(\\(?)\\s*(.*)$");
    private static final Pattern DECLARATION;
    private static final Pattern COMMENT_FORM = Pattern.compile("\\b(WETNESSHL|DRYNESSHL)\\s*:\\s*([^\\s*]+)");
    private static final Pattern NUMBER = Pattern.compile("[+-]?(\\d+\\.?\\d*|\\.\\d+)([eE][+-]?\\d+)?[fF]?");

    static {
        StringBuilder names = new StringBuilder();
        for (Name name : NAMES) {
            names.append(names.length() == 0 ? "" : "|").append(name.constName);
        }
        DECLARATION = Pattern.compile("\\bconst\\s+(\\w+)\\s+(" + names + ")\\s*=\\s*([^;]*);");
    }

    private final float[] values;
    private final SourceLocation[] locations;

    private ShaderConstants(float[] values, SourceLocation[] locations) {
        this.values = values;
        this.locations = locations;
    }

    private static float[] defaults() {
        float[] values = new float[NAMES.length];
        for (Name name : NAMES) {
            values[name.ordinal()] = name.defaultValue;
        }
        return values;
    }

    public float value(Name name) {
        return values[name.ordinal()];
    }

    /** Where the value comes from, or null when it's the default. */
    @Nullable
    public SourceLocation declaredAt(Name name) {
        return locations[name.ordinal()];
    }

    public float sunPathRotation() {
        return value(Name.SUN_PATH_ROTATION);
    }

    public float ambientOcclusionLevel() {
        return value(Name.AMBIENT_OCCLUSION_LEVEL);
    }

    static Map<String, ShaderConstants> readAll(ShaderPack pack, IncludeResolver.FileText texts,
            Map<String, String> macros, Predicate<ProgramSource> enabled, List<PackIssue> issues) {
        Map<String, ShaderConstants> folders = new LinkedHashMap<>();
        folders.put(pack.root().name(), read(pack.root(), texts, macros, enabled, issues));
        for (ProgramDirectory directory : pack.dimensionDirectories().values()) {
            folders.put(directory.name(), read(directory, texts, macros, enabled, issues));
        }
        return Collections.unmodifiableMap(folders);
    }

    static ShaderConstants read(ProgramDirectory directory, IncludeResolver.FileText texts, Map<String, String> macros,
            Predicate<ProgramSource> enabled, List<PackIssue> issues) {
        Found found = new Found();
        IncludeResolver resolver = new IncludeResolver(texts);
        for (ProgramSource program : directory.programs()) {
            ShaderPath fragment = program.file(ProgramStage.FRAGMENT);
            if (fragment == null || program.problem() != null || !LOADED_PROGRAM.matcher(program.name()).matches()
                    || !enabled.test(program)) {
                continue;
            }
            ResolvedSource source;
            try {
                source = resolver.resolve(fragment);
            } catch (IncludeException e) {
                issues.add(new PackIssue(new SourceLocation(fragment, 1), "The configuration consts of "
                        + PackConfiguration.label(program) + " can't be read, " + e.getMessage()));
                continue;
            }
            scan(source, macros, found);
        }
        return found.resolve(directory, issues);
    }

    private static void scan(ResolvedSource source, Map<String, String> macros, Found found) {
        Preprocessor preprocessor = new Preprocessor(macros);
        CommentScanner comments = new CommentScanner();
        String text = source.text();
        int start = 0;
        for (int line = 1; start < text.length(); line++) {
            int end = text.indexOf('\n', start);
            if (end < 0) {
                end = text.length();
            }
            String raw = text.substring(start, end);
            start = end + 1;
            CommentScanner.ScannedLine scanned = comments.scan(raw);
            String code = scanned.code().trim();
            if (code.startsWith("#")) {
                Matcher directive = DIRECTIVE.matcher(code);
                if (directive.matches()) {
                    preprocessor.directive(directive.group(1), directive.group(2).trim());
                }
                continue;
            }
            int state = preprocessor.state();
            if (state == Preprocessor.INACTIVE || (raw.indexOf("const") < 0 && raw.indexOf("HL") < 0)) {
                continue;
            }
            SourceLocation at = source.origin(line);
            find(DECLARATION.matcher(scanned.code()), at, state, preprocessor, found);
            find(DECLARATION.matcher(scanned.blockComments()), at, state, preprocessor, found);
            Matcher legacy = COMMENT_FORM.matcher(scanned.blockComments());
            while (legacy.find()) {
                Name name = legacy.group(1).equals("WETNESSHL") ? Name.WETNESS_HALFLIFE : Name.DRYNESS_HALFLIFE;
                found.add(name, "float", legacy.group(2), at, state, preprocessor.uncertainty());
            }
        }
    }

    private static void find(Matcher declaration, SourceLocation at, int state, Preprocessor preprocessor,
            Found found) {
        while (declaration.find()) {
            Name name = byConstName(declaration.group(2));
            found.add(name, declaration.group(1), declaration.group(3).trim(), at, state, preprocessor.uncertainty());
        }
    }

    private static Name byConstName(String constName) {
        for (Name name : NAMES) {
            if (name.constName.equals(constName)) {
                return name;
            }
        }
        throw new IllegalArgumentException(constName);
    }

    private static final class Declaration {

        final String text;
        final SourceLocation at;
        final float value;
        @Nullable
        final String problem;

        Declaration(String text, SourceLocation at, float value, @Nullable String problem) {
            this.text = text;
            this.at = at;
            this.value = value;
            this.problem = problem;
        }
    }

    private static final class Found {

        private final Map<Name, Map<String, Declaration>> declarations = new HashMap<>();

        void add(Name name, String type, String valueText, SourceLocation at, int state, @Nullable String uncertainty) {
            String text = name.constName + " = " + valueText;
            String problem = null;
            float value = Float.NaN;
            if (state == Preprocessor.UNKNOWN) {
                problem = "it can't be told whether it's compiled: " + uncertainty;
            } else if (!type.equals("float")) {
                problem = "it's declared as " + type + ", and only a float counts";
            } else if (!NUMBER.matcher(valueText).matches()) {
                problem = valueText + " isn't a plain number";
            } else {
                value = Float.parseFloat(valueText.endsWith("f") || valueText.endsWith("F")
                        ? valueText.substring(0, valueText.length() - 1) : valueText);
                if (Float.isInfinite(value) || Float.isNaN(value)) {
                    problem = valueText + " isn't a finite number";
                } else if (name == Name.AMBIENT_OCCLUSION_LEVEL && (value < 0.0F || value > 1.0F)) {
                    problem = "it's outside 0.0 to 1.0, the only levels Focalis supports";
                }
            }
            declarations.computeIfAbsent(name, key -> new LinkedHashMap<>())
                    .putIfAbsent(at + " " + text, new Declaration(text, at, value, problem));
        }

        ShaderConstants resolve(ProgramDirectory directory, List<PackIssue> issues) {
            float[] values = defaults();
            SourceLocation[] locations = new SourceLocation[NAMES.length];
            for (Name name : NAMES) {
                Map<String, Declaration> forName = declarations.get(name);
                if (forName == null) {
                    continue;
                }
                List<Declaration> all = new ArrayList<>(forName.values());
                boolean rejected = false;
                Set<Float> distinct = new HashSet<>();
                for (Declaration declaration : all) {
                    if (declaration.problem != null) {
                        rejected = true;
                        issues.add(new PackIssue(declaration.at, "const " + declaration.text + " is rejected, "
                                + declaration.problem + ", so " + directory.path() + " uses " + name.constName + " "
                                + format(name.defaultValue)));
                    } else {
                        distinct.add(declaration.value);
                    }
                }
                if (rejected) {
                    continue;
                }
                if (distinct.size() > 1) {
                    List<String> where = new ArrayList<>();
                    for (Declaration declaration : all) {
                        where.add(declaration.at + " (" + format(declaration.value) + ")");
                    }
                    issues.add(new PackIssue(all.get(0).at, "The programs in " + directory.path() + " declare "
                            + name.constName + " differently, at " + String.join(", ", where) + ", so it stays "
                            + format(name.defaultValue)));
                    continue;
                }
                values[name.ordinal()] = all.get(0).value;
                locations[name.ordinal()] = all.get(0).at;
            }
            return new ShaderConstants(values, locations);
        }
    }

    private static String format(float value) {
        return Float.toString(value);
    }

    // C preprocessing for deciding which declarations are compiled. A condition that can't be evaluated makes its
    // whole #if chain UNKNOWN rather than guessing a branch.
    static final class Preprocessor {

        static final int ACTIVE = 0;
        static final int INACTIVE = 1;
        static final int UNKNOWN = 2;
        private static final int MAX_DEPTH = 32;

        private static final class Level {

            final int parent;
            int state;
            boolean taken;
            boolean maybeTaken;
            boolean sawElse;
            @Nullable
            String uncertainty;

            Level(int parent) {
                this.parent = parent;
            }
        }

        private final Map<String, String> defined;
        // Names defined or removed in an UNKNOWN branch: their value depends on a branch that can't be decided.
        private final Set<String> poisoned = new HashSet<>();
        private final Deque<Level> levels = new ArrayDeque<>();

        Preprocessor(Map<String, String> macros) {
            defined = new HashMap<>(macros);
        }

        int state() {
            return levels.isEmpty() ? ACTIVE : levels.peek().state;
        }

        @Nullable
        String uncertainty() {
            for (Level level : levels) {
                if (level.uncertainty != null) {
                    return level.uncertainty;
                }
            }
            return null;
        }

        void directive(String name, String argument) {
            String plain = argument(argument);
            switch (name) {
                case "define":
                case "undef":
                    define(name, plain);
                    return;
                case "ifdef":
                case "ifndef": {
                    Level level = new Level(state());
                    levels.push(level);
                    branch(level, NAME.matcher(plain).matches() && !poisoned.contains(plain)
                            ? Boolean.valueOf(defined.containsKey(plain) == name.equals("ifdef")) : null,
                            "#" + name + " " + plain);
                    return;
                }
                case "if": {
                    Level level = new Level(state());
                    levels.push(level);
                    branch(level, test(plain), "#if " + plain);
                    return;
                }
                case "elif": {
                    Level level = levels.peek();
                    if (level != null && !level.sawElse) {
                        branch(level, level.parent == INACTIVE || level.taken ? Boolean.FALSE : test(plain),
                                "#elif " + plain);
                    }
                    return;
                }
                case "else": {
                    Level level = levels.peek();
                    if (level != null && !level.sawElse) {
                        level.sawElse = true;
                        branch(level, Boolean.TRUE, "#else");
                    }
                    return;
                }
                default:
                    levels.poll();
            }
        }

        private void define(String name, String argument) {
            int state = state();
            if (state == INACTIVE) {
                return;
            }
            Matcher macro = MACRO.matcher(argument);
            if (!macro.matches()) {
                return;
            }
            if (state == UNKNOWN) {
                poisoned.add(macro.group(1));
                return;
            }
            poisoned.remove(macro.group(1));
            if (name.equals("undef")) {
                defined.remove(macro.group(1));
            } else {
                defined.put(macro.group(1), macro.group(2).isEmpty() ? macro.group(3).trim() : "");
            }
        }

        private void branch(Level level, @Nullable Boolean condition, String directive) {
            if (level.parent == INACTIVE || level.taken) {
                level.state = INACTIVE;
                return;
            }
            if (condition == null) {
                level.state = UNKNOWN;
                level.maybeTaken = true;
                level.uncertainty = directive + " can't be evaluated";
                return;
            }
            if (!condition) {
                level.state = INACTIVE;
                return;
            }
            boolean unknown = level.parent == UNKNOWN || level.maybeTaken;
            level.state = unknown ? UNKNOWN : ACTIVE;
            if (unknown) {
                level.maybeTaken = true;
            } else {
                level.taken = true;
            }
        }

        @Nullable
        private Boolean test(String expression) {
            try {
                return PackExpression.isTrue(PackExpression.evaluate(expression, names(0)));
            } catch (PackExpression.ExpressionException e) {
                return null;
            }
        }

        private PackExpression.Names names(int depth) {
            return new PackExpression.Names() {
                @Override
                public double value(String name) throws PackExpression.ExpressionException {
                    if (poisoned.contains(name)) {
                        throw new PackExpression.ExpressionException(name + " is defined in an unknown branch");
                    }
                    String value = defined.get(name);
                    if (value == null) {
                        return 0;
                    }
                    if (value.isEmpty() || depth >= MAX_DEPTH) {
                        throw new PackExpression.ExpressionException(name + " has no usable value");
                    }
                    return PackExpression.evaluate(value, names(depth + 1));
                }

                @Override
                public boolean defined(String name) throws PackExpression.ExpressionException {
                    if (poisoned.contains(name)) {
                        throw new PackExpression.ExpressionException(name + " is defined in an unknown branch");
                    }
                    return defined.containsKey(name);
                }
            };
        }

        private static String argument(String raw) {
            int comment = raw.indexOf("//");
            return (comment >= 0 ? raw.substring(0, comment) : raw).trim();
        }
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof ShaderConstants && Arrays.equals(values, ((ShaderConstants) other).values);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(values);
    }

    @Override
    public String toString() {
        StringBuilder text = new StringBuilder();
        for (Name name : NAMES) {
            text.append(text.length() == 0 ? "" : ", ").append(name.constName).append(' ')
                    .append(format(values[name.ordinal()]));
        }
        return text.toString();
    }
}
