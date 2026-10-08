// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.shader.pack;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The option values and program states of one pack under one set of {@link PackSettings}. Everything is worked out
 * once when it's created, before any source is prepared, so nothing here parses or reads files while drawing.
 * Immutable. Two configurations are equal when they'd prepare the same sources and pick the same programs.
 *
 * <p>Option values start at the sources as written, then the selected profile applies, then the explicit entries.
 * Profiles are read with only the environment macros, and {@code program.<name>.enabled} with the option macros
 * added. A disabled program counts as missing when roles pick their programs, so they fall back like the format
 * documents.
 */
public final class PackConfiguration {

    private static final ShaderPath PROPERTIES = ShaderPath.of(ShaderProperties.FILE_NAME);
    private static final String PROFILE = "profile.";
    private static final String DISABLE_PROGRAM = "!program.";
    private static final Pattern PROGRAM_ENABLED = Pattern.compile("program\\.(.+)\\.enabled");

    private final ShaderPack pack;
    @Nullable
    private final String profile;
    private final Map<String, String> values;
    private final Map<String, String> changed;
    private final Map<String, String> disabled;
    private final Map<ShaderPath, String> texts;
    private final List<PackIssue> issues;

    private PackConfiguration(ShaderPack pack, @Nullable String profile, Map<String, String> values,
            Map<String, String> changed, Map<String, String> disabled, Map<ShaderPath, String> texts,
            List<PackIssue> issues) {
        this.pack = pack;
        this.profile = profile;
        this.values = values;
        this.changed = changed;
        this.disabled = disabled;
        this.texts = texts;
        this.issues = issues;
    }

    /** The pack as written, with its own program conditions applied and the standard environment. */
    public static PackConfiguration defaults(ShaderPack pack) {
        return resolve(pack, PackSettings.NONE, StandardMacros.environment());
    }

    /**
     * Works out the configuration. Settings that don't fit the pack are reported in {@link #issues()} and left
     * out, and a program condition that can't be evaluated leaves its program enabled.
     */
    public static PackConfiguration resolve(ShaderPack pack, PackSettings settings, ShaderMacros environment) {
        Objects.requireNonNull(pack, "pack");
        Objects.requireNonNull(settings, "settings");
        Objects.requireNonNull(environment, "environment");
        ShaderOptions options = pack.options();
        String properties = pack.text(PROPERTIES);
        List<PackIssue> profileIssues = new ArrayList<>();
        Map<String, ShaderProperties.Property> profiles = properties == null
                ? Collections.<String, ShaderProperties.Property>emptyMap()
                : ShaderProperties.read(PROPERTIES, properties, environment.asMap(), profileIssues);

        Resolution resolution = new Resolution(pack, profiles);
        for (ShaderOption option : options.all().values()) {
            resolution.values.put(option.name(), option.defaultValue());
        }
        String profile = null;
        if (!settings.profile().isEmpty()) {
            if (profiles.containsKey(PROFILE + settings.profile())) {
                profile = settings.profile();
                resolution.applyProfile(profile, new ArrayDeque<String>());
            } else {
                resolution.issues.add(new PackIssue(null, "The pack has no profile '" + settings.profile()
                        + "', so none is applied"));
            }
        }
        resolution.applyEntries(settings.options(), null, null, null);

        List<PackIssue> conditionIssues = new ArrayList<>();
        Map<String, ShaderProperties.Property> conditions = properties == null
                ? Collections.<String, ShaderProperties.Property>emptyMap()
                : ShaderProperties.read(PROPERTIES, properties, optionMacros(options, resolution.values, environment),
                        conditionIssues);
        resolution.applyConditions(conditions);

        // Both reads see the same file, so its problems would mostly show up twice.
        List<PackIssue> issues = new ArrayList<>(resolution.issues);
        Set<String> reported = new HashSet<>();
        for (PackIssue issue : conditionIssues) {
            issues.add(issue);
            reported.add(issue.toString());
        }
        for (PackIssue issue : profileIssues) {
            if (!reported.contains(issue.toString())) {
                issues.add(issue);
            }
        }

        Map<String, String> changed = new LinkedHashMap<>();
        for (ShaderOption option : options.all().values()) {
            String value = resolution.values.get(option.name());
            if (!value.equals(option.defaultValue())) {
                changed.put(option.name(), value);
            }
        }
        return new PackConfiguration(pack, profile, Collections.unmodifiableMap(resolution.values),
                Collections.unmodifiableMap(changed), Collections.unmodifiableMap(resolution.disabled),
                Collections.unmodifiableMap(rewrite(pack, changed)), Collections.unmodifiableList(issues));
    }

    // Value options become macros with their value and switches that are on become empty macros. Ambiguous options
    // and consts don't, and the environment wins a name clash.
    private static Map<String, String> optionMacros(ShaderOptions options, Map<String, String> values,
            ShaderMacros environment) {
        Map<String, String> macros = new LinkedHashMap<>(environment.asMap());
        for (ShaderOption option : options.all().values()) {
            if (option.ambiguous() || macros.containsKey(option.name())) {
                continue;
            }
            String value = values.get(option.name());
            if (option.kind() == ShaderOption.Kind.VALUE) {
                macros.put(option.name(), value);
            } else if (option.kind() == ShaderOption.Kind.SWITCH && value.equals("true")) {
                macros.put(option.name(), "");
            }
        }
        return macros;
    }

    private static Map<ShaderPath, String> rewrite(ShaderPack pack, Map<String, String> changed) {
        Map<ShaderPath, Map<Integer, OptionScanner.OptionValue>> byFile = new TreeMap<>();
        for (Map.Entry<String, String> entry : changed.entrySet()) {
            ShaderOption option = pack.options().get(entry.getKey());
            OptionScanner.OptionValue value = new OptionScanner.OptionValue(option.kind(), entry.getValue());
            for (SourceLocation location : option.definitions()) {
                byFile.computeIfAbsent(location.file(), key -> new TreeMap<>()).put(location.line(), value);
            }
        }
        Map<ShaderPath, String> texts = new LinkedHashMap<>();
        for (Map.Entry<ShaderPath, Map<Integer, OptionScanner.OptionValue>> file : byFile.entrySet()) {
            texts.put(file.getKey(), OptionScanner.rewrite(pack.text(file.getKey()), file.getValue()));
        }
        return texts;
    }

    public ShaderPack pack() {
        return pack;
    }

    /** The profile that was applied, or null when none was selected or it doesn't exist. */
    @Nullable
    public String profile() {
        return profile;
    }

    /** The value of every option in name order. Read only. */
    public Map<String, String> values() {
        return values;
    }

    /** The options whose value differs from the sources as written. Read only. */
    public Map<String, String> changed() {
        return changed;
    }

    /** Disabled programs like {@code world-1/composite} with what disabled them, in name order. Read only. */
    public Map<String, String> disabledPrograms() {
        return disabled;
    }

    public boolean isEnabled(ProgramSource program) {
        return !disabled.containsKey(label(program));
    }

    /** Settings and pack lines that were ignored, and why. */
    public List<PackIssue> issues() {
        return issues;
    }

    /** A file's text with the option values written in, or null when no option changed it. */
    @Nullable
    String text(ShaderPath file) {
        return texts.get(file);
    }

    static String label(ProgramSource program) {
        return program.directory().isEmpty() ? program.name() : program.directory() + "/" + program.name();
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof PackConfiguration)) {
            return false;
        }
        PackConfiguration configuration = (PackConfiguration) other;
        return pack == configuration.pack && values.equals(configuration.values)
                && disabled.keySet().equals(configuration.disabled.keySet());
    }

    @Override
    public int hashCode() {
        return Objects.hash(System.identityHashCode(pack), values, disabled.keySet());
    }

    @Override
    public String toString() {
        return "profile " + profile + ", changed " + changed + ", disabled " + disabled.keySet();
    }

    // The state built up while resolving.
    private static final class Resolution {

        final ShaderPack pack;
        final Map<String, ShaderProperties.Property> profiles;
        final Map<String, String> values = new LinkedHashMap<>();
        final Map<String, String> disabled = new TreeMap<>();
        final List<PackIssue> issues = new ArrayList<>();

        Resolution(ShaderPack pack, Map<String, ShaderProperties.Property> profiles) {
            this.pack = pack;
            this.profiles = profiles;
        }

        void applyProfile(String name, Deque<String> chain) {
            ShaderProperties.Property property = profiles.get(PROFILE + name);
            chain.push(name);
            applyEntries(property.value, name, new SourceLocation(PROPERTIES, property.line), chain);
            chain.pop();
        }

        // Entries apply in order, so a later one overrides an earlier one and anything a referenced profile set.
        void applyEntries(String entries, @Nullable String profile, @Nullable SourceLocation where,
                @Nullable Deque<String> chain) {
            if (entries.isEmpty()) {
                return;
            }
            for (String entry : entries.split("\\s+")) {
                if (entry.startsWith(PROFILE)) {
                    inherit(entry, entry.substring(PROFILE.length()), profile, where, chain);
                } else if (entry.startsWith(DISABLE_PROGRAM)) {
                    disable(entry, entry.substring(DISABLE_PROGRAM.length()), profile, where);
                } else if (entry.startsWith("!")) {
                    set(entry, entry.substring(1), "false", true, where);
                } else {
                    int separator = firstSeparator(entry);
                    if (separator < 0) {
                        set(entry, entry, "true", true, where);
                    } else {
                        set(entry, entry.substring(0, separator), entry.substring(separator + 1), false, where);
                    }
                }
            }
        }

        private void inherit(String entry, String name, @Nullable String profile, @Nullable SourceLocation where,
                @Nullable Deque<String> chain) {
            if (profile == null || chain == null) {
                ignored(entry, where, "only a profile can include another profile");
            } else if (chain.contains(name)) {
                ignored(entry, where, "profile " + name + " includes itself");
            } else if (!profiles.containsKey(PROFILE + name)) {
                ignored(entry, where, "there is no profile " + name);
            } else {
                applyProfile(name, chain);
            }
        }

        private void disable(String entry, String label, @Nullable String profile, @Nullable SourceLocation where) {
            if (profile == null) {
                ignored(entry, where, "only a profile can disable programs");
            } else if (find(label) == null) {
                ignored(entry, where, "there is no program " + label);
            } else if (!disabled.containsKey(label)) {
                disabled.put(label, "profile " + profile);
            }
        }

        private void set(String entry, String name, String value, boolean bare, @Nullable SourceLocation where) {
            ShaderOption option = pack.options().get(name);
            if (option == null) {
                ignored(entry, where, "the pack has no option " + name);
            } else if (option.ambiguous()) {
                ignored(entry, where, name + " is defined differently in several places, so it can't change");
            } else if (bare && option.kind() != ShaderOption.Kind.SWITCH) {
                ignored(entry, where, name + " needs a value");
            } else if (!option.allowedValues().contains(value)) {
                ignored(entry, where, value + " isn't one of " + option.allowedValues());
            } else {
                values.put(name, value);
            }
        }

        void applyConditions(Map<String, ShaderProperties.Property> conditions) {
            for (Map.Entry<String, ShaderProperties.Property> entry : conditions.entrySet()) {
                Matcher key = PROGRAM_ENABLED.matcher(entry.getKey());
                if (!key.matches()) {
                    continue;
                }
                String label = key.group(1);
                SourceLocation here = new SourceLocation(PROPERTIES, entry.getValue().line);
                if (find(label) == null) {
                    issues.add(new PackIssue(here, entry.getKey() + " is ignored, there is no program " + label));
                    continue;
                }
                boolean enabled;
                try {
                    enabled = PackExpression.isTrue(PackExpression.evaluate(entry.getValue().value, switches()));
                } catch (PackExpression.ExpressionException e) {
                    issues.add(new PackIssue(here, "Can't evaluate " + entry.getKey() + ", so " + label
                            + " stays enabled: " + e.getMessage()));
                    continue;
                }
                if (!enabled && !disabled.containsKey(label)) {
                    disabled.put(label, PROPERTIES + " line " + entry.getValue().line);
                }
            }
        }

        // Program conditions may only use switch options and true or false.
        private PackExpression.Names switches() {
            return new PackExpression.Names() {
                @Override
                public double value(String name) throws PackExpression.ExpressionException {
                    if (name.equals("true") || name.equals("false")) {
                        return name.equals("true") ? 1 : 0;
                    }
                    ShaderOption option = pack.options().get(name);
                    if (option == null || option.kind() != ShaderOption.Kind.SWITCH) {
                        throw new PackExpression.ExpressionException(name + " isn't a switch option");
                    }
                    return values.get(name).equals("true") ? 1 : 0;
                }

                @Override
                public boolean defined(String name) throws PackExpression.ExpressionException {
                    throw new PackExpression.ExpressionException("defined can't be used here");
                }
            };
        }

        // A name without a folder is a program in the shaders folder itself.
        @Nullable
        private ProgramSource find(String label) {
            int slash = label.lastIndexOf('/');
            ProgramDirectory directory = slash < 0 ? pack.root()
                    : pack.dimensionDirectories().get(label.substring(0, slash));
            return directory == null ? null : directory.find(label.substring(slash + 1));
        }

        private void ignored(String entry, @Nullable SourceLocation where, String why) {
            String source = where == null ? "The option entry '" + entry + "' in the Focalis config"
                    : "The profile entry '" + entry + "'";
            issues.add(new PackIssue(where, source + " is ignored, " + why));
        }

        private static int firstSeparator(String entry) {
            int colon = entry.indexOf(':');
            int equals = entry.indexOf('=');
            if (colon < 0) {
                return equals;
            }
            return equals < 0 ? colon : Math.min(colon, equals);
        }
    }
}
