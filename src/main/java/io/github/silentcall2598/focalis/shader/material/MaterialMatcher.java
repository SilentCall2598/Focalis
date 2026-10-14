// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.shader.material;

import io.github.silentcall2598.focalis.shader.pack.MaterialMappings;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Matches declarations against registries seen only through names and property values, so it works without
 * Minecraft. A block state, item or entity type gets the ID of the first declaration in file order that matches it,
 * however specific a later one is, and later matches are reported as conflicts. Other implementations of the format
 * keep that order for block states for OptiFine parity, and items and entities follow the same rule here.
 *
 * <p>A property the block doesn't have is left out of the entry, so packs that add 1.13 properties to 1.12 names still
 * map those blocks. A value the property doesn't have matches nothing.
 */
final class MaterialMatcher {

    /** Blocks by registry name. */
    interface Blocks<S> {

        /** The block registered under a name like {@code minecraft:stone}, or null when there is none. */
        @Nullable
        Block<S> find(String name);
    }

    /** One block's states and how they read. */
    interface Block<S> {

        /** Every valid state, each one a distinct object. */
        Collection<S> states();

        /** The allowed values of a property as the game writes them, or null when the block has no such property. */
        @Nullable
        Collection<String> values(String property);

        /** A state's value of a property the block has, as the game writes it. */
        String value(S state, String property);
    }

    /** Items or entity types by registry name. */
    interface Names<T> {

        /** The object registered under a name like {@code minecraft:pig}, or null when there is none. */
        @Nullable
        T find(String name);
    }

    private MaterialMatcher() {
    }

    /** Block states to their IDs. Lookups need the exact state objects the registry handed out. */
    static <S> Map<S, Integer> blocks(List<MaterialMappings.Declaration> declarations, Blocks<S> blocks,
            MaterialReport.Builder report) {
        Map<S, MaterialMappings.Declaration> owners = new IdentityHashMap<>();
        Map<S, Integer> ids = new IdentityHashMap<>();
        Set<String> mappedBlocks = new HashSet<>();
        for (MaterialMappings.Declaration declaration : declarations) {
            // One box per line, shared by every state it maps.
            Integer id = declaration.id();
            for (MaterialMappings.Selector selector : declaration.selectors()) {
                List<S> states;
                try {
                    Block<S> block = blocks.find(selector.name());
                    if (block == null) {
                        report.note(declaration, selector, MaterialReport.Problem.UNKNOWN_NAME,
                                "isn't a block in this game");
                        continue;
                    }
                    states = matching(declaration, selector, block, report);
                } catch (RuntimeException | LinkageError e) {
                    report.note(declaration, selector, MaterialReport.Problem.FAILED, "can't be read: " + e);
                    continue;
                }
                int taken = 0;
                MaterialMappings.Declaration takenBy = null;
                for (S state : states) {
                    MaterialMappings.Declaration owner = owners.get(state);
                    if (owner == null) {
                        owners.put(state, declaration);
                        ids.put(state, id);
                        mappedBlocks.add(selector.name());
                    } else if (owner.id() != declaration.id()) {
                        taken++;
                        if (takenBy == null) {
                            takenBy = owner;
                        }
                    }
                }
                if (takenBy != null) {
                    report.note(declaration, selector, MaterialReport.Problem.CONFLICT, "loses " + taken + " of its "
                            + states.size() + " states to " + takenBy.key() + " from " + takenBy.location());
                }
            }
        }
        report.mapped(MaterialMappings.Kind.BLOCK, ids.size());
        report.blocks(mappedBlocks.size());
        return ids;
    }

    // The states the entry matches, all read before any of them gets an ID, so a block that throws maps nothing.
    private static <S> List<S> matching(MaterialMappings.Declaration declaration, MaterialMappings.Selector selector,
            Block<S> block, MaterialReport.Builder report) {
        List<String> names = new ArrayList<>();
        List<Set<String>> allowed = new ArrayList<>();
        boolean matchesNothing = false;
        for (Map.Entry<String, List<String>> property : selector.properties().entrySet()) {
            Collection<String> values = block.values(property.getKey());
            if (values == null) {
                report.note(declaration, selector, MaterialReport.Problem.UNKNOWN_PROPERTY, "names property "
                        + property.getKey() + ", which " + selector.name() + " doesn't have, so it's left out");
                continue;
            }
            Set<String> kept = new HashSet<>();
            for (String value : property.getValue()) {
                if (values.contains(value)) {
                    kept.add(value);
                } else {
                    report.note(declaration, selector, MaterialReport.Problem.UNKNOWN_VALUE, "names "
                            + property.getKey() + "=" + value + ", which isn't one of " + values);
                }
            }
            matchesNothing |= kept.isEmpty();
            names.add(property.getKey());
            allowed.add(kept);
        }
        List<S> states = new ArrayList<>();
        if (matchesNothing) {
            return states;
        }
        for (S state : block.states()) {
            if (matches(block, state, names, allowed)) {
                states.add(state);
            }
        }
        return states;
    }

    private static <S> boolean matches(Block<S> block, S state, List<String> names, List<Set<String>> allowed) {
        for (int i = 0; i < names.size(); i++) {
            if (!allowed.get(i).contains(block.value(state, names.get(i)))) {
                return false;
            }
        }
        return true;
    }

    /** Items or entity types to their IDs, by object identity. */
    static <T> Map<T, Integer> named(MaterialMappings.Kind kind, List<MaterialMappings.Declaration> declarations,
            Names<T> names, MaterialReport.Builder report) {
        Map<T, MaterialMappings.Declaration> owners = new IdentityHashMap<>();
        Map<T, Integer> ids = new IdentityHashMap<>();
        for (MaterialMappings.Declaration declaration : declarations) {
            Integer id = declaration.id();
            for (MaterialMappings.Selector selector : declaration.selectors()) {
                T found;
                try {
                    found = names.find(selector.name());
                } catch (RuntimeException | LinkageError e) {
                    report.note(declaration, selector, MaterialReport.Problem.FAILED, "can't be read: " + e);
                    continue;
                }
                if (found == null) {
                    report.note(declaration, selector, MaterialReport.Problem.UNKNOWN_NAME, "isn't "
                            + (kind == MaterialMappings.Kind.ITEM ? "an item" : "an entity") + " in this game");
                    continue;
                }
                MaterialMappings.Declaration owner = owners.get(found);
                if (owner == null) {
                    owners.put(found, declaration);
                    ids.put(found, id);
                } else if (owner.id() != declaration.id()) {
                    report.note(declaration, selector, MaterialReport.Problem.CONFLICT, "keeps the ID of "
                            + owner.key() + " from " + owner.location());
                }
            }
        }
        report.mapped(kind, ids.size());
        return ids;
    }
}
