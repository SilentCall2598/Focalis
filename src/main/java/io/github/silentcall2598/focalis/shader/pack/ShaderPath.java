// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.shader.pack;

import java.util.ArrayDeque;
import java.util.Deque;

/** Construction fails for anything that would point outside the shaders directory. */
public final class ShaderPath implements Comparable<ShaderPath> {

    private final String path;

    private ShaderPath(String path) {
        this.path = path;
    }

    /**
     * Parses a path relative to the shaders directory. A leading slash means the same thing, which is how
     * OptiFine-style absolute includes like {@code /lib/common.glsl} are written.
     *
     * @throws IllegalArgumentException if the path is empty, escapes the shaders directory or looks like a
     *                                  filesystem path such as {@code C:/...}
     */
    public static ShaderPath of(String raw) {
        Deque<String> segments = normalize(raw);
        if (segments.isEmpty()) {
            throw new IllegalArgumentException("'" + raw + "' does not name a file");
        }
        return new ShaderPath(String.join("/", segments));
    }

    static Deque<String> normalize(String raw) {
        // Backslashes show up in ZIP entries made on Windows.
        String unified = raw.replace('\\', '/');
        if (unified.indexOf(':') >= 0 || unified.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("'" + raw + "' is not a shaderpack path");
        }
        Deque<String> segments = new ArrayDeque<>();
        for (String segment : unified.split("/")) {
            if (segment.isEmpty() || segment.equals(".")) {
                continue;
            }
            if (segment.equals("..")) {
                if (segments.isEmpty()) {
                    throw new IllegalArgumentException("'" + raw + "' points outside the shaders directory");
                }
                segments.removeLast();
            } else {
                segments.addLast(segment);
            }
        }
        return segments;
    }

    public ShaderPath resolveSibling(String relative) {
        int slash = path.lastIndexOf('/');
        return slash < 0 ? of(relative) : of(path.substring(0, slash + 1) + relative);
    }

    public String directory() {
        int slash = path.lastIndexOf('/');
        return slash < 0 ? "" : path.substring(0, slash);
    }

    public String fileName() {
        return path.substring(path.lastIndexOf('/') + 1);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof ShaderPath && ((ShaderPath) other).path.equals(path);
    }

    @Override
    public int hashCode() {
        return path.hashCode();
    }

    @Override
    public int compareTo(ShaderPath other) {
        return path.compareTo(other.path);
    }

    @Override
    public String toString() {
        return path;
    }
}
