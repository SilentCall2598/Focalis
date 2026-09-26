// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.shader.pack;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Shader source with every {@code #include} expanded. Each output line remembers which pack file and line it
 * came from, so compile errors can later point at the file the pack author actually wrote.
 */
public final class ResolvedSource {

    private final String text;
    private final ShaderPath[] fileByLine;
    private final int[] lineByLine;

    private ResolvedSource(String text, ShaderPath[] fileByLine, int[] lineByLine) {
        this.text = text;
        this.fileByLine = fileByLine;
        this.lineByLine = lineByLine;
    }

    public String text() {
        return text;
    }

    public int lineCount() {
        return lineByLine.length;
    }

    /** Where a one-based line of {@link #text()} came from. */
    public SourceLocation origin(int line) {
        if (line < 1 || line > lineByLine.length) {
            throw new IndexOutOfBoundsException("Line " + line + " of " + lineByLine.length);
        }
        ShaderPath file = fileByLine[line - 1];
        return file == null ? SourceLocation.ADDED_BY_FOCALIS : new SourceLocation(file, lineByLine[line - 1]);
    }

    /**
     * Returns a copy with {@code #define} lines for the environment macros followed by the pack option macros,
     * placed right after {@code #version} since GLSL requires that to come first.
     *
     * @throws IllegalArgumentException if an option uses the name of an environment macro
     */
    public ResolvedSource withDefines(ShaderMacros environment, ShaderMacros options) {
        for (String name : options.asMap().keySet()) {
            if (environment.isDefined(name)) {
                throw new IllegalArgumentException("Option '" + name + "' clashes with an environment macro");
            }
        }
        List<String> lines = lines();
        // Defines go right after #version when it's the first real line, otherwise at the very top.
        int versionLine = -1;
        int split = 0;
        CommentScanner comments = new CommentScanner();
        for (int i = 0; i < lines.size(); i++) {
            CommentScanner.ScannedLine scanned = comments.scan(lines.get(i));
            String code = scanned.code().trim();
            if (!code.isEmpty()) {
                if (code.startsWith("#version")) {
                    versionLine = i;
                    split = scanned.openCommentStart() >= 0 ? scanned.openCommentStart() : lines.get(i).length();
                }
                break;
            }
        }

        Builder builder = new Builder();
        for (int i = 0; i < versionLine; i++) {
            builder.add(lines.get(i), fileByLine[i], lineByLine[i]);
        }
        if (versionLine >= 0) {
            builder.add(lines.get(versionLine).substring(0, split), fileByLine[versionLine], lineByLine[versionLine]);
        }
        addDefines(builder, environment);
        addDefines(builder, options);
        if (versionLine >= 0 && split < lines.get(versionLine).length()) {
            // A block comment opened on the #version line would hide the defines, so it goes after them.
            builder.add(lines.get(versionLine).substring(split), fileByLine[versionLine], lineByLine[versionLine]);
        }
        for (int i = versionLine + 1; i < lines.size(); i++) {
            builder.add(lines.get(i), fileByLine[i], lineByLine[i]);
        }
        return builder.build();
    }

    private static void addDefines(Builder builder, ShaderMacros macros) {
        for (Map.Entry<String, String> macro : macros.asMap().entrySet()) {
            String define = macro.getValue().isEmpty()
                    ? "#define " + macro.getKey()
                    : "#define " + macro.getKey() + " " + macro.getValue();
            builder.add(define, null, 0);
        }
    }

    private List<String> lines() {
        List<String> lines = new ArrayList<>(lineByLine.length);
        int start = 0;
        for (int i = 0; i < lineByLine.length; i++) {
            int end = text.indexOf('\n', start);
            lines.add(text.substring(start, end));
            start = end + 1;
        }
        return lines;
    }

    /** Collects lines in order. Every line ends with a newline in the final text. */
    static final class Builder {

        private final StringBuilder text = new StringBuilder();
        private ShaderPath[] fileByLine = new ShaderPath[256];
        private int[] lineByLine = new int[256];
        private int lines;

        void add(String line, @Nullable ShaderPath file, int lineNumber) {
            if (lines == lineByLine.length) {
                fileByLine = Arrays.copyOf(fileByLine, lines * 2);
                lineByLine = Arrays.copyOf(lineByLine, lines * 2);
            }
            text.append(line).append('\n');
            fileByLine[lines] = file;
            lineByLine[lines] = lineNumber;
            lines++;
        }

        int length() {
            return text.length();
        }

        ResolvedSource build() {
            return new ResolvedSource(text.toString(), Arrays.copyOf(fileByLine, lines),
                    Arrays.copyOf(lineByLine, lines));
        }
    }
}
