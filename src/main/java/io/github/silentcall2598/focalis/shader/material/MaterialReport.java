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

/**
 * What resolving one configuration's mappings found. Every entry that didn't do everything it says gets a note, so
 * logs can stay short and still point at the lines. Immutable.
 */
public final class MaterialReport {

    /** Why an entry did less than it says. */
    public enum Problem {
        /** The game has nothing by that name, which is normal for packs written for several versions. */
        UNKNOWN_NAME,
        /** The block has no property by that name, so the condition is left out and the rest of the entry applies. */
        UNKNOWN_PROPERTY,
        /** The property doesn't have that value, so it matches no state. */
        UNKNOWN_VALUE,
        /** Some of what the entry matches already has another ID from an earlier line, which it keeps. */
        CONFLICT,
        /** Reading the entry's block, item or entity threw, so it maps nothing. */
        FAILED
    }

    /** One entry and what happened to it. */
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

        /** The line's key, like {@code block.10005}. */
        public String key() {
            return key;
        }

        /** The entry as written, like {@code furnace:lit=false}. */
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

    /** How many block states, items or entity types got an ID. */
    public int mapped(MaterialMappings.Kind kind) {
        Integer count = mapped.get(kind);
        return count == null ? 0 : count;
    }

    /** How many different blocks have at least one state with an ID. */
    public int blocks() {
        return blocks;
    }

    /** Every note in the order the entries were resolved. Read only. */
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

    /** How long resolving took. */
    public long nanos() {
        return nanos;
    }

    /** Collects a report while the matcher runs. */
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
