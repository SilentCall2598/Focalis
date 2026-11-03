// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.shader.pack;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Declarations are in file order. A key written again replaces the earlier line at its own place, as if the earlier
 * line weren't written. Keys are compared as written, so {@code block.10} and {@code block.010} are two lines with the
 * same ID, and both count. Both rules follow OptiFine 1.12.2 HD U G5 as observed; the format only says one line per ID
 * is allowed.
 */
public final class MaterialMappings {

    public static final MaterialMappings NONE = new MaterialMappings(
            Collections.unmodifiableMap(new EnumMap<Kind, List<Declaration>>(Kind.class)),
            Collections.<Layer>emptyList());

    static final String LAYER = "layer.";
    private static final List<String> LAYERS = Arrays.asList("solid", "cutout", "cutout_mipped", "translucent");
    private static final Pattern DIGITS = Pattern.compile("[0-9]+");
    private static final Pattern METADATA = Pattern.compile("[0-9]+(-[0-9]+)?(,[0-9]+(-[0-9]+)?)*");

    public static final int ANY_METADATA = 0xFFFF;

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

        public String key() {
            return key;
        }

        public List<Selector> selectors() {
            return selectors;
        }

        public SourceLocation location() {
            return location;
        }

        @Override
        public String toString() {
            return key + " at " + location;
        }
    }

    /**
     * One entry of a line, like {@code stone}, {@code botania:reeds}, {@code wool:1,3-5} or
     * {@code minecraft:stone:variant=granite,diorite}. Without a namespace it's {@code minecraft}. Names are lowercased
     * like 1.12.2 resource locations. Metadata is a bit per value, property names and values are kept as written, and
     * only blocks have either.
     */
    public static final class Selector {

        private final String text;
        private final String namespace;
        private final String path;
        private final int metadata;
        private final Map<String, List<String>> properties;

        Selector(String text, String namespace, String path, int metadata, Map<String, List<String>> properties) {
            this.text = text;
            this.namespace = namespace;
            this.path = path;
            this.metadata = metadata;
            this.properties = properties;
        }

        public String text() {
            return text;
        }

        public String namespace() {
            return namespace;
        }

        public String path() {
            return path;
        }

        public String name() {
            return namespace + ":" + path;
        }

        public int metadata() {
            return metadata;
        }

        public Map<String, List<String>> properties() {
            return properties;
        }

        @Override
        public String toString() {
            return text;
        }
    }

    public static final class Layer {

        private final String name;
        private final List<Selector> blocks;
        private final SourceLocation location;

        Layer(String name, List<Selector> blocks, SourceLocation location) {
            this.name = name;
            this.blocks = blocks;
            this.location = location;
        }

        public String name() {
            return name;
        }

        public List<Selector> blocks() {
            return blocks;
        }

        public SourceLocation location() {
            return location;
        }

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

    /** Only the standard environment macros: the G5 format has no option macros in these files. */
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
        // Removing before putting moves a repeated key to its latest line, so the order stays the file's.
        Map<String, Declaration> byKey = new LinkedHashMap<>();
        for (ShaderProperties.Line line : ShaderProperties.readLines(file, text, macros, issues)) {
            SourceLocation here = new SourceLocation(file, line.line);
            if (line.key.startsWith(kind.prefix)) {
                String written = line.key.substring(kind.prefix.length());
                Integer id = id(written);
                if (id == null) {
                    issues.add(new PackIssue(here, line.key + " is ignored, '" + written + "' isn't a whole number from"
                            + " 0 to " + Integer.MAX_VALUE));
                    continue;
                }
                Declaration earlier = byKey.remove(line.key);
                if (earlier != null) {
                    issues.add(new PackIssue(here, line.key + " is written again, so the line at " + earlier.location()
                            + " no longer counts. Only one line per ID is allowed"));
                }
                byKey.put(line.key, new Declaration(kind, id, line.key, selectors(kind, line, here, issues), here));
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
        return Collections.unmodifiableList(new ArrayList<>(byKey.values()));
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
            String[] problem = new String[1];
            Selector selector = selector(kind, text, problem);
            if (selector == null) {
                issues.add(new PackIssue(here, line.key + " skips '" + text + "', " + problem[0]));
            } else {
                selectors.add(selector);
            }
        }
        return Collections.unmodifiableList(selectors);
    }

    @Nullable
    private static Selector selector(Kind kind, String text, String[] problem) {
        String[] parts = text.split(":", -1);
        if (DIGITS.matcher(parts[0]).matches()) {
            // Forge gives blocks and items other numbers in each world, see MaterialIds.
            problem[0] = "numeric IDs aren't supported, use names";
            return null;
        }
        int next = 1;
        String namespace = "minecraft";
        String path = parts[0];
        if (parts.length > 1 && parts[1].indexOf('=') < 0 && !METADATA.matcher(parts[1]).matches()) {
            namespace = parts[0];
            path = parts[1];
            next = 2;
        }
        if (namespace.isEmpty() || path.isEmpty() || path.indexOf('=') >= 0) {
            problem[0] = "a name is either name or namespace:name, followed by metadata and property=value parts";
            return null;
        }
        int metadata = ANY_METADATA;
        if (next < parts.length && METADATA.matcher(parts[next]).matches()) {
            metadata = metadata(parts[next]);
            if (metadata == 0) {
                problem[0] = "metadata values go from 0 to 15";
                return null;
            }
            next++;
        }
        if ((metadata != ANY_METADATA || next < parts.length) && kind != Kind.BLOCK) {
            problem[0] = (kind == Kind.ITEM ? "items" : "entities") + " can't have metadata or properties";
            return null;
        }
        Map<String, List<String>> properties = new LinkedHashMap<>();
        for (int i = next; i < parts.length; i++) {
            int equals = parts[i].indexOf('=');
            if (equals < 0) {
                problem[0] = "a property has to look like name=value";
                return null;
            }
            String property = parts[i].substring(0, equals);
            if (property.isEmpty()) {
                problem[0] = "a property has no name";
                return null;
            }
            String[] values = parts[i].substring(equals + 1).split(",", -1);
            for (String allowed : values) {
                if (allowed.isEmpty()) {
                    problem[0] = "property " + property + " has an empty value";
                    return null;
                }
            }
            if (properties.containsKey(property)) {
                problem[0] = "property " + property + " is listed twice";
                return null;
            }
            properties.put(property, Collections.unmodifiableList(Arrays.asList(values)));
        }
        return new Selector(text, namespace.toLowerCase(Locale.ROOT), path.toLowerCase(Locale.ROOT), metadata,
                properties.isEmpty() ? Collections.<String, List<String>>emptyMap()
                        : Collections.unmodifiableMap(properties));
    }

    private static int metadata(String text) {
        int bits = 0;
        for (String part : text.split(",")) {
            int dash = part.indexOf('-');
            int from;
            int to;
            try {
                from = Integer.parseInt(dash < 0 ? part : part.substring(0, dash));
                to = dash < 0 ? from : Integer.parseInt(part.substring(dash + 1));
            } catch (NumberFormatException e) {
                return 0;
            }
            if (from > to || to > 15) {
                return 0;
            }
            for (int value = from; value <= to; value++) {
                bits |= 1 << value;
            }
        }
        return bits;
    }

    public List<Declaration> declarations(Kind kind) {
        List<Declaration> list = declarations.get(kind);
        return list == null ? Collections.<Declaration>emptyList() : list;
    }

    public List<Layer> layers() {
        return layers;
    }
}
