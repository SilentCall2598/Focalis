// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.shader.material;

import io.github.silentcall2598.focalis.shader.pack.MaterialMappings;
import io.github.silentcall2598.focalis.shader.pack.SourceLocation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public final class MaterialReport {

    public enum Problem {
        UNKNOWN_NAME,
        UNKNOWN_PROPERTY,
        UNKNOWN_VALUE,
        CONFLICT,
        FAILED
    }

    public static final class Note {

        private final MaterialMappings.Kind kind;
        private final Problem problem;
        private final String key;
        private final String entry;
        private final SourceLocation location;
        private final String detail;

        Note(MaterialMappings.Kind kind, Problem problem, String key, String entry, SourceLocation location,
                String detail) {
            this.kind = kind;
            this.problem = problem;
            this.key = key;
            this.entry = entry;
            this.location = location;
            this.detail = detail;
        }

        public MaterialMappings.Kind kind() {
            return kind;
        }

        public Problem problem() {
            return problem;
        }

        public String key() {
            return key;
        }

        public String entry() {
            return entry;
        }

        public SourceLocation location() {
            return location;
        }

        public String detail() {
            return detail;
        }

        @Override
        public String toString() {
            return location + " " + key + " '" + entry + "' " + detail;
        }
    }

    private final Map<MaterialMappings.Kind, Integer> mapped;
    private final int blocks;
    private final List<Note> notes;
    private final long nanos;

    private MaterialReport(Map<MaterialMappings.Kind, Integer> mapped, int blocks, List<Note> notes, long nanos) {
        this.mapped = mapped;
        this.blocks = blocks;
        this.notes = notes;
        this.nanos = nanos;
    }

    public int mapped(MaterialMappings.Kind kind) {
        Integer count = mapped.get(kind);
        return count == null ? 0 : count;
    }

    public int blocks() {
        return blocks;
    }

    public List<Note> notes() {
        return notes;
    }

    public List<Note> notes(Problem problem) {
        List<Note> found = new ArrayList<>();
        for (Note note : notes) {
            if (note.problem == problem) {
                found.add(note);
            }
        }
        return found;
    }

    public long nanos() {
        return nanos;
    }

    static final class Builder {

        private final Map<MaterialMappings.Kind, Integer> mapped = new EnumMap<>(MaterialMappings.Kind.class);
        private int blocks;
        private final List<Note> notes = new ArrayList<>();

        void note(MaterialMappings.Declaration declaration, MaterialMappings.Selector selector, Problem problem,
                String detail) {
            notes.add(new Note(declaration.kind(), problem, declaration.key(), selector.text(),
                    declaration.location(), detail));
        }

        void mapped(MaterialMappings.Kind kind, int count) {
            mapped.put(kind, count);
        }

        void blocks(int count) {
            blocks = count;
        }

        MaterialReport build(long nanos) {
            return new MaterialReport(Collections.unmodifiableMap(new EnumMap<>(mapped)), blocks,
                    Collections.unmodifiableList(new ArrayList<>(notes)), nanos);
        }
    }
}
