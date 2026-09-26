// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.shader.pack;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Expands {@code #include "path"} directives. A path starting with a slash is relative to the shaders directory,
 * anything else is relative to the including file. Directives inside comments are left alone.
 */
final class IncludeResolver {

    /** A second safeguard next to cycle detection, only meant to stop runaway input. */
    static final int MAX_DEPTH = 32;
    /** Stops a file that includes the same big file many times from growing without bound. */
    static final int MAX_RESOLVED_CHARS = 16 * 1024 * 1024;

    private static final Pattern INCLUDE_DIRECTIVE = Pattern.compile("^\\s*#\\s*include\\b(.*)$");
    // Comments are already blanked out by CommentScanner by the time this runs.
    private static final Pattern QUOTED_PATH = Pattern.compile("^\\s*\"([^\"]*)\"\\s*$");

    @FunctionalInterface
    interface FileText {

        /** Returns the text of {@code path}, or null when the pack has no such file. */
        @Nullable
        String read(ShaderPath path) throws IOException;
    }

    private final FileText files;

    IncludeResolver(FileText files) {
        this.files = files;
    }

    ResolvedSource resolve(ShaderPath file) throws IncludeException {
        ResolvedSource.Builder output = new ResolvedSource.Builder();
        expand(file, new ArrayList<ShaderPath>(), null, output);
        return output.build();
    }

    // Includes are expanded before any other preprocessing, so #ifdef guards can't break a cycle. Iris works the
    // same way.
    private void expand(ShaderPath file, List<ShaderPath> chain, @Nullable SourceLocation includedAt,
            ResolvedSource.Builder output) throws IncludeException {
        int cycleStart = chain.indexOf(file);
        if (cycleStart >= 0) {
            StringBuilder cycle = new StringBuilder("#include cycle: ");
            for (ShaderPath link : chain.subList(cycleStart, chain.size())) {
                cycle.append(link).append(" -> ");
            }
            throw new IncludeException(cycle.append(file).toString(), includedAt);
        }
        if (chain.size() >= MAX_DEPTH) {
            throw new IncludeException("#include nested deeper than " + MAX_DEPTH + " levels", includedAt);
        }

        String text = read(file, includedAt);
        chain.add(file);
        CommentScanner comments = new CommentScanner();
        String[] lines = text.split("\n", -1);
        // A trailing newline leaves an empty last element that isn't a real line.
        int lineCount = text.endsWith("\n") ? lines.length - 1 : lines.length;
        for (int i = 0; i < lineCount; i++) {
            String line = lines[i].endsWith("\r") ? lines[i].substring(0, lines[i].length() - 1) : lines[i];
            CommentScanner.ScannedLine scanned = comments.scan(line);
            Matcher directive = INCLUDE_DIRECTIVE.matcher(scanned.code());
            if (!directive.matches()) {
                add(output, line, file, i + 1, includedAt);
                continue;
            }
            SourceLocation here = new SourceLocation(file, i + 1);
            ShaderPath target = target(file, directive.group(1), here);
            // A comment that ends before the directive or starts after it spans other lines, so keep that part.
            if (scanned.startedInComment()) {
                add(output, line.substring(0, scanned.code().indexOf('#')), file, i + 1, includedAt);
            }
            expand(target, chain, here, output);
            if (scanned.openCommentStart() >= 0) {
                add(output, line.substring(scanned.openCommentStart()), file, i + 1, includedAt);
            }
        }
        chain.remove(chain.size() - 1);
    }

    private static void add(ResolvedSource.Builder output, String line, ShaderPath file, int lineNumber,
            @Nullable SourceLocation includedAt) throws IncludeException {
        output.add(line, file, lineNumber);
        if (output.length() > MAX_RESOLVED_CHARS) {
            throw new IncludeException("Source grows past " + MAX_RESOLVED_CHARS + " characters after expanding"
                    + " includes", includedAt);
        }
    }

    private static ShaderPath target(ShaderPath from, String directiveArguments, SourceLocation here)
            throws IncludeException {
        Matcher quoted = QUOTED_PATH.matcher(directiveArguments);
        if (!quoted.matches() || quoted.group(1).trim().isEmpty()) {
            throw new IncludeException("Malformed #include, expected #include \"path\"", here);
        }
        String path = quoted.group(1);
        try {
            return path.startsWith("/") ? ShaderPath.of(path) : from.resolveSibling(path);
        } catch (IllegalArgumentException e) {
            throw new IncludeException("Rejected #include \"" + path + "\": " + e.getMessage(), here);
        }
    }

    private String read(ShaderPath file, @Nullable SourceLocation includedAt) throws IncludeException {
        String text;
        try {
            text = files.read(file);
        } catch (IOException e) {
            throw new IncludeException("Could not read " + file + ": " + e.getMessage(), includedAt);
        }
        if (text == null) {
            throw new IncludeException("Missing file " + file, includedAt);
        }
        return text;
    }
}
