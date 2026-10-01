// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.shader.program;

import io.github.silentcall2598.focalis.render.lifecycle.RenderDrawKind;
import io.github.silentcall2598.focalis.render.lifecycle.RenderStage;
import io.github.silentcall2598.focalis.shader.pack.ShaderMacros;
import io.github.silentcall2598.focalis.shader.pack.ShaderPack;
import io.github.silentcall2598.focalis.shader.pack.StandardMacros;
import io.github.silentcall2598.focalis.shader.routing.ShaderProgramRole;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;

import static io.github.silentcall2598.focalis.shader.program.TestPrograms.FRAGMENT;
import static io.github.silentcall2598.focalis.shader.program.TestPrograms.VERTEX;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldProgramBindingTest {

    private static final ShaderCapabilities EVERYTHING = new ShaderCapabilities(true, true, true);
    // RecordingShaderGl fails to compile any stage whose source contains this.
    private static final String BROKEN = "#version 120\nBROKEN\nvoid main() {}\n";
    private static final String MISSING_INCLUDE = "#version 120\n#include \"/lib/missing.glsl\"\nvoid main() {}\n";
    // A program Minecraft or another mod had active before Focalis did anything.
    private static final int EXTERNAL = 7;

    @TempDir
    Path temp;

    private final RecordingShaderGl shaderGl = new RecordingShaderGl();
    private final RecordingProgramBindingGl gl = new RecordingProgramBindingGl(EXTERNAL);
    private BuiltWorldPrograms programs;
    private WorldProgramBinding binding;

    WorldProgramBindingTest() {
        shaderGl.failCompileWhenSourceContains = "BROKEN";
    }

    // A plain vertex and fragment shader for each program, like "gbuffers_terrain".
    private void load(String... programNames) throws Exception {
        List<String> files = new ArrayList<>();
        for (String program : programNames) {
            files.addAll(Arrays.asList(program + ".vsh", VERTEX, program + ".fsh", FRAGMENT));
        }
        loadFiles(files.toArray(new String[0]));
    }

    private void loadFiles(String... pathsAndTexts) throws Exception {
        ShaderPack pack = TestPrograms.pack(temp, pathsAndTexts);
        PreparedWorldPrograms prepared = PreparedWorldPrograms.prepare(pack, pack.root(),
                StandardMacros.environment(), ShaderMacros.empty());
        programs = BuiltWorldPrograms.build(prepared, new ProgramBuilder(EVERYTHING, shaderGl));
        binding = new WorldProgramBinding(programs, new ScopedProgramBinding(gl));
    }

    private int id(ShaderProgramRole role) {
        ShaderProgram program = programs.forRole(role).program();
        assertNotNull(program, role.toString());
        return program.id();
    }

    private void assertUses(Integer... expected) {
        assertEquals(Arrays.asList(expected), gl.uses);
    }

    @Test
    void readyRoleIsBoundAndThePreviousProgramComesBack() throws Exception {
        load("gbuffers_clouds");
        int clouds = id(ShaderProgramRole.CLOUDS);

        binding.start(RenderStage.CLOUDS, RenderDrawKind.DEFAULT);
        assertEquals(clouds, gl.current);
        binding.end(RenderStage.CLOUDS, RenderDrawKind.DEFAULT);

        assertEquals(EXTERNAL, gl.current);
        assertUses(clouds, EXTERNAL);
        assertTrue(binding.isEmpty());
    }

    @Test
    void terrainLayersAndWaterShareTheTerrainProgram() throws Exception {
        load("gbuffers_terrain");
        int terrain = id(ShaderProgramRole.TERRAIN_SOLID);

        for (RenderDrawKind layer : Arrays.asList(RenderDrawKind.TERRAIN_SOLID, RenderDrawKind.TERRAIN_CUTOUT_MIPPED,
                RenderDrawKind.TERRAIN_CUTOUT)) {
            binding.start(RenderStage.TERRAIN, layer);
            assertEquals(terrain, gl.current, layer.toString());
            binding.end(RenderStage.TERRAIN, layer);
        }
        // Without gbuffers_water, translucent terrain falls back to the terrain program too.
        binding.start(RenderStage.TRANSLUCENT, RenderDrawKind.TERRAIN_TRANSLUCENT);
        assertEquals(terrain, gl.current);
        binding.end(RenderStage.TRANSLUCENT, RenderDrawKind.TERRAIN_TRANSLUCENT);

        assertUses(terrain, EXTERNAL, terrain, EXTERNAL, terrain, EXTERNAL, terrain, EXTERNAL);
        assertEquals(1, programs.builds().size());
    }

    @Test
    void bothEntityPassesBindTheEntitiesProgram() throws Exception {
        load("gbuffers_entities", "gbuffers_textured_lit");
        int entities = id(ShaderProgramRole.ENTITIES);

        binding.start(RenderStage.ENTITIES, RenderDrawKind.ENTITY_PASS_0);
        assertEquals(entities, gl.current);
        binding.end(RenderStage.ENTITIES, RenderDrawKind.ENTITY_PASS_0);
        binding.start(RenderStage.ENTITIES, RenderDrawKind.ENTITY_PASS_1);
        assertEquals(entities, gl.current);
        binding.end(RenderStage.ENTITIES, RenderDrawKind.ENTITY_PASS_1);

        assertUses(entities, EXTERNAL, entities, EXTERNAL);
    }

    @Test
    void particleKindsBindTheirOwnPrograms() throws Exception {
        load("gbuffers_textured_lit", "gbuffers_textured");
        int lit = id(ShaderProgramRole.PARTICLES_LIT);
        int normal = id(ShaderProgramRole.PARTICLES_NORMAL);
        assertNotEquals(lit, normal);

        binding.start(RenderStage.PARTICLES, RenderDrawKind.PARTICLES_LIT);
        assertEquals(lit, gl.current);
        binding.end(RenderStage.PARTICLES, RenderDrawKind.PARTICLES_LIT);
        binding.start(RenderStage.PARTICLES, RenderDrawKind.PARTICLES_NORMAL);
        assertEquals(normal, gl.current);
        binding.end(RenderStage.PARTICLES, RenderDrawKind.PARTICLES_NORMAL);

        assertUses(lit, EXTERNAL, normal, EXTERNAL);
    }

    @Test
    void weatherSharesTheLitProgramWithoutItsOwn() throws Exception {
        load("gbuffers_textured_lit");
        int lit = id(ShaderProgramRole.PARTICLES_LIT);

        binding.start(RenderStage.WEATHER, RenderDrawKind.DEFAULT);
        assertEquals(lit, gl.current);
        binding.end(RenderStage.WEATHER, RenderDrawKind.DEFAULT);

        assertUses(lit, EXTERNAL);
    }

    @Test
    void sunAndMoonNestInsideTheSkyAndPutItsProgramBack() throws Exception {
        load("gbuffers_skybasic", "gbuffers_skytextured");
        int basic = id(ShaderProgramRole.SKY_BASIC);
        int textured = id(ShaderProgramRole.SKY_TEXTURED);

        binding.start(RenderStage.SKY, RenderDrawKind.SKY_BASIC);
        assertEquals(basic, gl.current);
        for (int draw = 0; draw < 2; draw++) {
            binding.start(RenderStage.SKY, RenderDrawKind.SKY_TEXTURED);
            assertEquals(textured, gl.current);
            binding.end(RenderStage.SKY, RenderDrawKind.SKY_TEXTURED);
            assertEquals(basic, gl.current);
        }
        binding.end(RenderStage.SKY, RenderDrawKind.SKY_BASIC);

        assertUses(basic, textured, basic, textured, basic, EXTERNAL);
        assertTrue(binding.isEmpty());
    }

    @Test
    void nestedScopeOfTheSameProgramDoesNotBindAgain() throws Exception {
        // Both sky roles fall back to gbuffers_basic.
        load("gbuffers_basic");
        int basic = id(ShaderProgramRole.SKY_BASIC);
        assertEquals(basic, id(ShaderProgramRole.SKY_TEXTURED));

        binding.start(RenderStage.SKY, RenderDrawKind.SKY_BASIC);
        binding.start(RenderStage.SKY, RenderDrawKind.SKY_TEXTURED);
        binding.end(RenderStage.SKY, RenderDrawKind.SKY_TEXTURED);
        assertEquals(basic, gl.current);
        binding.end(RenderStage.SKY, RenderDrawKind.SKY_BASIC);

        assertUses(basic, EXTERNAL);
    }

    @Test
    void missingRoleOpensAScopeThatBindsNothing() throws Exception {
        load("gbuffers_terrain");
        assertNull(binding.program(ShaderProgramRole.CLOUDS));

        binding.start(RenderStage.CLOUDS, RenderDrawKind.DEFAULT);
        assertFalse(binding.isEmpty());
        binding.end(RenderStage.CLOUDS, RenderDrawKind.DEFAULT);

        assertUses();
        assertEquals(EXTERNAL, gl.current);
    }

    @Test
    void missingRoleInsideABoundScopeKeepsTheOuterProgram() throws Exception {
        load("gbuffers_skybasic");
        int basic = id(ShaderProgramRole.SKY_BASIC);
        // gbuffers_skybasic isn't in the sun and moon's chain, so they have no program.
        assertNull(binding.program(ShaderProgramRole.SKY_TEXTURED));

        binding.start(RenderStage.SKY, RenderDrawKind.SKY_BASIC);
        binding.start(RenderStage.SKY, RenderDrawKind.SKY_TEXTURED);
        assertEquals(basic, gl.current);
        binding.end(RenderStage.SKY, RenderDrawKind.SKY_TEXTURED);
        binding.end(RenderStage.SKY, RenderDrawKind.SKY_BASIC);

        assertUses(basic, EXTERNAL);
    }

    @Test
    void programThatFailedToPrepareIsNotBound() throws Exception {
        loadFiles("gbuffers_terrain.vsh", VERTEX, "gbuffers_terrain.fsh", MISSING_INCLUDE);
        assertNotNull(programs.forRole(ShaderProgramRole.TERRAIN_SOLID).prepared().problem());

        binding.start(RenderStage.TERRAIN, RenderDrawKind.TERRAIN_SOLID);
        binding.end(RenderStage.TERRAIN, RenderDrawKind.TERRAIN_SOLID);

        assertUses();
        assertEquals(0, shaderGl.createdPrograms);
    }

    @Test
    void programThatFailedToBuildIsNotBoundAndDoesNotFallBack() throws Exception {
        loadFiles("gbuffers_terrain.vsh", VERTEX, "gbuffers_terrain.fsh", FRAGMENT,
                "gbuffers_water.vsh", VERTEX, "gbuffers_water.fsh", BROKEN);
        assertNotNull(programs.forRole(ShaderProgramRole.TERRAIN_TRANSLUCENT).failure());
        int terrain = id(ShaderProgramRole.TERRAIN_SOLID);

        binding.start(RenderStage.TRANSLUCENT, RenderDrawKind.TERRAIN_TRANSLUCENT);
        assertEquals(EXTERNAL, gl.current);
        binding.end(RenderStage.TRANSLUCENT, RenderDrawKind.TERRAIN_TRANSLUCENT);
        // The other program built fine and still works.
        binding.start(RenderStage.TERRAIN, RenderDrawKind.TERRAIN_SOLID);
        assertEquals(terrain, gl.current);
        binding.end(RenderStage.TERRAIN, RenderDrawKind.TERRAIN_SOLID);

        assertUses(terrain, EXTERNAL);
    }

    @Test
    void unclassifiedDrawKindsBindNothing() throws Exception {
        load("gbuffers_basic", "gbuffers_terrain");

        // A Forge sky renderer, the End sky, or a terrain layer some mod added.
        binding.start(RenderStage.SKY, RenderDrawKind.DEFAULT);
        binding.end(RenderStage.SKY, RenderDrawKind.DEFAULT);
        binding.start(RenderStage.TERRAIN, RenderDrawKind.DEFAULT);
        binding.end(RenderStage.TERRAIN, RenderDrawKind.DEFAULT);

        assertUses();
        assertNull(binding.program(ShaderProgramRole.UNCLASSIFIED));
        assertNull(binding.program(ShaderProgramRole.NONE));
    }

    @Test
    void deletedProgramsAreNotBound() throws Exception {
        load("gbuffers_terrain");
        programs.delete();

        binding.start(RenderStage.TERRAIN, RenderDrawKind.TERRAIN_SOLID);
        binding.end(RenderStage.TERRAIN, RenderDrawKind.TERRAIN_SOLID);

        assertUses();
    }

    @Test
    void mismatchedEndUnwindsEveryScope() throws Exception {
        load("gbuffers_skybasic", "gbuffers_skytextured");
        int basic = id(ShaderProgramRole.SKY_BASIC);
        int textured = id(ShaderProgramRole.SKY_TEXTURED);
        binding.start(RenderStage.SKY, RenderDrawKind.SKY_BASIC);
        binding.start(RenderStage.SKY, RenderDrawKind.SKY_TEXTURED);

        assertThrows(IllegalStateException.class,
                () -> binding.end(RenderStage.SKY, RenderDrawKind.SKY_BASIC));

        assertTrue(binding.isEmpty());
        assertEquals(EXTERNAL, gl.current);
        assertUses(basic, textured, basic, EXTERNAL);
    }

    @Test
    void handIsNeverBound() throws Exception {
        load("gbuffers_hand", "gbuffers_terrain");
        int terrain = id(ShaderProgramRole.TERRAIN_SOLID);
        binding.start(RenderStage.TERRAIN, RenderDrawKind.TERRAIN_SOLID);

        assertThrows(IllegalArgumentException.class, () -> binding.start(RenderStage.HAND, RenderDrawKind.DEFAULT));

        // The open scope was unwound before the throw.
        assertTrue(binding.isEmpty());
        assertEquals(EXTERNAL, gl.current);
        assertUses(terrain, EXTERNAL);
    }

    @Test
    void entityOutlinesStepAsideInsideEntities() throws Exception {
        load("gbuffers_entities");
        int entities = id(ShaderProgramRole.ENTITIES);
        binding.start(RenderStage.ENTITIES, RenderDrawKind.ENTITY_PASS_0);

        binding.suspend(RenderStage.ENTITY_OUTLINES, RenderDrawKind.DEFAULT);
        assertEquals(EXTERNAL, gl.current);
        // Vanilla's outline shader leaves no program bound.
        gl.current = 0;
        binding.resume(RenderStage.ENTITY_OUTLINES, RenderDrawKind.DEFAULT);
        assertEquals(entities, gl.current);
        binding.end(RenderStage.ENTITIES, RenderDrawKind.ENTITY_PASS_0);

        assertEquals(EXTERNAL, gl.current);
        assertUses(entities, EXTERNAL, entities, EXTERNAL);
        assertTrue(binding.isEmpty());
    }

    @Test
    void reassertBindsTheStageProgramAfterARendererChangedIt() throws Exception {
        load("gbuffers_entities");
        int entities = id(ShaderProgramRole.ENTITIES);
        binding.start(RenderStage.ENTITIES, RenderDrawKind.ENTITY_PASS_0);
        gl.current = 0;

        assertTrue(binding.reassert());
        binding.end(RenderStage.ENTITIES, RenderDrawKind.ENTITY_PASS_0);

        assertEquals(EXTERNAL, gl.current);
        assertUses(entities, entities, EXTERNAL);
    }

    @Test
    void reassertNeverClaimsTheProgramForARoleWithoutOne() throws Exception {
        // No entities program, and a build failure for the clouds.
        loadFiles("gbuffers_terrain.vsh", VERTEX, "gbuffers_terrain.fsh", FRAGMENT,
                "gbuffers_clouds.vsh", VERTEX, "gbuffers_clouds.fsh", BROKEN);
        binding.start(RenderStage.ENTITIES, RenderDrawKind.ENTITY_PASS_0);
        gl.current = 0;
        assertFalse(binding.reassert());
        binding.end(RenderStage.ENTITIES, RenderDrawKind.ENTITY_PASS_0);
        binding.start(RenderStage.CLOUDS, RenderDrawKind.DEFAULT);
        gl.current = 55;
        assertFalse(binding.reassert());
        binding.end(RenderStage.CLOUDS, RenderDrawKind.DEFAULT);
        // An unclassified sky has no role either.
        binding.start(RenderStage.SKY, RenderDrawKind.DEFAULT);
        gl.current = 66;
        assertFalse(binding.reassert());
        binding.end(RenderStage.SKY, RenderDrawKind.DEFAULT);

        assertEquals(66, gl.current);
        assertUses();
    }

    @Test
    void onlyVanillaProgramStagesSuspend() throws Exception {
        load("gbuffers_entities");
        int entities = id(ShaderProgramRole.ENTITIES);
        binding.start(RenderStage.ENTITIES, RenderDrawKind.ENTITY_PASS_0);

        assertThrows(IllegalArgumentException.class,
                () -> binding.suspend(RenderStage.ENTITIES, RenderDrawKind.ENTITY_PASS_0));
        assertThrows(IllegalArgumentException.class,
                () -> binding.resume(RenderStage.SKY, RenderDrawKind.SKY_BASIC));
        assertThrows(IllegalArgumentException.class,
                () -> binding.start(RenderStage.ENTITY_OUTLINES, RenderDrawKind.DEFAULT));

        assertTrue(binding.isEmpty());
        assertFalse(binding.isSuspended());
        assertEquals(EXTERNAL, gl.current);
        assertUses(entities, EXTERNAL);
        assertEquals(EnumSet.of(RenderStage.ENTITY_OUTLINES), WorldProgramBinding.VANILLA_PROGRAM_STAGES);
    }

    @Test
    void onlyStagesInsideTheWorldPassAreBound() {
        assertEquals(EnumSet.of(RenderStage.SKY, RenderStage.TERRAIN, RenderStage.ENTITIES, RenderStage.PARTICLES,
                RenderStage.TRANSLUCENT, RenderStage.WEATHER, RenderStage.CLOUDS), WorldProgramBinding.STAGES);
    }
}
