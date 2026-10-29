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
            "atlasSize", "playerMood", "biome", "biome_category",
            "temperature", "rainfall", "is_alive", "is_burning", "is_child", "is_glowing",
            "is_hurt", "is_in_lava", "is_in_water", "is_invisible", "is_on_ground", "is_ridden", "is_riding",
            "is_sneaking", "is_sprinting", "is_wet"));
    private static final Set<String> PER_DRAW = new HashSet<>(Arrays.asList("entityColor", "entityId",
            "blockEntityId", "fogMode", "fogColor", "fogStart", "fogEnd", "fogDensity"));

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

        private String describe() {
            return (uniform ? "Custom uniform " : "Custom variable ") + name;
        }
    }

    private final List<Declaration> declarations;
    private final Map<String, Declaration> uniforms;
    private final int[] order;
    private final int smoothSites;

    private CustomUniforms(List<Declaration> declarations, int[] order, int smoothSites) {
        this.declarations = declarations;
        this.order = order;
        this.smoothSites = smoothSites;
        Map<String, Declaration> usableUniforms = new LinkedHashMap<>();
        for (Declaration declaration : declarations) {
            if (declaration.uniform && declaration.usable()) {
                usableUniforms.put(declaration.name, declaration);
            }
        }
        this.uniforms = Collections.unmodifiableMap(usableUniforms);
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
            }
        }
        int[] order = orderAndPropagate(declarations);
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
        if (!NAME.matcher(declaration.name).matches()) {
            return "'" + declaration.name + "' isn't a valid name";
        }
        if (LATER_TYPES.contains(declaration.type)) {
            return "the type " + declaration.type + " isn't supported yet";
        }
        if (!SUPPORTED_TYPES.contains(declaration.type)) {
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
