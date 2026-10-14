// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.shader.pack;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * The material IDs one configuration declares in {@code block.properties}, {@code item.properties} and
 * {@code entity.properties}, after the preprocessor. Only names and properties are kept. Matching them against the
 * game's registries is separate and has to wait until those are complete. Immutable.
 *
 * <p>Declarations keep the order their keys first appear in, which is the order matching tries them. A key written
 * twice keeps its last value at the place of its first line, like the other properties files of a pack.
 */
public final class MaterialMappings {

    public static final MaterialMappings NONE = new MaterialMappings(
            Collections.unmodifiableMap(new EnumMap<Kind, List<Declaration>>(Kind.class)),
            Collections.<Layer>emptyList());

    static final String LAYER = "layer.";
    private static final List<String> LAYERS = Arrays.asList("solid", "cutout", "translucent");
    private static final Pattern DIGITS = Pattern.compile("[0-9]+");

    /** The three mapping files and the key prefix each one uses. */
    public enum Kind {
        BLOCK("block.properties", "block."),
        ITEM("item.properties", "item."),
        ENTITY("entity.properties", "entity.");

        private final ShaderPath file;
        private final String prefix;

        Kind(String file, String prefix) {
            this.file = ShaderPath.of(file);
            this.prefix = prefix;
        }

        public ShaderPath file() {
            return file;
        }

        public String prefix() {
            return prefix;
        }
    }

    /** One {@code <kind>.<id>} line. It may list nothing, which maps nothing. */
    public static final class Declaration {

        private final Kind kind;
        private final int id;
        private final String key;
        private final List<Selector> selectors;
        private final SourceLocation location;

        Declaration(Kind kind, int id, String key, List<Selector> selectors, SourceLocation location) {
            this.kind = kind;
            this.id = id;
            this.key = key;
            this.selectors = selectors;
            this.location = location;
        }

        public Kind kind() {
            return kind;
        }

        public int id() {
            return id;
        }

        /** The key as written, like {@code block.10005}. */
        public String key() {
            return key;
        }

        /** The names on the line in written order, without the ones that couldn't be read. */
        public List<Selector> selectors() {
            return selectors;
        }

        /** The line the value came from. */
        public SourceLocation location() {
            return location;
        }

        @Override
        public String toString() {
            return key + " at " + location;
        }
    }

    /**
     * One entry of a line, like {@code stone}, {@code botania:reeds} or {@code minecraft:stone:variant=granite,diorite}.
     * Without a namespace it's {@code minecraft}. Names are lowercased like 1.12.2 resource locations. Property names
     * and values are kept as written, and only blocks have them.
     */
    public static final class Selector {

        private final String text;
        private final String namespace;
        private final String path;
        private final Map<String, List<String>> properties;

        Selector(String text, String namespace, String path, Map<String, List<String>> properties) {
            this.text = text;
            this.namespace = namespace;
            this.path = path;
            this.properties = properties;
        }

        /** The entry as written. */
        public String text() {
            return text;
        }

        public String namespace() {
            return namespace;
        }

        public String path() {
            return path;
        }

        /** The registry name like {@code minecraft:stone}. */
        public String name() {
            return namespace + ":" + path;
        }

        /** Property name to its allowed values, both in written order. Empty for a plain name. Read only. */
        public Map<String, List<String>> properties() {
            return properties;
        }

        @Override
        public String toString() {
            return text;
        }
    }

    /** A {@code layer.<name>} line. Kept for when Focalis can move blocks to another render layer. */
    public static final class Layer {

        private final String name;
        private final List<Selector> blocks;
        private final SourceLocation location;

        Layer(String name, List<Selector> blocks, SourceLocation location) {
            this.name = name;
            this.blocks = blocks;
            this.location = location;
        }

        /** solid, cutout or translucent. */
        public String name() {
            return name;
        }

        public List<Selector> blocks() {
            return blocks;
        }

        public SourceLocation location() {
            return location;
        }

        /** What Focalis still needs before it can apply this. */
        public String requires() {
            return "render layer overrides";
        }
    }

    private final Map<Kind, List<Declaration>> declarations;
    private final List<Layer> layers;

    private MaterialMappings(Map<Kind, List<Declaration>> declarations, List<Layer> layers) {
        this.declarations = declarations;
        this.layers = layers;
    }

    /**
     * Reads the mapping files the pack has with the given macros, which should be the ones its other properties
     * see. A line that can't be read is reported and left out, and a bad entry only costs itself.
     */
    static MaterialMappings read(ShaderPack pack, Map<String, String> macros, List<PackIssue> issues) {
        Map<Kind, List<Declaration>> declarations = new EnumMap<>(Kind.class);
        List<Layer> layers = new ArrayList<>();
        boolean found = false;
        for (Kind kind : Kind.values()) {
            String text = pack.text(kind.file());
            if (text != null) {
                found = true;
                declarations.put(kind, readFile(kind, text, macros, layers, issues));
            }
        }
        if (!found) {
            return NONE;
        }
        return new MaterialMappings(declarations, Collections.unmodifiableList(layers));
    }

    private static List<Declaration> readFile(Kind kind, String text, Map<String, String> macros, List<Layer> layers,
            List<PackIssue> issues) {
        ShaderPath file = kind.file();
        Map<String, ShaderProperties.Line> byKey = new LinkedHashMap<>();
        for (ShaderProperties.Line line : ShaderProperties.readLines(file, text, macros, issues)) {
            ShaderProperties.Line earlier = byKey.put(line.key, line);
            if (earlier != null) {
                issues.add(new PackIssue(new SourceLocation(file, line.line), line.key + " is written again, which"
                        + " replaces the value from line " + earlier.line + ". Only one line per ID is allowed"));
            }
        }
        List<Declaration> declarations = new ArrayList<>();
        Map<Integer, String> keysById = new HashMap<>();
        for (ShaderProperties.Line line : byKey.values()) {
            SourceLocation here = new SourceLocation(file, line.line);
            if (line.key.startsWith(kind.prefix)) {
                String written = line.key.substring(kind.prefix.length());
                Integer id = id(written);
                if (id == null) {
                    issues.add(new PackIssue(here, line.key + " is ignored, '" + written + "' isn't a whole number from"
                            + " 0 to " + Integer.MAX_VALUE));
                    continue;
                }
                String other = keysById.put(id, line.key);
                if (other != null) {
                    issues.add(new PackIssue(here, line.key + " declares ID " + id + " again after " + other
                            + ". Both lines are used"));
                }
                declarations.add(new Declaration(kind, id, line.key, selectors(kind, line, here, issues), here));
            } else if (kind == Kind.BLOCK && line.key.startsWith(LAYER)) {
                String name = line.key.substring(LAYER.length());
                if (!LAYERS.contains(name)) {
                    issues.add(new PackIssue(here, line.key + " is ignored, the layers are " + LAYERS));
                    continue;
                }
                Layer layer = new Layer(name, selectors(kind, line, here, issues), here);
                layers.add(layer);
                issues.add(new PackIssue(here, line.key + " isn't applied yet, it needs " + layer.requires()));
            } else {
                issues.add(new PackIssue(here, line.key + " is ignored, " + file + " only has " + kind.prefix
                        + "<id>" + (kind == Kind.BLOCK ? " and " + LAYER + "<layer>" : "") + " lines"));
            }
        }
        return Collections.unmodifiableList(declarations);
    }

    @Nullable
    private static Integer id(String written) {
        if (!DIGITS.matcher(written).matches()) {
            return null;
        }
        try {
            return Integer.parseInt(written);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static List<Selector> selectors(Kind kind, ShaderProperties.Line line, SourceLocation here,
            List<PackIssue> issues) {
        String value = line.value.trim();
        if (value.isEmpty()) {
            return Collections.emptyList();
        }
        List<Selector> selectors = new ArrayList<>();
        for (String text : value.split("\\s+")) {
            String problem = problem(kind, text);
            if (problem != null) {
                issues.add(new PackIssue(here, line.key + " skips '" + text + "', " + problem));
            } else {
                selectors.add(selector(text));
            }
        }
        return Collections.unmodifiableList(selectors);
    }

    // Why an entry can't be read, or null when it can. The name is everything before the first part with an equals
    // sign, which may have a namespace in front.
    @Nullable
    private static String problem(Kind kind, String text) {
        String[] parts = text.split(":", -1);
        int names = nameParts(parts);
        if (names == 0 || names > 2) {
            return "a name is either name or namespace:name, followed by property=value parts";
        }
        for (int i = 0; i < names; i++) {
            if (parts[i].isEmpty()) {
                return "it has an empty name part";
            }
            // Numeric IDs change between worlds in modded 1.12.2, and states are matched by property, not metadata.
            if (DIGITS.matcher(parts[i]).matches()) {
                return "numeric IDs and metadata values aren't supported, use names and properties";
            }
        }
        if (names < parts.length && kind != Kind.BLOCK) {
            return (kind == Kind.ITEM ? "items" : "entities") + " can't have properties";
        }
        List<String> seen = new ArrayList<>();
        for (int i = names; i < parts.length; i++) {
            int equals = parts[i].indexOf('=');
            if (equals < 0) {
                return "a property has to look like name=value";
            }
            String property = parts[i].substring(0, equals);
            if (property.isEmpty()) {
                return "a property has no name";
            }
            for (String allowed : parts[i].substring(equals + 1).split(",", -1)) {
                if (allowed.isEmpty()) {
                    return "property " + property + " has an empty value";
                }
            }
            if (seen.contains(property)) {
                return "property " + property + " is listed twice";
            }
            seen.add(property);
        }
        return null;
    }

    private static int nameParts(String[] parts) {
        int names = 0;
        while (names < parts.length && parts[names].indexOf('=') < 0) {
            names++;
        }
        return names;
    }

    // Only for entries problem() accepted.
    private static Selector selector(String text) {
        String[] parts = text.split(":", -1);
        int names = nameParts(parts);
        String namespace = names == 2 ? parts[0].toLowerCase(Locale.ROOT) : "minecraft";
        String path = parts[names - 1].toLowerCase(Locale.ROOT);
        Map<String, List<String>> properties = new LinkedHashMap<>();
        for (int i = names; i < parts.length; i++) {
            int equals = parts[i].indexOf('=');
            properties.put(parts[i].substring(0, equals), Collections.unmodifiableList(
                    Arrays.asList(parts[i].substring(equals + 1).split(","))));
        }
        return new Selector(text, namespace, path, properties.isEmpty()
                ? Collections.<String, List<String>>emptyMap() : Collections.unmodifiableMap(properties));
    }

    /** The declarations of one file in matching order, empty when the pack doesn't have the file. */
    public List<Declaration> declarations(Kind kind) {
        List<Declaration> list = declarations.get(kind);
        return list == null ? Collections.<Declaration>emptyList() : list;
    }

    /** The layer lines of block.properties in file order. None of them is applied yet. */
    public List<Layer> layers() {
        return layers;
    }
}
