// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.shader.pack;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads {@code shaders.properties} through its preprocessor. Directives may be indented, any other line starting
 * with {@code #} or {@code !} is a comment, and a line ending in a backslash continues on the next one. A key
 * written twice keeps its last value, like Java properties.
 */
final class ShaderProperties {

    static final String FILE_NAME = "shaders.properties";

    // The directive has to follow the # directly, so a comment like "# if needed" stays a comment.
    private static final Pattern DIRECTIVE =
            Pattern.compile("^#(define|undef|ifdef|ifndef|if|elif|else|endif)\\b\\s*(.*)$");
    private static final Pattern NAME = Pattern.compile("[A-Za-z_]\\w*");

    /** One key's value and the line it came from. */
    static final class Property {

        final String value;
        final int line;

        Property(String value, int line) {
            this.value = value;
            this.line = line;
        }
    }

    // One #if level. Lines count only while every level is active.
    private static final class Level {

        final boolean parentActive;
        boolean active;
        boolean taken;
        boolean sawElse;

        Level(boolean parentActive, boolean active) {
            this.parentActive = parentActive;
            this.active = active;
            this.taken = active;
        }
    }

    private ShaderProperties() {
    }

    /**
     * Returns the keys the active lines set, in file order.
     *
     * @param macros the macros visible at the start of the file. {@code #define} and {@code #undef} only change a
     *     copy.
     */
    static Map<String, Property> read(ShaderPath file, String text, Map<String, String> macros,
            List<PackIssue> issues) {
        Map<String, String> defined = new LinkedHashMap<>(macros);
        Map<String, Property> properties = new LinkedHashMap<>();
        Deque<Level> levels = new ArrayDeque<>();
        String[] lines = text.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            int lineNumber = i + 1;
            String line = strip(lines[i]);
            boolean active = levels.isEmpty() || levels.peek().active;
            Matcher directive = DIRECTIVE.matcher(line);
            if (directive.matches()) {
                String argument = argument(directive.group(2));
                SourceLocation here = new SourceLocation(file, lineNumber);
                directive(directive.group(1), argument, active, levels, defined, here, issues);
                continue;
            }
            if (line.isEmpty() || line.startsWith("#") || line.startsWith("!")) {
                continue;
            }
            StringBuilder logical = new StringBuilder();
            while (endsWithContinuation(line) && i + 1 < lines.length) {
                logical.append(line, 0, line.length() - 1);
                line = strip(lines[++i]);
            }
            logical.append(endsWithContinuation(line) ? line.substring(0, line.length() - 1) : line);
            if (active) {
                put(logical.toString(), lineNumber, properties);
            }
        }
        if (!levels.isEmpty()) {
            issues.add(new PackIssue(new SourceLocation(file, lines.length), levels.size()
                    + " #if block(s) are never closed with #endif"));
        }
        return properties;
    }

    private static void directive(String name, String argument, boolean active, Deque<Level> levels,
            Map<String, String> defined, SourceLocation here, List<PackIssue> issues) {
        switch (name) {
            case "define":
            case "undef": {
                if (!active) {
                    return;
                }
                String[] parts = argument.split("\\s+", 2);
                if (!NAME.matcher(parts[0]).matches()) {
                    issues.add(new PackIssue(here, "#" + name + " needs a macro name"));
                } else if (name.equals("define")) {
                    defined.put(parts[0], parts.length > 1 ? parts[1].trim() : "");
                } else {
                    defined.remove(parts[0]);
                }
                return;
            }
            case "ifdef":
            case "ifndef": {
                boolean test = false;
                if (active) {
                    if (NAME.matcher(argument).matches()) {
                        test = defined.containsKey(argument) == name.equals("ifdef");
                    } else {
                        issues.add(new PackIssue(here, "#" + name + " needs a macro name"));
                    }
                }
                levels.push(new Level(active, test));
                return;
            }
            case "if":
                levels.push(new Level(active, active && test(argument, defined, here, issues)));
                return;
            case "elif": {
                Level level = levels.peek();
                if (level == null || level.sawElse) {
                    issues.add(new PackIssue(here, level == null ? "#elif without #if" : "#elif after #else"));
                    return;
                }
                level.active = !level.taken && level.parentActive && test(argument, defined, here, issues);
                level.taken |= level.active;
                return;
            }
            case "else": {
                Level level = levels.peek();
                if (level == null || level.sawElse) {
                    issues.add(new PackIssue(here, level == null ? "#else without #if" : "#else after #else"));
                    return;
                }
                level.active = !level.taken && level.parentActive;
                level.taken = true;
                level.sawElse = true;
                return;
            }
            default:
                if (levels.poll() == null) {
                    issues.add(new PackIssue(here, "#endif without #if"));
                }
        }
    }

    // A name that isn't defined counts as 0, as in C. One that is defined has to be a number.
    private static boolean test(String expression, Map<String, String> defined, SourceLocation here,
            List<PackIssue> issues) {
        try {
            return PackExpression.isTrue(PackExpression.evaluate(expression, new PackExpression.Names() {
                @Override
                public double value(String name) throws PackExpression.ExpressionException {
                    String value = defined.get(name);
                    if (value == null) {
                        return 0;
                    }
                    if (value.isEmpty()) {
                        throw new PackExpression.ExpressionException(name + " is defined without a value");
                    }
                    return PackExpression.number(value);
                }

                @Override
                public boolean defined(String name) {
                    return defined.containsKey(name);
                }
            }));
        } catch (PackExpression.ExpressionException e) {
            issues.add(new PackIssue(here, "Can't evaluate #if " + expression + ", so the block is skipped: "
                    + e.getMessage()));
            return false;
        }
    }

    // Keys end at the first equals sign, colon or whitespace, like Java properties.
    private static void put(String line, int lineNumber, Map<String, Property> properties) {
        int end = 0;
        while (end < line.length() && "=: \t".indexOf(line.charAt(end)) < 0) {
            end++;
        }
        String key = line.substring(0, end);
        int start = end;
        while (start < line.length() && Character.isWhitespace(line.charAt(start))) {
            start++;
        }
        if (start < line.length() && (line.charAt(start) == '=' || line.charAt(start) == ':')) {
            start++;
        }
        properties.put(key, new Property(line.substring(start).trim(), lineNumber));
    }

    private static String argument(String raw) {
        int comment = raw.indexOf("//");
        return (comment >= 0 ? raw.substring(0, comment) : raw).trim();
    }

    private static boolean endsWithContinuation(String line) {
        int backslashes = 0;
        for (int i = line.length() - 1; i >= 0 && line.charAt(i) == '\\'; i--) {
            backslashes++;
        }
        return backslashes % 2 == 1;
    }

    private static String strip(String line) {
        int start = 0;
        int end = line.length();
        while (start < end && Character.isWhitespace(line.charAt(start))) {
            start++;
        }
        while (end > start && Character.isWhitespace(line.charAt(end - 1))) {
            end--;
        }
        return line.substring(start, end);
    }
}
