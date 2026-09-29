// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.shader.program;

import io.github.silentcall2598.focalis.shader.pack.ShaderMacros;
import io.github.silentcall2598.focalis.shader.pack.ShaderPack;
import io.github.silentcall2598.focalis.shader.pack.StandardMacros;
import io.github.silentcall2598.focalis.shader.routing.ResolutionState;
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

class BuiltWorldProgramsTest {

    private static final ShaderCapabilities EVERYTHING = new ShaderCapabilities(true, true, true);
    // RecordingShaderGl fails to compile any stage whose source contains this.
    private static final String BROKEN = "#version 120\nBROKEN\nvoid main() {}\n";

    @TempDir
    Path temp;

    private final RecordingShaderGl gl = new RecordingShaderGl();

    BuiltWorldProgramsTest() {
        gl.failCompileWhenSourceContains = "BROKEN";
    }

    private static PreparedWorldPrograms prepare(ShaderPack pack) {
        return PreparedWorldPrograms.prepare(pack, pack.root(), StandardMacros.environment(), ShaderMacros.empty());
    }

    // A vertex and fragment shader for each program, like "gbuffers_basic".
    private PreparedWorldPrograms prepared(String... programs) throws Exception {
        List<String> files = new ArrayList<>();
        for (String program : programs) {
            files.addAll(Arrays.asList(program + ".vsh", VERTEX, program + ".fsh", FRAGMENT));
        }
        return prepare(TestPrograms.pack(temp, files.toArray(new String[0])));
    }

    // Pairs of path and text, for programs that need something other than the plain shaders.
    private PreparedWorldPrograms preparedFiles(String... pathsAndTexts) throws Exception {
        return prepare(TestPrograms.pack(temp, pathsAndTexts));
    }

    private BuiltWorldPrograms build(PreparedWorldPrograms prepared) {
        return BuiltWorldPrograms.build(prepared, new ProgramBuilder(EVERYTHING, gl));
    }

    private static List<String> names(List<BuiltWorldPrograms.Build> builds) {
        List<String> names = new ArrayList<>();
        for (BuiltWorldPrograms.Build build : builds) {
            names.add(build.prepared().name());
        }
        return names;
    }

    private static void assertNoBuild(BuiltWorldPrograms.Entry entry) {
        assertNull(entry.build(), entry.role().toString());
        assertNull(entry.program(), entry.role().toString());
        assertNull(entry.failure(), entry.role().toString());
        assertFalse(entry.ready(), entry.role().toString());
    }

    @Test
    void everyRoleHasAnEntryWrappingItsPreparedEntry() throws Exception {
        PreparedWorldPrograms prepared = prepared("gbuffers_basic");
        BuiltWorldPrograms built = build(prepared);

        for (ShaderProgramRole role : ShaderProgramRole.values()) {
            BuiltWorldPrograms.Entry entry = built.forRole(role);
            assertNotNull(entry, role.toString());
            assertSame(role, entry.role());
            assertSame(prepared.forRole(role), entry.prepared());
            assertSame(entry, built.entries().get(role));
        }
        assertEquals(ShaderProgramRole.values().length, built.entries().size());
    }

    @Test
    void oneBasicProgramIsBuiltOnceForEveryRole() throws Exception {
        PreparedWorldPrograms prepared = prepared("gbuffers_basic");
        BuiltWorldPrograms built = build(prepared);

        assertEquals(1, built.builds().size());
        BuiltWorldPrograms.Build basic = built.builds().get(0);
        assertTrue(basic.succeeded());
        for (ShaderProgramRole role : ShaderProgramRole.values()) {
            BuiltWorldPrograms.Entry entry = built.forRole(role);
            if (entry.prepared().program() != null) {
                assertSame(basic, entry.build(), role.toString());
                assertSame(basic.program(), entry.program(), role.toString());
                assertTrue(entry.ready(), role.toString());
            }
        }
        assertEquals(1, gl.createdPrograms);
        assertEquals(1, gl.livePrograms.size());
        assertTrue(gl.liveShaders.isEmpty());
    }

    @Test
    void eachUniqueProgramIsBuiltOnceInPreparedOrder() throws Exception {
        PreparedWorldPrograms prepared = prepared("gbuffers_basic", "gbuffers_entities", "gbuffers_terrain",
                "gbuffers_textured", "gbuffers_skybasic");
        BuiltWorldPrograms built = build(prepared);

        assertEquals(prepared.uniquePrograms().size(), built.builds().size());
        for (int i = 0; i < built.builds().size(); i++) {
            assertSame(prepared.uniquePrograms().get(i), built.builds().get(i).prepared());
        }
        assertEquals(Arrays.asList("gbuffers_skybasic", "gbuffers_textured", "gbuffers_terrain", "gbuffers_entities"),
                names(built.builds()));
        assertEquals(4, gl.createdPrograms);
        assertEquals(4, gl.linkCalls);
        assertSame(built.forRole(ShaderProgramRole.TERRAIN_SOLID).build(),
                built.forRole(ShaderProgramRole.TERRAIN_CUTOUT).build());
        assertNotSame(built.forRole(ShaderProgramRole.TERRAIN_SOLID).program(),
                built.forRole(ShaderProgramRole.ENTITIES).program());
    }

    // ENTITIES, PARTICLES_LIT, WEATHER and HAND all fall back to the broken gbuffers_textured_lit.
    @Test
    void sharedFailureIsOneBuildForEveryAlias() throws Exception {
        BuiltWorldPrograms built = build(preparedFiles("gbuffers_terrain.vsh", VERTEX, "gbuffers_terrain.fsh",
                FRAGMENT, "gbuffers_textured_lit.vsh", VERTEX, "gbuffers_textured_lit.fsh", BROKEN,
                "gbuffers_textured.vsh", VERTEX, "gbuffers_textured.fsh", FRAGMENT, "gbuffers_basic.vsh", VERTEX,
                "gbuffers_basic.fsh", FRAGMENT));

        BuiltWorldPrograms.Entry entities = built.forRole(ShaderProgramRole.ENTITIES);
        BuiltWorldPrograms.Build lit = entities.build();
        assertNotNull(lit);
        assertEquals("gbuffers_textured_lit", lit.prepared().name());
        assertFalse(lit.succeeded());
        assertNull(lit.program());
        assertEquals(ProgramFailure.Kind.COMPILE, lit.failure().kind());
        for (ShaderProgramRole role : Arrays.asList(ShaderProgramRole.PARTICLES_LIT, ShaderProgramRole.WEATHER,
                ShaderProgramRole.HAND)) {
            BuiltWorldPrograms.Entry entry = built.forRole(role);
            assertSame(lit, entry.build(), role.toString());
            assertSame(lit.failure(), entry.failure(), role.toString());
            assertFalse(entry.ready(), role.toString());
        }
        int failed = 0;
        for (BuiltWorldPrograms.Build build : built.builds()) {
            failed += build.succeeded() ? 0 : 1;
        }
        assertEquals(1, failed);
        assertEquals(3, gl.livePrograms.size());
        assertTrue(gl.liveShaders.isEmpty());
    }

    // gbuffers_skybasic is the first unique program, so everything after it has to build anyway.
    @Test
    void failureDoesNotStopLaterPrograms() throws Exception {
        BuiltWorldPrograms built = build(preparedFiles("gbuffers_skybasic.vsh", VERTEX, "gbuffers_skybasic.fsh",
                BROKEN, "gbuffers_terrain.vsh", VERTEX, "gbuffers_terrain.fsh", FRAGMENT, "gbuffers_textured.vsh",
                VERTEX, "gbuffers_textured.fsh", FRAGMENT));

        assertEquals(Arrays.asList("gbuffers_skybasic", "gbuffers_textured", "gbuffers_terrain"),
                names(built.builds()));
        assertNotNull(built.builds().get(0).failure());
        for (BuiltWorldPrograms.Build later : built.builds().subList(1, 3)) {
            assertTrue(later.succeeded(), later.prepared().name());
            assertNull(later.failure());
            assertFalse(later.program().isDeleted());
            assertTrue(gl.livePrograms.contains(later.program().id()));
        }
        assertEquals(2, gl.livePrograms.size());
    }

    @Test
    void invalidStageSetIsIsolated() throws Exception {
        BuiltWorldPrograms built = build(preparedFiles("gbuffers_entities.fsh", FRAGMENT, "gbuffers_basic.vsh",
                VERTEX, "gbuffers_basic.fsh", FRAGMENT));

        BuiltWorldPrograms.Entry entities = built.forRole(ShaderProgramRole.ENTITIES);
        assertEquals(ProgramFailure.Kind.INVALID_STAGES, entities.failure().kind());
        assertTrue(built.forRole(ShaderProgramRole.SKY_BASIC).ready());
        // The stage check runs before any GL call, so only gbuffers_basic made a program.
        assertEquals(1, gl.createdPrograms);
        assertEquals(1, gl.livePrograms.size());
    }

    @Test
    void unsupportedStageIsIsolated() throws Exception {
        PreparedWorldPrograms prepared = preparedFiles("gbuffers_entities.vsh", VERTEX, "gbuffers_entities.fsh",
                FRAGMENT, "gbuffers_entities.gsh", "#version 150\nvoid main() {}\n", "gbuffers_basic.vsh", VERTEX,
                "gbuffers_basic.fsh", FRAGMENT);

        BuiltWorldPrograms built = BuiltWorldPrograms.build(prepared,
                new ProgramBuilder(new ShaderCapabilities(true, false, false), gl));

        assertEquals(ProgramFailure.Kind.UNSUPPORTED_STAGE,
                built.forRole(ShaderProgramRole.ENTITIES).failure().kind());
        assertTrue(built.forRole(ShaderProgramRole.HAND).ready());
        assertEquals(1, gl.createdPrograms);
    }

    // A source problem was found before any GL work, so it isn't a build and never reaches the driver.
    @Test
    void sourcePreparationFailureIsNeverBuilt() throws Exception {
        BuiltWorldPrograms built = build(preparedFiles("gbuffers_entities.vsh", VERTEX, "gbuffers_entities.fsh",
                "#version 120\n#include \"/lib/missing.glsl\"\nvoid main() {}\n", "gbuffers_basic.vsh", VERTEX,
                "gbuffers_basic.fsh", FRAGMENT));

        BuiltWorldPrograms.Entry entities = built.forRole(ShaderProgramRole.ENTITIES);
        assertEquals(ResolutionState.RESOLVED, entities.prepared().resolution().state());
        assertNull(entities.prepared().program());
        assertNotNull(entities.prepared().problem());
        assertNoBuild(entities);
        assertEquals(Arrays.asList("gbuffers_basic"), names(built.builds()));
        assertEquals(1, gl.createdPrograms);
    }

    @Test
    void rolesWithoutAPreparedProgramHaveNoBuild() throws Exception {
        BuiltWorldPrograms built = build(prepared("gbuffers_skybasic"));

        assertNoBuild(built.forRole(ShaderProgramRole.NONE));
        assertNoBuild(built.forRole(ShaderProgramRole.UNCLASSIFIED));
        assertEquals(ResolutionState.MISSING, built.forRole(ShaderProgramRole.ENTITIES).prepared().resolution()
                .state());
        assertNoBuild(built.forRole(ShaderProgramRole.ENTITIES));
        assertTrue(built.forRole(ShaderProgramRole.SKY_BASIC).ready());
    }

    @Test
    void driverWarningsStillCountAsSuccess() throws Exception {
        gl.linkLog = "warning: something the driver didn't like\n";

        BuiltWorldPrograms built = build(prepared("gbuffers_basic"));

        BuiltWorldPrograms.Build basic = built.builds().get(0);
        assertTrue(basic.succeeded());
        assertNull(basic.failure());
        assertTrue(basic.program().driverLog().contains("something the driver didn't like"),
                basic.program().driverLog());
    }

    @Test
    void deleteFreesEveryProgramOnceAndKeepsTheResults() throws Exception {
        BuiltWorldPrograms built = build(prepared("gbuffers_basic", "gbuffers_textured", "gbuffers_terrain"));
        assertEquals(3, gl.livePrograms.size());

        built.delete();
        int deletes = gl.deletedPrograms;
        built.delete();

        assertTrue(built.isDeleted());
        assertTrue(gl.livePrograms.isEmpty());
        assertEquals(3, deletes);
        assertEquals(deletes, gl.deletedPrograms);
        for (BuiltWorldPrograms.Build build : built.builds()) {
            assertTrue(build.succeeded());
            assertTrue(build.program().isDeleted());
            assertThrows(IllegalStateException.class, build.program()::id);
        }
        assertFalse(built.forRole(ShaderProgramRole.TERRAIN_SOLID).ready());
        assertNotNull(built.forRole(ShaderProgramRole.TERRAIN_SOLID).program());
    }

    @Test
    void aliasedProgramIsDeletedOnce() throws Exception {
        BuiltWorldPrograms built = build(prepared("gbuffers_basic"));

        built.delete();

        assertEquals(1, gl.deletedPrograms);
        assertTrue(gl.livePrograms.isEmpty());
    }

    // A compile failure never made a program, so only the successful one gets deleted.
    @Test
    void failedBuildsNeedNoDelete() throws Exception {
        BuiltWorldPrograms built = build(preparedFiles("gbuffers_skybasic.vsh", VERTEX, "gbuffers_skybasic.fsh",
                BROKEN, "gbuffers_basic.vsh", VERTEX, "gbuffers_basic.fsh", FRAGMENT));
        assertEquals(0, gl.deletedPrograms);

        built.delete();

        assertEquals(1, gl.deletedPrograms);
        assertTrue(gl.livePrograms.isEmpty());
    }

    @Test
    void unexpectedFailureDeletesEarlierProgramsAndIsRethrown() throws Exception {
        PreparedWorldPrograms prepared = prepared("gbuffers_basic", "gbuffers_textured", "gbuffers_terrain");
        IllegalStateException lost = new IllegalStateException("context lost");
        gl.linkThrows = lost;
        gl.linkThrowsFromCall = 3;

        IllegalStateException thrown = assertThrows(IllegalStateException.class, () -> build(prepared));

        assertSame(lost, thrown);
        assertEquals(3, gl.linkCalls);
        assertEquals(3, gl.createdPrograms);
        assertTrue(gl.livePrograms.isEmpty());
        assertTrue(gl.liveShaders.isEmpty());
    }

    @Test
    void linkageErrorAlsoDeletesEarlierPrograms() throws Exception {
        PreparedWorldPrograms prepared = prepared("gbuffers_basic", "gbuffers_textured");
        NoClassDefFoundError missing = new NoClassDefFoundError("org/lwjgl/opengl/GL20");
        gl.linkThrows = missing;
        gl.linkThrowsFromCall = 2;

        assertSame(missing, assertThrows(NoClassDefFoundError.class, () -> build(prepared)));

        assertEquals(2, gl.createdPrograms);
        assertTrue(gl.livePrograms.isEmpty());
        assertTrue(gl.liveShaders.isEmpty());
    }

    @Test
    void collectionsAreReadOnly() throws Exception {
        BuiltWorldPrograms built = build(prepared("gbuffers_basic"));
        BuiltWorldPrograms.Entry none = built.forRole(ShaderProgramRole.NONE);

        assertThrows(UnsupportedOperationException.class, () -> built.builds().clear());
        assertThrows(UnsupportedOperationException.class, () -> built.builds().add(built.builds().get(0)));
        assertThrows(UnsupportedOperationException.class, () -> built.entries().put(ShaderProgramRole.HAND, none));
        assertThrows(UnsupportedOperationException.class, () -> built.entries().remove(ShaderProgramRole.HAND));
    }

    @Test
    void nullsAreRejected() throws Exception {
        PreparedWorldPrograms prepared = prepared("gbuffers_basic");

        assertThrows(NullPointerException.class, () -> BuiltWorldPrograms.build(null, EVERYTHING));
        assertThrows(NullPointerException.class, () -> BuiltWorldPrograms.build(prepared, (ShaderCapabilities) null));
        BuiltWorldPrograms built = build(prepared);
        assertThrows(NullPointerException.class, () -> built.forRole(null));
        assertEquals(1, gl.createdPrograms);
    }
}
