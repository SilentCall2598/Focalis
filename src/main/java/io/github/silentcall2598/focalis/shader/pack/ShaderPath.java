// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.shader.pack;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * A normalized path to a file inside a pack's {@code shaders} directory, such as {@code lib/common.glsl}.
 * Construction fails for anything that would point outside that directory.
 */
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

    /**
     * Splits {@code raw} into normalized segments. The result is empty for paths like {@code ./}.
     *
     * @throws IllegalArgumentException if the path escapes its root or looks like a filesystem path
     */
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

    /** Resolves {@code relative} against this file's directory, the way a relative include does. */
    public ShaderPath resolveSibling(String relative) {
        int slash = path.lastIndexOf('/');
        return slash < 0 ? of(relative) : of(path.substring(0, slash + 1) + relative);
    }

    /** The directory part of this path, or an empty string for files directly in the shaders directory. */
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
