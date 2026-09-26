// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.shader.pack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * The folder mapping from {@code dimension.properties}, like {@code dimension.world-1=minecraft:the_nether}.
 * Dimension ids are kept exactly as written. Matching them against 1.12.2 dimensions happens later.
 */
public final class DimensionProperties {

    static final String FILE_NAME = "dimension.properties";
    static final DimensionProperties NONE = new DimensionProperties(Collections.<String, List<String>>emptyMap());

    private static final String KEY_PREFIX = "dimension.";
    private static final Pattern DIRECTIVE =
            Pattern.compile("#\\s*(if|ifdef|ifndef|elif|else|endif|define|undef|include)\\b");

    private final Map<String, List<String>> dimensionsByFolder;

    private DimensionProperties(Map<String, List<String>> dimensionsByFolder) {
        this.dimensionsByFolder = dimensionsByFolder;
    }

    /** Folder name to the dimension ids listed for it, in file order and exactly as written, including {@code *}. */
    public Map<String, List<String>> dimensionsByFolder() {
        return dimensionsByFolder;
    }

    /** Parses the file, skipping lines it can't understand and reporting them in {@code issues}. */
    static DimensionProperties parse(ShaderPath file, String text, List<PackIssue> issues) {
        Map<String, List<String>> result = new LinkedHashMap<>();
        boolean reportedDirective = false;
        String[] lines = text.split("\r?\n");
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            // Directives like #if are skipped like comments, so entries from every branch are kept. That isn't
            // what the pack means, which is why it gets reported.
            if (!reportedDirective && DIRECTIVE.matcher(line).lookingAt()) {
                issues.add(new PackIssue(new SourceLocation(file, i + 1), "Preprocessor directives aren't supported"
                        + " in " + FILE_NAME + " yet, so entries from every #if branch are kept"));
                reportedDirective = true;
            }
            if (line.isEmpty() || line.startsWith("#") || line.startsWith("!")) {
                continue;
            }
            SourceLocation location = new SourceLocation(file, i + 1);
            int equals = line.indexOf('=');
            String key = equals < 0 ? line : line.substring(0, equals).trim();
            String folder = key.startsWith(KEY_PREFIX) ? key.substring(KEY_PREFIX.length()) : "";
            if (equals < 0 || folder.isEmpty() || !isPlainFolderName(folder)) {
                issues.add(new PackIssue(location, "Expected dimension.<folder>=<dimension ids>"));
                continue;
            }
            String value = line.substring(equals + 1).trim();
            if (value.isEmpty()) {
                issues.add(new PackIssue(location, "No dimensions listed for folder " + folder));
                continue;
            }
            if (result.containsKey(folder)) {
                issues.add(new PackIssue(location, "Folder " + folder + " is listed again, keeping the first entry"));
                continue;
            }
            result.put(folder, Collections.unmodifiableList(new ArrayList<>(Arrays.asList(value.split("\\s+")))));
        }
        return new DimensionProperties(Collections.unmodifiableMap(result));
    }

    private static boolean isPlainFolderName(String folder) {
        try {
            return ShaderPath.of(folder).toString().equals(folder) && folder.indexOf('/') < 0;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
