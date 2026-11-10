// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.shader.pack;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class CustomUniforms {

    public static final CustomUniforms NONE = new CustomUniforms(Collections.<Declaration>emptyList(), new int[0], 0);

    private static final Pattern KEY = Pattern.compile("(uniform|variable)\\.([^.]+)\\.(.+)");
    private static final Pattern NAME = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");
    private static final Set<String> SUPPORTED_TYPES = new HashSet<>(Arrays.asList("float", "bool"));
    private static final Set<String> LATER_TYPES = new HashSet<>(Arrays.asList("int", "vec2", "vec3", "vec4"));
    private static final Set<String> UNAVAILABLE = new HashSet<>(Arrays.asList("heldItemId", "heldBlockLightValue",
            "heldItemId2", "heldBlockLightValue2", "sunPosition", "moonPosition",
            "shadowLightPosition", "upPosition", "gbufferModelView", "gbufferModelViewInverse",
            "gbufferPreviousModelView", "gbufferProjection", "gbufferProjectionInverse", "gbufferPreviousProjection",
            "shadowProjection", "shadowProjectionInverse", "shadowModelView", "shadowModelViewInverse", "wetness",
            "eyeBrightnessSmooth", "terrainTextureSize", "terrainIconSize", "hideGUI", "centerDepthSmooth",
            "atlasSize", "playerMood", "biome", "biome_category", "blendFunc", "instanceId",
            "temperature", "rainfall", "is_alive", "is_burning", "is_child", "is_glowing",
            "is_hurt", "is_in_lava", "is_in_water", "is_invisible", "is_on_ground", "is_ridden", "is_riding",
            "is_sneaking", "is_sprinting", "is_wet"));
    private static final Set<String> PER_DRAW = new HashSet<>(Arrays.asList("entityColor", "entityId",
            "blockEntityId", "fogMode", "fogColor", "fogStart", "fogEnd", "fogDensity"));

    // What the G5 docs give expressions and Focalis doesn't implement yet, as opposed to what they don't give at all.
    private static final Set<String> FUNCTIONS = new HashSet<>(Arrays.asList("abs", "min", "max", "sqrt", "if", "ifb",
            "in", "between", "smooth", "clamp", "floor", "ceil"));
    private static final Set<String> UNIMPLEMENTED_FUNCTIONS = new HashSet<>(Arrays.asList("sin", "cos", "asin",
            "acos", "tan", "atan", "atan2", "torad", "todeg", "exp", "frac", "log", "pow", "random", "round", "signum",
            "fmod", "equals", "vec2", "vec3", "vec4"));
    private static final Map<String, Pattern> UNIMPLEMENTED_INPUTS = new HashMap<>();
    private static final Pattern SCALAR = Pattern.compile("");
    private static final Pattern VEC3 = Pattern.compile("|[xyz]");
    private static final Pattern IVEC2 = Pattern.compile("|[xy]");
    private static final Pattern IVEC4 = Pattern.compile("|[xyzw]");
    private static final Pattern MAT4 = Pattern.compile("|[0-3]\\.[0-3]");

    static {
        for (String name : Arrays.asList("heldItemId", "heldBlockLightValue", "heldItemId2", "heldBlockLightValue2",
                "fogDensity", "wetness", "terrainIconSize", "hideGUI", "centerDepthSmooth", "instanceId", "biome",
                "biome_category", "temperature", "rainfall", "is_alive", "is_burning", "is_child", "is_glowing",
                "is_hurt", "is_in_lava", "is_in_water", "is_invisible", "is_on_ground", "is_ridden", "is_riding",
                "is_sneaking", "is_sprinting", "is_wet")) {
            UNIMPLEMENTED_INPUTS.put(name, SCALAR);
        }
        for (String name : Arrays.asList("sunPosition", "moonPosition", "shadowLightPosition", "upPosition")) {
            UNIMPLEMENTED_INPUTS.put(name, VEC3);
        }
        for (String name : Arrays.asList("eyeBrightnessSmooth", "terrainTextureSize", "atlasSize")) {
            UNIMPLEMENTED_INPUTS.put(name, IVEC2);
        }
        UNIMPLEMENTED_INPUTS.put("blendFunc", IVEC4);
        for (String name : Arrays.asList("gbufferModelView", "gbufferModelViewInverse", "gbufferPreviousModelView",
                "gbufferProjection", "gbufferProjectionInverse", "gbufferPreviousProjection", "shadowProjection",
                "shadowProjectionInverse", "shadowModelView", "shadowModelViewInverse")) {
            UNIMPLEMENTED_INPUTS.put(name, MAT4);
        }
    }

    // The G5 docs mention biome category constants but neither biome_category nor their names, which come from the
    // current docs. A program reading a uniform that uses them is refused rather than given 0, since whether G5 1.12.2
    // provides them is unknown.
    private static final Set<String> CATEGORIES = new HashSet<>(Arrays.asList("NONE", "TAIGA", "EXTREME_HILLS",
            "JUNGLE", "MESA", "PLAINS", "SAVANNA", "ICY", "THE_END", "BEACH", "FOREST", "OCEAN", "DESERT", "RIVER",
            "SWAMP", "MUSHROOM", "NETHER"));
    // BIOME_ constants of the vanilla 1.12.2 biome registry. Modded biomes' constants can't be told from typos here.
    private static final Set<String> BIOMES = new HashSet<>(Arrays.asList("OCEAN", "PLAINS", "DESERT",
            "EXTREME_HILLS", "FOREST", "TAIGA", "SWAMPLAND", "RIVER", "HELL", "SKY", "FROZEN_OCEAN", "FROZEN_RIVER",
            "ICE_FLATS", "ICE_MOUNTAINS", "MUSHROOM_ISLAND", "MUSHROOM_ISLAND_SHORE", "BEACHES", "DESERT_HILLS",
            "FOREST_HILLS", "TAIGA_HILLS", "SMALLER_EXTREME_HILLS", "JUNGLE", "JUNGLE_HILLS", "JUNGLE_EDGE",
            "DEEP_OCEAN", "STONE_BEACH", "COLD_BEACH", "BIRCH_FOREST", "BIRCH_FOREST_HILLS", "ROOFED_FOREST",
            "TAIGA_COLD", "TAIGA_COLD_HILLS", "REDWOOD_TAIGA", "REDWOOD_TAIGA_HILLS", "EXTREME_HILLS_WITH_TREES",
            "SAVANNA", "SAVANNA_ROCK", "MESA", "MESA_ROCK", "MESA_CLEAR_ROCK", "VOID", "MUTATED_PLAINS",
            "MUTATED_DESERT", "MUTATED_EXTREME_HILLS", "MUTATED_FOREST", "MUTATED_TAIGA", "MUTATED_SWAMPLAND",
            "MUTATED_ICE_FLATS", "MUTATED_JUNGLE", "MUTATED_JUNGLE_EDGE", "MUTATED_BIRCH_FOREST",
            "MUTATED_BIRCH_FOREST_HILLS", "MUTATED_ROOFED_FOREST", "MUTATED_TAIGA_COLD", "MUTATED_REDWOOD_TAIGA",
            "MUTATED_REDWOOD_TAIGA_HILLS", "MUTATED_EXTREME_HILLS_WITH_TREES", "MUTATED_SAVANNA",
            "MUTATED_SAVANNA_ROCK", "MUTATED_MESA", "MUTATED_MESA_ROCK", "MUTATED_MESA_CLEAR_ROCK"));
    private static final Pattern TOKEN =
            Pattern.compile("(?<![A-Za-z0-9_.])([A-Za-z_][A-Za-z0-9_]*(?:\\.[A-Za-z0-9_]+)*)\\s*(\\()?");
    private static final Pattern SYNTAX_TOKEN = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*(?:\\.[A-Za-z0-9_]+)*"
            + "|(?:\\d+\\.?\\d*|\\.\\d+)(?:[eE][+-]?\\d+)?[fF]?|&&|\\|\\||==|!=|<=|>=|[-+*/%!<>(),]");
    private static final Set<String> BINARY = new HashSet<>(Arrays.asList("+", "-", "*", "/", "%", "&&", "||",
            "==", "!=", "<", ">", "<=", ">="));
    private static final int USABLE = 0;
    private static final int UNIMPLEMENTED = 1;
    private static final int OUTSIDE = 2;

    public static final class Declaration {

        private final int index;
        private final String name;
        private final boolean uniform;
        private final String type;
        private final String expressionText;
        private final SourceLocation location;
        @Nullable
        private CustomExpression expression;
        @Nullable
        private String problem;
        @Nullable
        private String missingFeature;
        private boolean outside;
        private final Set<Integer> dependencies = new HashSet<>();

        private Declaration(int index, String name, boolean uniform, String type, String expressionText,
                SourceLocation location) {
            this.index = index;
            this.name = name;
            this.uniform = uniform;
            this.type = type;
            this.expressionText = expressionText;
            this.location = location;
        }

        public int index() {
            return index;
        }

        public String name() {
            return name;
        }

        public boolean uniform() {
            return uniform;
        }

        public String type() {
            return type;
        }

        public boolean bool() {
            return type.equals("bool");
        }

        public String expression() {
            return expressionText;
        }

        public SourceLocation location() {
            return location;
        }

        public boolean usable() {
            return problem == null;
        }

        @Nullable
        public String problem() {
            return problem;
        }

        /**
         * The G5-documented feature this unusable declaration needs and Focalis lacks, or null when it's usable or
         * would fail under G5 too (a name G5 doesn't have, a syntax or type error, a cycle).
         */
        @Nullable
        public String missingFeature() {
            return missingFeature;
        }

        private String describe() {
            return (uniform ? "Custom uniform " : "Custom variable ") + name;
        }
    }

    private final List<Declaration> declarations;
    private final Map<String, Declaration> uniforms;
    private final Map<String, Declaration> unimplemented;
    private final int[] order;
    private final int smoothSites;

    private CustomUniforms(List<Declaration> declarations, int[] order, int smoothSites) {
        this.declarations = declarations;
        this.order = order;
        this.smoothSites = smoothSites;
        Map<String, Declaration> usableUniforms = new LinkedHashMap<>();
        Map<String, Declaration> missing = new LinkedHashMap<>();
        for (Declaration declaration : declarations) {
            if (declaration.uniform && declaration.usable()) {
                usableUniforms.put(declaration.name, declaration);
            } else if (declaration.uniform && declaration.missingFeature != null) {
                missing.put(declaration.name, declaration);
            }
        }
        this.uniforms = Collections.unmodifiableMap(usableUniforms);
        this.unimplemented = Collections.unmodifiableMap(missing);
    }

    static CustomUniforms compile(ShaderPath file, Map<String, ShaderProperties.Property> properties,
            List<PackIssue> issues) {
        List<Declaration> declarations = new ArrayList<>();
        Map<String, List<Declaration>> byName = new HashMap<>();
        for (Map.Entry<String, ShaderProperties.Property> entry : properties.entrySet()) {
            Matcher key = KEY.matcher(entry.getKey());
            if (!key.matches()) {
                continue;
            }
            Declaration declaration = new Declaration(declarations.size(), key.group(3), key.group(1).equals("uniform"),
                    key.group(2), entry.getValue().value, new SourceLocation(file, entry.getValue().line));
            declarations.add(declaration);
            byName.computeIfAbsent(declaration.name, name -> new ArrayList<>()).add(declaration);
        }
        if (declarations.isEmpty()) {
            return NONE;
        }

        for (Declaration declaration : declarations) {
            declaration.problem = declarationProblem(declaration, byName);
        }
        int[] nextSite = {0};
        for (Declaration declaration : declarations) {
            if (declaration.problem == null) {
                compileExpression(declaration, byName, nextSite);
            } else if (declaration.missingFeature != null) {
                refuseBoolForInt(declaration, byName);
            }
        }
        int[] order = orderAndPropagate(declarations);
        int[] kinds = new int[declarations.size()];
        Arrays.fill(kinds, -1);
        for (Declaration declaration : declarations) {
            classify(declaration, declarations, byName, kinds, new HashSet<Integer>());
        }
        for (Declaration declaration : declarations) {
            if (declaration.problem != null) {
                issues.add(new PackIssue(declaration.location, declaration.describe() + " can't be used, "
                        + declaration.problem));
            }
        }
        return new CustomUniforms(Collections.unmodifiableList(declarations), order, nextSite[0]);
    }

    @Nullable
    private static String declarationProblem(Declaration declaration, Map<String, List<Declaration>> byName) {
        String problem = outsideProblem(declaration, byName);
        if (problem != null) {
            declaration.outside = true;
            return problem;
        }
        if (LATER_TYPES.contains(declaration.type)) {
            declaration.missingFeature = "the custom uniform type " + declaration.type;
            return "the type " + declaration.type + " isn't supported yet";
        }
        return null;
    }

    @Nullable
    private static String outsideProblem(Declaration declaration, Map<String, List<Declaration>> byName) {
        if (!NAME.matcher(declaration.name).matches()) {
            return "'" + declaration.name + "' isn't a valid name";
        }
        if (!SUPPORTED_TYPES.contains(declaration.type) && !LATER_TYPES.contains(declaration.type)) {
            return "there is no type " + declaration.type;
        }
        List<Declaration> same = byName.get(declaration.name);
        if (same.size() > 1) {
            List<Integer> lines = new ArrayList<>();
            for (Declaration other : same) {
                lines.add(other.location.line());
            }
            return declaration.name + " is declared more than once, on lines " + lines;
        }
        if (isBuiltInName(declaration.name)) {
            return declaration.name + " is the name of a built-in value";
        }
        return null;
    }

    // G5 refuses a bool expression in an int declaration and leaves the uniform at 0 (observed 2026-11-10).
    private static void refuseBoolForInt(Declaration declaration, Map<String, List<Declaration>> byName) {
        if (!declaration.type.equals("int")) {
            return;
        }
        try {
            CustomExpression trial = CustomExpressionParser.parse(declaration.expressionText,
                    name -> resolve(name, declaration, byName), new int[1]);
            if (trial.type == CustomExpression.Type.BOOL) {
                declaration.outside = true;
                declaration.problem = "it's declared int but its expression is a bool";
            }
        } catch (CustomExpressionParser.ParseException e) {
            // Its other problems decide below.
        }
    }

    // A declaration only needs a missing feature when nothing in it would fail under G5 as well.
    private static int classify(Declaration declaration, List<Declaration> all, Map<String, List<Declaration>> byName,
            int[] kinds, Set<Integer> visiting) {
        if (kinds[declaration.index] >= 0) {
            return kinds[declaration.index];
        }
        if (declaration.problem == null) {
            return kinds[declaration.index] = USABLE;
        }
        if (!visiting.add(declaration.index) || declaration.outside) {
            declaration.missingFeature = null;
            return kinds[declaration.index] = OUTSIDE;
        }
        String missing = declaration.missingFeature;
        boolean outside = !wellFormed(declaration.expressionText);
        if (declaration.problem.contains("smooth needs 1 or 4 arguments")) {
            missing = missing != null ? missing : "smooth with 2 or 3 arguments";
        } else if (declaration.problem.startsWith("it's part of the cycle")) {
            outside = true;
        }
        Matcher token = TOKEN.matcher(declaration.expressionText);
        while (!outside && token.find()) {
            String name = token.group(1);
            String base = name.indexOf('.') < 0 ? name : name.substring(0, name.indexOf('.'));
            if (token.group(2) != null) {
                if (UNIMPLEMENTED_FUNCTIONS.contains(name)) {
                    missing = missing != null ? missing : "the function " + name;
                } else if (!FUNCTIONS.contains(name)) {
                    outside = true;
                }
                continue;
            }
            List<Declaration> declared = byName.get(name);
            if (declared != null) {
                int kind = classify(declared.get(0), all, byName, kinds, visiting);
                if (kind == OUTSIDE) {
                    outside = true;
                } else if (kind == UNIMPLEMENTED) {
                    missing = missing != null ? missing : name + ", which needs "
                            + declared.get(0).missingFeature;
                }
            } else if (name.equals("pi") || name.equals("true") || name.equals("false") || name.equals("PPT_NONE")
                    || name.equals("PPT_RAIN") || name.equals("PPT_SNOW") || CustomInput.find(name) != null) {
                continue;
            } else if (UNIMPLEMENTED_INPUTS.containsKey(base) && !CustomInput.isBaseName(base)
                    && UNIMPLEMENTED_INPUTS.get(base).matcher(name.substring(Math.min(name.length(),
                    base.length() + 1))).matches()) {
                missing = missing != null ? missing : "the input " + base;
            } else if (name.startsWith("BIOME_") && BIOMES.contains(name.substring("BIOME_".length()))) {
                missing = missing != null ? missing : "the biome constants";
            } else if (name.startsWith("CAT_") && CATEGORIES.contains(name.substring("CAT_".length()))) {
                missing = missing != null ? missing : "the biome category constants";
            } else {
                outside = true;
            }
        }
        visiting.remove(declaration.index);
        if (outside || missing == null) {
            declaration.missingFeature = null;
            return kinds[declaration.index] = OUTSIDE;
        }
        declaration.missingFeature = missing;
        return kinds[declaration.index] = UNIMPLEMENTED;
    }

    private static boolean isBuiltInName(String name) {
        return name.equals("pi") || name.equals("true") || name.equals("false") || name.startsWith("PPT_")
                || CustomInput.isBaseName(name)
                || UNAVAILABLE.contains(name) || PER_DRAW.contains(name);
    }

    private static void compileExpression(Declaration declaration, Map<String, List<Declaration>> byName,
            int[] nextSite) {
        try {
            CustomExpression expression = CustomExpressionParser.parse(declaration.expressionText,
                    name -> resolve(name, declaration, byName), nextSite);
            String wanted = declaration.type;
            String actual = CustomExpressionParser.name(expression.type);
            if (!wanted.equals(actual)) {
                declaration.problem = "it's declared " + wanted + " but its expression is a " + actual;
                return;
            }
            declaration.expression = expression;
        } catch (CustomExpressionParser.ParseException e) {
            declaration.problem = e.getMessage();
        }
    }

    private static CustomExpression resolve(String name, Declaration from, Map<String, List<Declaration>> byName)
            throws CustomExpressionParser.ParseException {
        switch (name) {
            case "pi":
                return new CustomExpression.Number((float) Math.PI);
            case "PPT_NONE":
                return new CustomExpression.Number(0);
            case "PPT_RAIN":
                return new CustomExpression.Number(1);
            case "PPT_SNOW":
                return new CustomExpression.Number(2);
            case "true":
                return new CustomExpression.Truth(true);
            case "false":
                return new CustomExpression.Truth(false);
            default:
                break;
        }
        List<Declaration> declared = byName.get(name);
        if (declared != null) {
            Declaration target = declared.get(0);
            from.dependencies.add(target.index);
            return new CustomExpression.Reference(target.index,
                    target.bool() ? CustomExpression.Type.BOOL : CustomExpression.Type.FLOAT);
        }
        CustomInput input = CustomInput.find(name);
        if (input != null) {
            return new CustomExpression.Input(input);
        }
        String base = name.indexOf('.') < 0 ? name : name.substring(0, name.indexOf('.'));
        if (CustomInput.isBaseName(base)) {
            throw new CustomExpressionParser.ParseException(base + " has no component '"
                    + name.substring(base.length() + Math.min(1, name.length() - base.length())) + "'");
        }
        if (PER_DRAW.contains(base)) {
            throw new CustomExpressionParser.ParseException(base + " changes during a program, so expressions can't"
                    + " use it");
        }
        if (UNAVAILABLE.contains(base)) {
            throw new CustomExpressionParser.ParseException(base + " isn't available to expressions in Focalis yet");
        }
        if (base.startsWith("BIOME_") || base.startsWith("CAT_") || base.startsWith("PPT_")) {
            throw new CustomExpressionParser.ParseException("biome constants like " + base + " aren't available in"
                    + " Focalis yet");
        }
        throw new CustomExpressionParser.ParseException("there is nothing named " + name);
    }

    // The parser stops at its first problem, often a name, so the grammar is checked on its own, whatever the names.
    private static boolean wellFormed(String text) {
        List<String> tokens = new ArrayList<>();
        Matcher token = SYNTAX_TOKEN.matcher(text);
        int end = 0;
        while (token.find()) {
            if (!text.substring(end, token.start()).trim().isEmpty()) {
                return false;
            }
            tokens.add(token.group());
            end = token.end();
        }
        if (!text.substring(end).trim().isEmpty()) {
            return false;
        }
        int[] position = {0};
        return expression(tokens, position) && position[0] == tokens.size();
    }

    private static boolean expression(List<String> tokens, int[] position) {
        if (!operand(tokens, position)) {
            return false;
        }
        while (position[0] < tokens.size() && BINARY.contains(tokens.get(position[0]))) {
            position[0]++;
            if (!operand(tokens, position)) {
                return false;
            }
        }
        return true;
    }

    private static boolean operand(List<String> tokens, int[] position) {
        while (position[0] < tokens.size() && "!-+".contains(tokens.get(position[0]))
                && tokens.get(position[0]).length() == 1) {
            position[0]++;
        }
        if (position[0] >= tokens.size()) {
            return false;
        }
        String first = tokens.get(position[0]++);
        if (first.equals("(")) {
            return expression(tokens, position) && accept(tokens, position, ")");
        }
        if (Character.isDigit(first.charAt(0)) || first.charAt(0) == '.') {
            return true;
        }
        if (!Character.isLetter(first.charAt(0)) && first.charAt(0) != '_') {
            return false;
        }
        if (!accept(tokens, position, "(")) {
            return true;
        }
        if (accept(tokens, position, ")")) {
            return true;
        }
        do {
            if (!expression(tokens, position)) {
                return false;
            }
        } while (accept(tokens, position, ","));
        return accept(tokens, position, ")");
    }

    private static boolean accept(List<String> tokens, int[] position, String expected) {
        if (position[0] < tokens.size() && tokens.get(position[0]).equals(expected)) {
            position[0]++;
            return true;
        }
        return false;
    }

    private static int[] orderAndPropagate(List<Declaration> declarations) {
        int count = declarations.size();
        int[] state = new int[count];
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            visit(i, declarations, state, new ArrayList<Integer>(), order);
        }
        int[] result = new int[order.size()];
        int used = 0;
        for (int index : order) {
            if (declarations.get(index).problem == null) {
                result[used++] = index;
            }
        }
        return Arrays.copyOf(result, used);
    }

    private static final int NEW = 0;
    private static final int ACTIVE = 1;
    private static final int DONE = 2;

    private static void visit(int index, List<Declaration> declarations, int[] state, List<Integer> path,
            List<Integer> order) {
        if (state[index] == DONE) {
            return;
        }
        Declaration declaration = declarations.get(index);
        state[index] = ACTIVE;
        path.add(index);
        for (int dependency : sorted(declaration.dependencies)) {
            if (state[dependency] == ACTIVE) {
                markCycle(path, dependency, declarations);
                continue;
            }
            visit(dependency, declarations, state, path, order);
        }
        path.remove(path.size() - 1);
        state[index] = DONE;
        if (declaration.problem == null) {
            for (int dependency : sorted(declaration.dependencies)) {
                Declaration needed = declarations.get(dependency);
                if (needed.problem != null) {
                    declaration.problem = "it needs " + needed.name + ", which can't be used: " + needed.problem;
                    break;
                }
            }
        }
        order.add(index);
    }

    private static void markCycle(List<Integer> path, int start, List<Declaration> declarations) {
        List<String> names = new ArrayList<>();
        for (int i = path.indexOf(start); i < path.size(); i++) {
            names.add(declarations.get(path.get(i)).name);
        }
        names.add(declarations.get(start).name);
        String cycle = "it's part of the cycle " + String.join(" -> ", names);
        for (int i = path.indexOf(start); i < path.size(); i++) {
            Declaration member = declarations.get(path.get(i));
            if (member.problem == null) {
                member.problem = cycle;
            }
        }
    }

    private static List<Integer> sorted(Set<Integer> values) {
        List<Integer> list = new ArrayList<>(values);
        Collections.sort(list);
        return list;
    }

    public List<Declaration> declarations() {
        return declarations;
    }

    public Map<String, Declaration> uniforms() {
        return uniforms;
    }

    public Map<String, Declaration> unimplemented() {
        return unimplemented;
    }

    public int smoothSites() {
        return smoothSites;
    }

    public int evaluatedCount() {
        return order.length;
    }

    public boolean isEmpty() {
        return uniforms.isEmpty();
    }

    public void evaluate(CustomContext context, float[] values) {
        for (int index : order) {
            Declaration declaration = declarations.get(index);
            CustomExpression expression = declaration.expression;
            values[index] = expression.type == CustomExpression.Type.BOOL
                    ? (expression.test(context, values) ? 1 : 0)
                    : expression.number(context, values);
        }
    }
}
