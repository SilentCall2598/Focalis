// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.shader.routing;

import io.github.silentcall2598.focalis.shader.pack.ProgramDirectory;
import io.github.silentcall2598.focalis.shader.pack.ShaderPack;
import io.github.silentcall2598.focalis.shader.pack.ShaderPackException;
import io.github.silentcall2598.focalis.shader.pack.ShaderPackLoader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShaderProgramResolverTest {

    private static final String VERTEX = "#version 120\nvoid main() { gl_Position = ftransform(); }\n";
    private static final String FRAGMENT = "#version 120\nvoid main() { gl_FragColor = vec4(1.0); }\n";

    @TempDir
    Path temp;

    private int packs;

    // A pack with a vertex and fragment shader for each program, like "gbuffers_basic" or "world-1/gbuffers_basic".
    private ShaderPack pack(String... programs) throws IOException, ShaderPackException {
        Path pack = temp.resolve("pack" + packs++);
        for (String program : programs) {
            write(pack, program + ".vsh", VERTEX);
            write(pack, program + ".fsh", FRAGMENT);
        }
        return ShaderPackLoader.load(pack);
    }

    private static void write(Path pack, String file, String text) throws IOException {
        Path target = pack.resolve("shaders").resolve(file);
        Files.createDirectories(target.getParent());
        Files.write(target, text.getBytes(StandardCharsets.UTF_8));
    }

    private static void assertResolved(ProgramDirectory directory, ShaderProgramRole role, String name, int depth) {
        ProgramResolution resolution = ShaderProgramResolver.resolve(directory, role);
        assertEquals(ResolutionState.RESOLVED, resolution.state(), role.toString());
        assertEquals(name, resolution.selectedName(), role.toString());
        assertEquals(depth, resolution.fallbackDepth(), role.toString());
        assertSame(directory.find(name), resolution.program(), role.toString());
    }

    private static void assertMissing(ProgramDirectory directory, ShaderProgramRole role) {
        ProgramResolution resolution = ShaderProgramResolver.resolve(directory, role);
        assertEquals(ResolutionState.MISSING, resolution.state(), role.toString());
        assertNull(resolution.program());
        assertNull(resolution.selectedName());
        assertEquals(-1, resolution.fallbackDepth());
    }

    @Test
    void mostSpecificEntityProgramWins() throws Exception {
        ShaderPack pack = pack("gbuffers_entities", "gbuffers_textured_lit", "gbuffers_textured", "gbuffers_basic");

        assertResolved(pack.root(), ShaderProgramRole.ENTITIES, "gbuffers_entities", 0);
    }

    @Test
    void entityFallsBackStepByStep() throws Exception {
        assertResolved(pack("gbuffers_textured_lit", "gbuffers_textured", "gbuffers_basic").root(),
                ShaderProgramRole.ENTITIES, "gbuffers_textured_lit", 1);
        assertResolved(pack("gbuffers_textured", "gbuffers_basic").root(),
                ShaderProgramRole.ENTITIES, "gbuffers_textured", 2);
        assertResolved(pack("gbuffers_basic").root(), ShaderProgramRole.ENTITIES, "gbuffers_basic", 3);
    }

    @Test
    void missingChainIsMissing() throws Exception {
        ShaderPack pack = pack("gbuffers_skybasic", "composite", "final");

        assertMissing(pack.root(), ShaderProgramRole.ENTITIES);
        assertEquals(ShaderProgramResolver.candidates(ShaderProgramRole.ENTITIES),
                ShaderProgramResolver.resolve(pack.root(), ShaderProgramRole.ENTITIES).candidates());
    }

    @Test
    void allTerrainLayersUseTheLegacyTerrainProgram() throws Exception {
        ProgramDirectory root = pack("gbuffers_terrain", "gbuffers_textured_lit", "gbuffers_basic").root();

        assertResolved(root, ShaderProgramRole.TERRAIN_SOLID, "gbuffers_terrain", 0);
        assertResolved(root, ShaderProgramRole.TERRAIN_CUTOUT_MIPPED, "gbuffers_terrain", 0);
        assertResolved(root, ShaderProgramRole.TERRAIN_CUTOUT, "gbuffers_terrain", 0);
    }

    // Those names were documented as unused in the 1.12.2 era, so they must never be picked.
    @Test
    void unusedPerLayerTerrainProgramsAreIgnored() throws Exception {
        ProgramDirectory root = pack("gbuffers_terrain_solid", "gbuffers_terrain_cutout_mip",
                "gbuffers_terrain_cutout").root();

        assertMissing(root, ShaderProgramRole.TERRAIN_SOLID);
        assertMissing(root, ShaderProgramRole.TERRAIN_CUTOUT_MIPPED);
        assertMissing(root, ShaderProgramRole.TERRAIN_CUTOUT);
    }

    @Test
    void translucentTerrainFallsBackThroughTerrain() throws Exception {
        assertResolved(pack("gbuffers_water", "gbuffers_terrain", "gbuffers_basic").root(),
                ShaderProgramRole.TERRAIN_TRANSLUCENT, "gbuffers_water", 0);
        assertResolved(pack("gbuffers_terrain", "gbuffers_textured_lit", "gbuffers_basic").root(),
                ShaderProgramRole.TERRAIN_TRANSLUCENT, "gbuffers_terrain", 1);
        assertResolved(pack("gbuffers_textured_lit", "gbuffers_basic").root(),
                ShaderProgramRole.TERRAIN_TRANSLUCENT, "gbuffers_textured_lit", 2);
        assertResolved(pack("gbuffers_basic").root(), ShaderProgramRole.TERRAIN_TRANSLUCENT, "gbuffers_basic", 4);
    }

    @Test
    void particlesUseTexturedPrograms() throws Exception {
        ProgramDirectory full = pack("gbuffers_textured_lit", "gbuffers_textured", "gbuffers_basic").root();
        assertResolved(full, ShaderProgramRole.PARTICLES_LIT, "gbuffers_textured_lit", 0);
        assertResolved(full, ShaderProgramRole.PARTICLES_NORMAL, "gbuffers_textured", 0);

        ProgramDirectory basic = pack("gbuffers_basic").root();
        assertResolved(basic, ShaderProgramRole.PARTICLES_LIT, "gbuffers_basic", 2);
        assertResolved(basic, ShaderProgramRole.PARTICLES_NORMAL, "gbuffers_basic", 1);
    }

    @Test
    void weatherCloudsAndHandHaveTheirOwnProgramsFirst() throws Exception {
        ProgramDirectory own = pack("gbuffers_weather", "gbuffers_clouds", "gbuffers_hand", "gbuffers_textured_lit",
                "gbuffers_textured", "gbuffers_basic").root();
        assertResolved(own, ShaderProgramRole.WEATHER, "gbuffers_weather", 0);
        assertResolved(own, ShaderProgramRole.CLOUDS, "gbuffers_clouds", 0);
        assertResolved(own, ShaderProgramRole.HAND, "gbuffers_hand", 0);

        ProgramDirectory shared = pack("gbuffers_textured_lit", "gbuffers_textured", "gbuffers_basic").root();
        assertResolved(shared, ShaderProgramRole.WEATHER, "gbuffers_textured_lit", 1);
        assertResolved(shared, ShaderProgramRole.HAND, "gbuffers_textured_lit", 1);
        // Clouds skip the lit program.
        assertResolved(shared, ShaderProgramRole.CLOUDS, "gbuffers_textured", 1);
        assertResolved(pack("gbuffers_basic").root(), ShaderProgramRole.CLOUDS, "gbuffers_basic", 2);
    }

    @Test
    void basicSkyFallsBackToBasic() throws Exception {
        assertResolved(pack("gbuffers_skybasic", "gbuffers_basic").root(), ShaderProgramRole.SKY_BASIC,
                "gbuffers_skybasic", 0);
        assertResolved(pack("gbuffers_textured", "gbuffers_basic").root(), ShaderProgramRole.SKY_BASIC,
                "gbuffers_basic", 1);
    }

    @Test
    void texturedSkyFallsBackThroughTextured() throws Exception {
        assertResolved(pack("gbuffers_skytextured", "gbuffers_textured", "gbuffers_basic").root(),
                ShaderProgramRole.SKY_TEXTURED, "gbuffers_skytextured", 0);
        assertResolved(pack("gbuffers_textured", "gbuffers_basic").root(), ShaderProgramRole.SKY_TEXTURED,
                "gbuffers_textured", 1);
        assertResolved(pack("gbuffers_basic").root(), ShaderProgramRole.SKY_TEXTURED, "gbuffers_basic", 2);
    }

    // Each sky program only draws its own part of the sky.
    @Test
    void skyProgramsDontStandInForEachOther() throws Exception {
        assertMissing(pack("gbuffers_skytextured").root(), ShaderProgramRole.SKY_BASIC);
        assertMissing(pack("gbuffers_skybasic").root(), ShaderProgramRole.SKY_TEXTURED);
    }

    @Test
    void rolesWithoutAPackProgramAreNotResolved() throws Exception {
        ProgramDirectory root = pack("gbuffers_skybasic", "gbuffers_skytextured", "gbuffers_textured",
                "gbuffers_basic").root();

        assertEquals(ResolutionState.NOT_APPLICABLE,
                ShaderProgramResolver.resolve(root, ShaderProgramRole.NONE).state());
        // Like a sky Focalis doesn't recognize, even with both sky programs there.
        ProgramResolution unclassified = ShaderProgramResolver.resolve(root, ShaderProgramRole.UNCLASSIFIED);
        assertEquals(ResolutionState.NEEDS_MORE_CONTEXT, unclassified.state());
        assertNull(unclassified.program());
        assertEquals(-1, unclassified.fallbackDepth());
        assertTrue(unclassified.candidates().isEmpty());
    }

    @Test
    void presentButBrokenProgramStillWins() throws Exception {
        Path pack = temp.resolve("broken");
        write(pack, "gbuffers_entities.vsh", VERTEX);
        write(pack, "gbuffers_entities.fsh", "#version 120\n#include \"/lib/missing.glsl\"\nvoid main() {}\n");
        write(pack, "gbuffers_textured_lit.vsh", VERTEX);
        write(pack, "gbuffers_textured_lit.fsh", FRAGMENT);
        ProgramDirectory root = ShaderPackLoader.load(pack).root();
        assertNotNull(root.find("gbuffers_entities").problem());

        assertResolved(root, ShaderProgramRole.ENTITIES, "gbuffers_entities", 0);
    }

    @Test
    void dimensionFolderNeverFallsBackToTheRoot() throws Exception {
        ShaderPack pack = pack("gbuffers_entities", "gbuffers_textured_lit", "gbuffers_basic",
                "world-1/gbuffers_basic", "world1/gbuffers_skybasic");

        assertResolved(pack.root(), ShaderProgramRole.ENTITIES, "gbuffers_entities", 0);
        ProgramDirectory nether = pack.dimensionDirectories().get("world-1");
        assertResolved(nether, ShaderProgramRole.ENTITIES, "gbuffers_basic", 3);
        assertEquals("world-1", ShaderProgramResolver.resolve(nether, ShaderProgramRole.ENTITIES).program()
                .directory());
        assertMissing(pack.dimensionDirectories().get("world1"), ShaderProgramRole.ENTITIES);
    }

    // A small pack like many of the time had, where most roles land on a handful of programs.
    @Test
    void minimalLegacyPackCollapsesAsExpected() throws Exception {
        ProgramDirectory root = pack("gbuffers_basic", "gbuffers_textured", "gbuffers_textured_lit",
                "gbuffers_terrain").root();

        assertResolved(root, ShaderProgramRole.TERRAIN_SOLID, "gbuffers_terrain", 0);
        assertResolved(root, ShaderProgramRole.TERRAIN_TRANSLUCENT, "gbuffers_terrain", 1);
        assertResolved(root, ShaderProgramRole.ENTITIES, "gbuffers_textured_lit", 1);
        assertResolved(root, ShaderProgramRole.HAND, "gbuffers_textured_lit", 1);
        assertResolved(root, ShaderProgramRole.WEATHER, "gbuffers_textured_lit", 1);
        assertResolved(root, ShaderProgramRole.PARTICLES_LIT, "gbuffers_textured_lit", 0);
        assertResolved(root, ShaderProgramRole.PARTICLES_NORMAL, "gbuffers_textured", 0);
        assertResolved(root, ShaderProgramRole.CLOUDS, "gbuffers_textured", 1);
        assertResolved(root, ShaderProgramRole.SKY_BASIC, "gbuffers_basic", 1);
        assertResolved(root, ShaderProgramRole.SKY_TEXTURED, "gbuffers_textured", 1);
    }

    @Test
    void neverReturnsNull() throws Exception {
        ProgramDirectory root = pack("gbuffers_basic").root();
        for (ShaderProgramRole role : ShaderProgramRole.values()) {
            ProgramResolution resolution = ShaderProgramResolver.resolve(root, role);
            assertNotNull(resolution, role.toString());
            assertNotNull(resolution.state(), role.toString());
            assertSame(role, resolution.role());
        }
        assertEquals(ResolutionState.NEEDS_MORE_CONTEXT, ShaderProgramResolver.resolve(root, null).state());
    }

    @Test
    void candidateListsAreSharedAndReadOnly() {
        assertSame(ShaderProgramResolver.candidates(ShaderProgramRole.TERRAIN_SOLID),
                ShaderProgramResolver.candidates(ShaderProgramRole.TERRAIN_CUTOUT));
        assertThrows(UnsupportedOperationException.class,
                () -> ShaderProgramResolver.candidates(ShaderProgramRole.ENTITIES).add("gbuffers_particles"));
    }
}
