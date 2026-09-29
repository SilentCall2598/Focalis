// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.shader.program;

import io.github.silentcall2598.focalis.shader.pack.ProgramDirectory;
import io.github.silentcall2598.focalis.shader.pack.ProgramStage;
import io.github.silentcall2598.focalis.shader.pack.ShaderMacros;
import io.github.silentcall2598.focalis.shader.pack.ShaderPack;
import io.github.silentcall2598.focalis.shader.pack.StandardMacros;
import io.github.silentcall2598.focalis.shader.routing.ResolutionState;
import io.github.silentcall2598.focalis.shader.routing.ShaderProgramResolver;
import io.github.silentcall2598.focalis.shader.routing.ShaderProgramRole;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static io.github.silentcall2598.focalis.shader.program.TestPrograms.FRAGMENT;
import static io.github.silentcall2598.focalis.shader.program.TestPrograms.VERTEX;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PreparedWorldProgramsTest {

    private static final String BROKEN_FRAGMENT = "#version 120\n#include \"/lib/missing.glsl\"\nvoid main() {}\n";

    @TempDir
    Path temp;

    // A pack with a vertex and fragment shader for each program, like "gbuffers_basic" or "world-1/gbuffers_basic".
    private ShaderPack pack(String... programs) throws Exception {
        List<String> files = new ArrayList<>();
        for (String program : programs) {
            files.addAll(Arrays.asList(program + ".vsh", VERTEX, program + ".fsh", FRAGMENT));
        }
        return TestPrograms.pack(temp, files.toArray(new String[0]));
    }

    private static PreparedWorldPrograms prepare(ShaderPack pack, ProgramDirectory directory) {
        return PreparedWorldPrograms.prepare(pack, directory, StandardMacros.environment(), ShaderMacros.empty());
    }

    private static PreparedWorldPrograms prepareRoot(ShaderPack pack) {
        return prepare(pack, pack.root());
    }

    private static PreparedProgram program(PreparedWorldPrograms programs, ShaderProgramRole role) {
        PreparedWorldPrograms.Entry entry = programs.forRole(role);
        assertTrue(entry.ready(), role + " should be ready, is " + entry);
        return entry.program();
    }

    private static void assertNothingPrepared(PreparedWorldPrograms.Entry entry, ResolutionState state) {
        assertEquals(state, entry.resolution().state(), entry.role().toString());
        assertNull(entry.program(), entry.role().toString());
        assertNull(entry.problem(), entry.role().toString());
        assertFalse(entry.ready(), entry.role().toString());
    }

    private static List<String> names(List<PreparedProgram> programs) {
        List<String> names = new ArrayList<>();
        for (PreparedProgram program : programs) {
            names.add(program.name());
        }
        return names;
    }

    @Test
    void everyRoleHasAnEntry() throws Exception {
        PreparedWorldPrograms programs = prepareRoot(pack("gbuffers_basic"));

        for (ShaderProgramRole role : ShaderProgramRole.values()) {
            PreparedWorldPrograms.Entry entry = programs.forRole(role);
            assertNotNull(entry, role.toString());
            assertSame(role, entry.role());
            assertNotNull(entry.resolution(), role.toString());
            assertSame(role, entry.resolution().role());
            assertSame(entry, programs.entries().get(role));
        }
        assertEquals(ShaderProgramRole.values().length, programs.entries().size());
        assertThrows(NullPointerException.class, () -> programs.forRole(null));
    }

    // Every drawable role falls back to gbuffers_basic in the end, so a pack with only that has one program.
    @Test
    void oneBasicProgramIsSharedByEveryDrawableRole() throws Exception {
        PreparedWorldPrograms programs = prepareRoot(pack("gbuffers_basic"));

        PreparedProgram basic = programs.uniquePrograms().get(0);
        assertEquals(1, programs.uniquePrograms().size());
        assertEquals("gbuffers_basic", basic.name());
        int shared = 0;
        for (ShaderProgramRole role : ShaderProgramRole.values()) {
            if (!ShaderProgramResolver.candidates(role).isEmpty()) {
                assertEquals(ResolutionState.RESOLVED, programs.forRole(role).resolution().state(), role.toString());
                assertSame(basic, program(programs, role));
                shared++;
            }
        }
        assertEquals(ShaderProgramRole.values().length - 2, shared);
        assertNothingPrepared(programs.forRole(ShaderProgramRole.NONE), ResolutionState.NOT_APPLICABLE);
        assertNothingPrepared(programs.forRole(ShaderProgramRole.UNCLASSIFIED), ResolutionState.NEEDS_MORE_CONTEXT);
    }

    @Test
    void terrainLayersShareOneProgram() throws Exception {
        PreparedWorldPrograms programs = prepareRoot(pack("gbuffers_terrain", "gbuffers_textured_lit",
                "gbuffers_textured", "gbuffers_basic"));

        PreparedProgram terrain = program(programs, ShaderProgramRole.TERRAIN_SOLID);
        assertEquals("gbuffers_terrain", terrain.name());
        assertSame(terrain, program(programs, ShaderProgramRole.TERRAIN_CUTOUT_MIPPED));
        assertSame(terrain, program(programs, ShaderProgramRole.TERRAIN_CUTOUT));
        // Translucent terrain falls back to gbuffers_terrain without gbuffers_water.
        assertSame(terrain, program(programs, ShaderProgramRole.TERRAIN_TRANSLUCENT));
    }

    @Test
    void distinctProgramsStayDistinct() throws Exception {
        PreparedWorldPrograms programs = prepareRoot(pack("gbuffers_terrain", "gbuffers_entities",
                "gbuffers_textured_lit", "gbuffers_textured", "gbuffers_basic"));

        PreparedProgram entities = program(programs, ShaderProgramRole.ENTITIES);
        assertEquals("gbuffers_entities", entities.name());
        assertNotSame(program(programs, ShaderProgramRole.TERRAIN_SOLID), entities);
        assertNotSame(program(programs, ShaderProgramRole.HAND), entities);
    }

    @Test
    void rolesFallingBackToTheSameProgramShareIt() throws Exception {
        PreparedWorldPrograms programs = prepareRoot(pack("gbuffers_textured_lit", "gbuffers_textured",
                "gbuffers_basic"));

        PreparedProgram lit = program(programs, ShaderProgramRole.ENTITIES);
        assertEquals("gbuffers_textured_lit", lit.name());
        assertEquals(1, programs.forRole(ShaderProgramRole.ENTITIES).resolution().fallbackDepth());
        for (ShaderProgramRole role : Arrays.asList(ShaderProgramRole.TERRAIN_SOLID, ShaderProgramRole.WEATHER,
                ShaderProgramRole.HAND, ShaderProgramRole.PARTICLES_LIT)) {
            assertSame(lit, program(programs, role), role.toString());
        }
        assertEquals(Arrays.asList("gbuffers_basic", "gbuffers_textured", "gbuffers_textured_lit"),
                names(programs.uniquePrograms()));
    }

    // gbuffers_entities won resolution, so its failure is the answer instead of quietly using a lesser program.
    @Test
    void brokenMostSpecificProgramDoesNotFallThrough() throws Exception {
        ShaderPack pack = TestPrograms.pack(temp, "gbuffers_entities.vsh", VERTEX,
                "gbuffers_entities.fsh", BROKEN_FRAGMENT, "gbuffers_textured_lit.vsh", VERTEX,
                "gbuffers_textured_lit.fsh", FRAGMENT, "gbuffers_textured.vsh", VERTEX, "gbuffers_textured.fsh",
                FRAGMENT, "gbuffers_basic.vsh", VERTEX, "gbuffers_basic.fsh", FRAGMENT);
        assertNotNull(pack.root().find("gbuffers_entities").problem());

        PreparedWorldPrograms programs = prepareRoot(pack);

        PreparedWorldPrograms.Entry entities = programs.forRole(ShaderProgramRole.ENTITIES);
        assertEquals(ResolutionState.RESOLVED, entities.resolution().state());
        assertEquals("gbuffers_entities", entities.resolution().selectedName());
        assertNull(entities.program());
        assertFalse(entities.ready());
        assertNotNull(entities.problem());
        assertTrue(entities.problem().contains("missing.glsl"), entities.problem());
        assertEquals("gbuffers_textured_lit", program(programs, ShaderProgramRole.HAND).name());
        assertEquals("gbuffers_textured", program(programs, ShaderProgramRole.CLOUDS).name());
        assertFalse(names(programs.uniquePrograms()).contains("gbuffers_entities"));
    }

    // A second attempt would throw a new exception with a new message, so one shared message means one attempt.
    @Test
    void sharedBrokenProgramFailsOnceForEveryRole() throws Exception {
        PreparedWorldPrograms programs = prepareRoot(TestPrograms.pack(temp, "gbuffers_basic.vsh", VERTEX,
                "gbuffers_basic.fsh", BROKEN_FRAGMENT));

        String problem = programs.forRole(ShaderProgramRole.SKY_BASIC).problem();
        assertNotNull(problem);
        for (ShaderProgramRole role : ShaderProgramRole.values()) {
            PreparedWorldPrograms.Entry entry = programs.forRole(role);
            if (entry.resolution().state() == ResolutionState.RESOLVED) {
                assertEquals("gbuffers_basic", entry.resolution().selectedName());
                assertSame(problem, entry.problem(), role.toString());
                assertNull(entry.program());
            }
        }
        assertTrue(programs.uniquePrograms().isEmpty());
    }

    @Test
    void missingRoleHasNothingToPrepare() throws Exception {
        PreparedWorldPrograms programs = prepareRoot(pack("gbuffers_skybasic", "composite", "final"));

        assertNothingPrepared(programs.forRole(ShaderProgramRole.ENTITIES), ResolutionState.MISSING);
        assertNothingPrepared(programs.forRole(ShaderProgramRole.TERRAIN_SOLID), ResolutionState.MISSING);
        assertEquals("gbuffers_skybasic", program(programs, ShaderProgramRole.SKY_BASIC).name());
    }

    @Test
    void noneAndUnclassifiedAreNeverPrepared() throws Exception {
        PreparedWorldPrograms programs = prepareRoot(pack("gbuffers_basic", "gbuffers_textured"));

        assertNothingPrepared(programs.forRole(ShaderProgramRole.NONE), ResolutionState.NOT_APPLICABLE);
        assertNothingPrepared(programs.forRole(ShaderProgramRole.UNCLASSIFIED), ResolutionState.NEEDS_MORE_CONTEXT);
    }

    @Test
    void skyRolesUseTheirOwnChains() throws Exception {
        PreparedWorldPrograms own = prepareRoot(pack("gbuffers_skybasic", "gbuffers_skytextured",
                "gbuffers_textured", "gbuffers_basic"));
        assertEquals("gbuffers_skybasic", program(own, ShaderProgramRole.SKY_BASIC).name());
        assertEquals("gbuffers_skytextured", program(own, ShaderProgramRole.SKY_TEXTURED).name());

        PreparedWorldPrograms shared = prepareRoot(pack("gbuffers_textured", "gbuffers_basic"));
        assertEquals("gbuffers_basic", program(shared, ShaderProgramRole.SKY_BASIC).name());
        assertEquals("gbuffers_textured", program(shared, ShaderProgramRole.SKY_TEXTURED).name());
        assertSame(program(shared, ShaderProgramRole.SKY_TEXTURED),
                program(shared, ShaderProgramRole.PARTICLES_NORMAL));
    }

    @Test
    void onlyTheGivenDirectoryIsUsed() throws Exception {
        ShaderPack pack = pack("gbuffers_basic", "gbuffers_entities", "world-1/gbuffers_textured");
        ProgramDirectory nether = pack.dimensionDirectories().get("world-1");

        PreparedWorldPrograms root = prepare(pack, pack.root());
        PreparedWorldPrograms dimension = prepare(pack, nether);

        for (PreparedWorldPrograms.Entry entry : root.entries().values()) {
            if (entry.resolution().program() != null) {
                assertEquals("", entry.resolution().program().directory(), entry.role().toString());
                assertSame(pack.root().find(entry.resolution().selectedName()), entry.resolution().program());
            }
        }
        for (PreparedWorldPrograms.Entry entry : dimension.entries().values()) {
            if (entry.resolution().program() != null) {
                assertEquals("world-1", entry.resolution().program().directory(), entry.role().toString());
                assertSame(nether.find(entry.resolution().selectedName()), entry.resolution().program());
            }
        }
        assertEquals("gbuffers_entities", program(root, ShaderProgramRole.ENTITIES).name());
        assertEquals("world-1/gbuffers_textured", program(dimension, ShaderProgramRole.ENTITIES).name());
        // The root has gbuffers_basic, but world-1 doesn't fall back to it.
        assertNothingPrepared(dimension.forRole(ShaderProgramRole.SKY_BASIC), ResolutionState.MISSING);
        assertEquals(Arrays.asList("world-1/gbuffers_textured"), names(dimension.uniquePrograms()));
    }

    // Same folder and program names in both packs, so only the exact folder object can tell them apart.
    @Test
    void folderOfAnotherPackIsRejected() throws Exception {
        ShaderPack own = pack("gbuffers_basic", "world-1/gbuffers_basic");
        ShaderPack other = pack("gbuffers_basic", "world-1/gbuffers_basic");

        IllegalArgumentException root = assertThrows(IllegalArgumentException.class, () -> prepare(own, other.root()));
        assertTrue(root.getMessage().contains("'shaders'"), root.getMessage());
        IllegalArgumentException dimension = assertThrows(IllegalArgumentException.class,
                () -> prepare(own, other.dimensionDirectories().get("world-1")));
        assertTrue(dimension.getMessage().contains("'shaders/world-1'"), dimension.getMessage());
        assertEquals(1, prepare(own, own.dimensionDirectories().get("world-1")).uniquePrograms().size());
    }

    @Test
    void suppliedMacrosReachEveryStage() throws Exception {
        ShaderPack pack = pack("gbuffers_basic");

        PreparedWorldPrograms programs = PreparedWorldPrograms.prepare(pack, pack.root(),
                ShaderMacros.empty().with("FOCALIS_TEST", "7"), ShaderMacros.empty().with("SHADOW_QUALITY", "2"));

        PreparedProgram basic = programs.uniquePrograms().get(0);
        assertEquals(2, basic.stages().size());
        for (PreparedProgram.Stage stage : basic.stages().values()) {
            String text = stage.source().text();
            assertEquals("#version 120\n#define FOCALIS_TEST 7\n#define SHADOW_QUALITY 2\n",
                    text.substring(0, text.indexOf("void")), stage.file().toString());
        }
    }

    // Which stages a program needs is checked when it's built, not here.
    @Test
    void stageCombinationsAreNotChecked() throws Exception {
        PreparedWorldPrograms programs = prepareRoot(TestPrograms.pack(temp, "gbuffers_basic.fsh", FRAGMENT));

        PreparedProgram basic = program(programs, ShaderProgramRole.HAND);
        assertEquals(1, basic.stages().size());
        assertTrue(basic.stages().containsKey(ProgramStage.FRAGMENT));
    }

    @Test
    void uniqueProgramsFollowRoleOrder() throws Exception {
        PreparedWorldPrograms programs = prepareRoot(pack("gbuffers_basic", "gbuffers_entities", "gbuffers_terrain",
                "gbuffers_textured", "gbuffers_skybasic"));

        // SKY_BASIC, then SKY_TEXTURED on gbuffers_textured, then the terrain roles, then ENTITIES. Nothing
        // falls back as far as gbuffers_basic.
        assertEquals(Arrays.asList("gbuffers_skybasic", "gbuffers_textured", "gbuffers_terrain", "gbuffers_entities"),
                names(programs.uniquePrograms()));
    }

    @Test
    void collectionsAreReadOnly() throws Exception {
        PreparedWorldPrograms programs = prepareRoot(pack("gbuffers_basic"));
        PreparedWorldPrograms.Entry none = programs.forRole(ShaderProgramRole.NONE);

        assertThrows(UnsupportedOperationException.class,
                () -> programs.entries().put(ShaderProgramRole.HAND, none));
        assertThrows(UnsupportedOperationException.class, () -> programs.entries().remove(ShaderProgramRole.HAND));
        assertThrows(UnsupportedOperationException.class,
                () -> programs.uniquePrograms().add(programs.uniquePrograms().get(0)));
        assertThrows(UnsupportedOperationException.class, () -> programs.uniquePrograms().clear());
    }
}
