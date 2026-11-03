// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.shader.pack;

final class CommentScanner {

    private boolean inBlockComment;

    static final class ScannedLine {

        private final String code;
        private final boolean startedInComment;
        private final int openCommentStart;

        private ScannedLine(String code, boolean startedInComment, int openCommentStart) {
            this.code = code;
            this.startedInComment = startedInComment;
            this.openCommentStart = openCommentStart;
        }

        /** The line with every comment character replaced by a space, so positions still match the original. */
        String code() {
            return code;
        }

        boolean startedInComment() {
            return startedInComment;
        }

        int openCommentStart() {
            return openCommentStart;
        }
    }

    ScannedLine scan(String line) {
        boolean startedInComment = inBlockComment;
        char[] code = line.toCharArray();
        int openCommentStart = -1;
        boolean inQuotes = false;
        int i = 0;
        while (i < code.length) {
            if (inBlockComment) {
                if (line.startsWith("*/", i)) {
                    code[i] = ' ';
                    code[i + 1] = ' ';
                    i += 2;
                    inBlockComment = false;
                    openCommentStart = -1;
                } else {
                    code[i++] = ' ';
                }
            } else if (inQuotes) {
                // GLSL has no string literals, but #include paths are quoted and may contain // or /*.
                inQuotes = line.charAt(i++) != '"';
            } else if (line.startsWith("//", i)) {
                for (int j = i; j < code.length; j++) {
                    code[j] = ' ';
                }
                break;
            } else if (line.startsWith("/*", i)) {
                openCommentStart = i;
                code[i] = ' ';
                code[i + 1] = ' ';
                i += 2;
                inBlockComment = true;
            } else {
                inQuotes = line.charAt(i++) == '"';
            }
        }
        return new ScannedLine(new String(code), startedInComment, openCommentStart);
    }
}
