// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.shader.routing;

import io.github.silentcall2598.focalis.shader.pack.ProgramDirectory;
import io.github.silentcall2598.focalis.shader.pack.ProgramSource;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Picks the pack program for a role in one program folder, using the program names and fallbacks of 1.12.2 era
 * shaderpacks. It only looks at that one folder, so a dimension folder never falls back to the root programs.
 * Nothing is compiled or bound here.
 */
public final class ShaderProgramResolver {

    // The legacy fallback order. A program falls back when the pack doesn't have it at all.
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

    // Programs that shaders.properties turns off would count as missing, but those settings aren't read yet, so the
    // folder is taken as loaded.
    public static ProgramResolution resolve(ProgramDirectory directory, ShaderProgramRole role) {
        Objects.requireNonNull(directory, "directory");
        ShaderProgramRole resolved = role == null ? ShaderProgramRole.UNCLASSIFIED : role;
        if (resolved == ShaderProgramRole.NONE) {
            return new ProgramResolution(resolved, ResolutionState.NOT_APPLICABLE, null, NO_CHAIN, -1);
        }
        List<String> candidates = candidates(resolved);
        if (candidates.isEmpty()) {
            return new ProgramResolution(resolved, ResolutionState.NEEDS_MORE_CONTEXT, null, NO_CHAIN, -1);
        }
        for (int i = 0; i < candidates.size(); i++) {
            // A program that exists wins even if it failed to load. Falling past it would hide a pack error.
            ProgramSource program = directory.find(candidates.get(i));
            if (program != null) {
                return new ProgramResolution(resolved, ResolutionState.RESOLVED, program, candidates, i);
            }
        }
        return new ProgramResolution(resolved, ResolutionState.MISSING, null, candidates, -1);
    }

    /** The program names that can draw a role, most specific first. Empty for roles that can't be resolved. */
    public static List<String> candidates(ShaderProgramRole role) {
        if (role == null) {
            return NO_CHAIN;
        }
        switch (role) {
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
            case SKY:
                // The sky call draws both what gbuffers_skybasic and what gbuffers_skytextured are for, so it needs
                // finer hooks before one of them can be picked.
            default:
                return NO_CHAIN;
        }
    }

    private static List<String> chain(String... names) {
        return Collections.unmodifiableList(Arrays.asList(names));
    }
}
