// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.shader.routing;

import io.github.silentcall2598.focalis.shader.pack.ProgramDirectory;
import io.github.silentcall2598.focalis.shader.pack.ProgramSource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * Picks the pack program for a role in one program folder, using the program names and fallbacks of 1.12.2 era
 * shaderpacks. It only looks at that one folder, so a dimension folder never falls back to the root programs.
 * Nothing is compiled or bound here.
 */
public final class ShaderProgramResolver {

    // The legacy fallback order. A program falls back when the pack doesn't have it or has it disabled.
    private static final List<String> SKY_BASIC = chain("gbuffers_skybasic", "gbuffers_basic");
    private static final List<String> SKY_TEXTURED = chain("gbuffers_skytextured", "gbuffers_textured",
            "gbuffers_basic");
    private static final List<String> TEXTURED = chain("gbuffers_textured", "gbuffers_basic");
    private static final List<String> TEXTURED_LIT = chain("gbuffers_textured_lit", "gbuffers_textured",
            "gbuffers_basic");
    // The per layer terrain programs of that era were documented as unused, so all three layers share this.
    private static final List<String> TERRAIN = chain("gbuffers_terrain", "gbuffers_textured_lit",
            "gbuffers_textured", "gbuffers_basic");
    private static final List<String> WATER = chain("gbuffers_water", "gbuffers_terrain", "gbuffers_textured_lit",
            "gbuffers_textured", "gbuffers_basic");
    private static final List<String> ENTITIES = chain("gbuffers_entities", "gbuffers_textured_lit",
            "gbuffers_textured", "gbuffers_basic");
    private static final List<String> WEATHER = chain("gbuffers_weather", "gbuffers_textured_lit",
            "gbuffers_textured", "gbuffers_basic");
    private static final List<String> CLOUDS = chain("gbuffers_clouds", "gbuffers_textured", "gbuffers_basic");
    private static final List<String> HAND = chain("gbuffers_hand", "gbuffers_textured_lit", "gbuffers_textured",
            "gbuffers_basic");
    private static final List<String> NO_CHAIN = Collections.emptyList();

    private ShaderProgramResolver() {
    }

    /** Resolves with every program in the folder enabled. */
    public static ProgramResolution resolve(ProgramDirectory directory, ShaderProgramRole role) {
        return resolve(directory, role, program -> true);
    }

    /**
     * Resolves with some programs disabled. A disabled program counts as missing, so the role falls back past it,
     * which is what the format documents for programs that shaders.properties turns off.
     */
    public static ProgramResolution resolve(ProgramDirectory directory, ShaderProgramRole role,
            Predicate<ProgramSource> enabled) {
        Objects.requireNonNull(directory, "directory");
        Objects.requireNonNull(enabled, "enabled");
        ShaderProgramRole resolved = role == null ? ShaderProgramRole.UNCLASSIFIED : role;
        if (resolved == ShaderProgramRole.NONE) {
            return new ProgramResolution(resolved, ResolutionState.NOT_APPLICABLE, null, NO_CHAIN, -1);
        }
        List<String> candidates = candidates(resolved);
        if (candidates.isEmpty()) {
            return new ProgramResolution(resolved, ResolutionState.NEEDS_MORE_CONTEXT, null, NO_CHAIN, -1);
        }
        List<String> disabled = new ArrayList<>(0);
        for (int i = 0; i < candidates.size(); i++) {
            // A program that exists wins even if it failed to load. Falling past it would hide a pack error.
            ProgramSource program = directory.find(candidates.get(i));
            if (program != null && !enabled.test(program)) {
                disabled.add(program.name());
            } else if (program != null) {
                return new ProgramResolution(resolved, ResolutionState.RESOLVED, program, candidates, i,
                        frozen(disabled));
            }
        }
        return new ProgramResolution(resolved, disabled.isEmpty() ? ResolutionState.MISSING
                : ResolutionState.DISABLED, null, candidates, -1, frozen(disabled));
    }

    /** The program names that can draw a role, most specific first. Empty for roles that can't be resolved. */
    public static List<String> candidates(ShaderProgramRole role) {
        if (role == null) {
            return NO_CHAIN;
        }
        switch (role) {
            case SKY_BASIC:
                return SKY_BASIC;
            case SKY_TEXTURED:
                return SKY_TEXTURED;
            case TERRAIN_SOLID:
            case TERRAIN_CUTOUT_MIPPED:
            case TERRAIN_CUTOUT:
                return TERRAIN;
            case TERRAIN_TRANSLUCENT:
                return WATER;
            case ENTITIES:
                return ENTITIES;
            case PARTICLES_LIT:
                return TEXTURED_LIT;
            case PARTICLES_NORMAL:
                return TEXTURED;
            case WEATHER:
                return WEATHER;
            case CLOUDS:
                return CLOUDS;
            case HAND:
                return HAND;
            default:
                return NO_CHAIN;
        }
    }

    private static List<String> frozen(List<String> names) {
        return names.isEmpty() ? Collections.<String>emptyList() : Collections.unmodifiableList(names);
    }

    private static List<String> chain(String... names) {
        return Collections.unmodifiableList(Arrays.asList(names));
    }
}
