// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.shader.program;

import io.github.silentcall2598.focalis.shader.pack.ShaderMacros;
import io.github.silentcall2598.focalis.shader.pack.ShaderPack;
import io.github.silentcall2598.focalis.shader.pack.StandardMacros;
import io.github.silentcall2598.focalis.shader.routing.ShaderProgramRole;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;

import static io.github.silentcall2598.focalis.shader.program.TestPrograms.FRAGMENT;
import static io.github.silentcall2598.focalis.shader.program.TestPrograms.VERTEX;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldProgramInputsTest {

    private static final ShaderCapabilities EVERYTHING = new ShaderCapabilities(true, true, true);
    // A program Minecraft or another mod had active before Focalis did anything.
    private static final int EXTERNAL = 7;
    private static final String LIT = "#version 120\nuniform sampler2D texture;\nuniform sampler2D lightmap;\n"
            + "void main() { gl_FragColor = vec4(1.0); }\n";

    @TempDir
    Path temp;

    private final RecordingShaderGl gl = new RecordingShaderGl();

    WorldProgramInputsTest() {
        gl.current = EXTERNAL;
    }

    private BuiltWorldPrograms build(String... pathsAndTexts) throws Exception {
        ShaderPack pack = TestPrograms.pack(temp, pathsAndTexts);
        PreparedWorldPrograms prepared = PreparedWorldPrograms.prepare(pack, pack.root(),
                StandardMacros.environment(), ShaderMacros.empty());
        return BuiltWorldPrograms.build(prepared, new ProgramBuilder(EVERYTHING, gl));
    }

    private static String fragment(String declarations) {
        return "#version 120\n" + declarations + "\nvoid main() { gl_FragColor = vec4(1.0); }\n";
    }

    private static BuiltWorldPrograms.Build only(BuiltWorldPrograms programs) {
        assertEquals(1, programs.builds().size());
        return programs.builds().get(0);
    }

    @Test
    void textureAndLightmapReadUnitsZeroAndOne() throws Exception {
        BuiltWorldPrograms programs = build("gbuffers_terrain.vsh", VERTEX, "gbuffers_terrain.fsh", LIT);
        ShaderProgram program = only(programs).program();

        assertEquals(0, gl.uniformValue(program.id(), "texture"));
        assertEquals(1, gl.uniformValue(program.id(), "lightmap"));
        // Unit indices, never GL_TEXTURE0 or GL_TEXTURE1.
        assertEquals(Arrays.asList(program.id() + ":texture=0", program.id() + ":lightmap=1"), gl.uniformSets);
        assertEquals(0, gl.uniformErrors);
        WorldProgramInputs inputs = only(programs).inputs();
        assertTrue(inputs.location(WorldSampler.TEXTURE) >= 0);
        assertTrue(inputs.location(WorldSampler.LIGHTMAP) >= 0);
        assertEquals(Collections.emptyList(), inputs.unprovided());
    }

    @Test
    void theProgramThatWasCurrentIsBoundAgain() throws Exception {
        BuiltWorldPrograms programs = build("gbuffers_terrain.vsh", VERTEX, "gbuffers_terrain.fsh", LIT);

        assertEquals(Arrays.asList(only(programs).program().id(), EXTERNAL), gl.uses);
        assertEquals(EXTERNAL, gl.current);
    }

    @Test
    void noProgramCurrentBeforeMeansNoneAfter() throws Exception {
        gl.current = 0;

        build("gbuffers_terrain.vsh", VERTEX, "gbuffers_terrain.fsh", LIT);

        assertEquals(0, gl.current);
    }

    @Test
    void aProgramWithoutSupportedSamplersIsNeverBound() throws Exception {
        BuiltWorldPrograms programs = build("gbuffers_terrain.vsh", VERTEX, "gbuffers_terrain.fsh",
                fragment("uniform float frameTimeCounter;"));

        assertEquals(Collections.emptyList(), gl.uses);
        assertEquals(Collections.emptyList(), gl.uniformSets);
        assertEquals(EXTERNAL, gl.current);
        assertEquals(Collections.singletonList("frameTimeCounter"), only(programs).inputs().unprovided());
    }

    @Test
    void anOptimizedOutSamplerIsLeftAlone() throws Exception {
        gl.optimizedOut.add("lightmap");

        BuiltWorldPrograms programs = build("gbuffers_terrain.vsh", VERTEX, "gbuffers_terrain.fsh", LIT);

        BuiltWorldPrograms.Build build = only(programs);
        assertTrue(build.succeeded());
        assertEquals(-1, build.inputs().location(WorldSampler.LIGHTMAP));
        assertEquals(Collections.singletonList(build.program().id() + ":texture=0"), gl.uniformSets);
        assertEquals(0, gl.uniformErrors);
    }

    @Test
    void samplerNamesFocalisDoesNotProvideGetNothing() throws Exception {
        BuiltWorldPrograms programs = build("gbuffers_terrain.vsh", VERTEX, "gbuffers_terrain.fsh",
                fragment("uniform sampler2D gtexture;\nuniform sampler2D tex;\nuniform sampler2D normals;"));

        BuiltWorldPrograms.Build build = only(programs);
        assertTrue(build.succeeded());
        assertEquals(Collections.emptyList(), gl.uniformSets);
        assertEquals(Arrays.asList("gtexture", "tex", "normals"), build.inputs().unprovided());
        assertEquals(-1, build.inputs().location(WorldSampler.TEXTURE));
    }

    @Test
    void aSupportedSamplerWithAnotherTypeFailsOnlyItsProgram() throws Exception {
        BuiltWorldPrograms programs = build("gbuffers_terrain.vsh", VERTEX, "gbuffers_terrain.fsh",
                fragment("uniform float lightmap;"), "gbuffers_water.vsh", VERTEX, "gbuffers_water.fsh", LIT);

        BuiltWorldPrograms.Entry terrain = programs.forRole(ShaderProgramRole.TERRAIN_SOLID);
        BuiltWorldPrograms.Entry water = programs.forRole(ShaderProgramRole.TERRAIN_TRANSLUCENT);
        assertFalse(terrain.ready());
        assertEquals(ProgramFailure.Kind.INPUTS, terrain.failure().kind());
        assertTrue(terrain.failure().summary().contains("declares lightmap as a float, but it has to be a sampler2D"),
                terrain.failure().summary());
        assertNull(terrain.build().inputs());
        assertTrue(water.ready());
        assertEquals(Collections.singleton(water.program().id()), gl.livePrograms);
        assertEquals(EXTERNAL, gl.current);
    }

    @Test
    void samplerArraysAndShadowSamplersAreRefused() throws Exception {
        // A driver reports an array of one, or one whose first element is the only one used, with a size of 1.
        gl.trimmedArrays.add("texture");
        BuiltWorldPrograms array = build("gbuffers_terrain.vsh", VERTEX, "gbuffers_terrain.fsh",
                fragment("uniform sampler2D lightmap[2];"));
        BuiltWorldPrograms single = build("gbuffers_terrain.vsh", VERTEX, "gbuffers_terrain.fsh",
                fragment("uniform sampler2D lightmap[1];"));
        BuiltWorldPrograms trimmed = build("gbuffers_terrain.vsh", VERTEX, "gbuffers_terrain.fsh",
                fragment("uniform sampler2D texture[4];"));
        BuiltWorldPrograms shadow = build("gbuffers_terrain.vsh", VERTEX, "gbuffers_terrain.fsh",
                fragment("uniform sampler2DShadow texture;"));

        for (BuiltWorldPrograms programs : Arrays.asList(array, single, trimmed)) {
            assertTrue(only(programs).failure().summary().contains("as an array with a sampler2D"),
                    only(programs).failure().summary());
        }
        assertTrue(only(shadow).failure().summary().contains("texture as a sampler2DShadow"),
                only(shadow).failure().summary());
        assertTrue(gl.livePrograms.isEmpty());
        assertEquals(Collections.emptyList(), gl.uses);
    }

    @Test
    void eachProgramGetsItsOwnLocationsAndValues() throws Exception {
        BuiltWorldPrograms programs = build("gbuffers_terrain.vsh", VERTEX, "gbuffers_terrain.fsh", LIT,
                "gbuffers_entities.vsh", VERTEX, "gbuffers_entities.fsh", LIT);

        ShaderProgram terrain = programs.forRole(ShaderProgramRole.TERRAIN_SOLID).program();
        ShaderProgram entities = programs.forRole(ShaderProgramRole.ENTITIES).program();
        assertNotEquals(terrain.id(), entities.id());
        WorldProgramInputs terrainInputs = programs.forRole(ShaderProgramRole.TERRAIN_SOLID).build().inputs();
        WorldProgramInputs entitiesInputs = programs.forRole(ShaderProgramRole.ENTITIES).build().inputs();
        assertNotEquals(terrainInputs.location(WorldSampler.LIGHTMAP), entitiesInputs.location(WorldSampler.LIGHTMAP));
        for (ShaderProgram program : Arrays.asList(terrain, entities)) {
            assertEquals(0, gl.uniformValue(program.id(), "texture"));
            assertEquals(1, gl.uniformValue(program.id(), "lightmap"));
        }
        assertEquals(0, gl.uniformErrors);
        assertEquals(2, gl.activeUniformQueries);
        assertEquals(4, gl.locationQueries);
    }

    @Test
    void aFailingUniformCallStillBindsThePreviousProgramAndDeletesTheNewOne() throws Exception {
        IllegalStateException failure = new IllegalStateException("driver");
        gl.uniformThrows = failure;

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> build("gbuffers_terrain.vsh", VERTEX, "gbuffers_terrain.fsh", LIT));

        assertSame(failure, thrown);
        assertEquals(0, thrown.getSuppressed().length);
        assertEquals(EXTERNAL, gl.current);
        assertEquals(EXTERNAL, (int) gl.uses.get(gl.uses.size() - 1));
        assertTrue(gl.livePrograms.isEmpty());
        assertTrue(gl.liveShaders.isEmpty());
    }

    @Test
    void aBindThatFailsBeforeBindingIsStillFollowedByTheRestore() throws Exception {
        IllegalStateException failure = new IllegalStateException("bind");
        gl.useThrowsOnCall.put(1, failure);

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> build("gbuffers_terrain.vsh", VERTEX, "gbuffers_terrain.fsh", LIT));

        assertSame(failure, thrown);
        assertEquals(2, gl.uses.size());
        assertEquals(EXTERNAL, (int) gl.uses.get(1));
        assertEquals(EXTERNAL, gl.current);
        assertEquals(Collections.emptyList(), gl.uniformSets);
        assertTrue(gl.livePrograms.isEmpty());
    }

    @Test
    void aBindThatThrowsAfterBindingIsUndone() throws Exception {
        IllegalStateException failure = new IllegalStateException("late error");
        gl.useThrowsAfterBindOnCall.put(1, failure);

        assertSame(failure, assertThrows(IllegalStateException.class,
                () -> build("gbuffers_terrain.vsh", VERTEX, "gbuffers_terrain.fsh", LIT)));

        assertEquals(EXTERNAL, gl.current);
        assertEquals(Collections.emptyList(), gl.uniformSets);
        assertTrue(gl.livePrograms.isEmpty());
    }

    @Test
    void aFailedRestoreLeavesNoProgramBoundInsteadOfTheDeletedOne() throws Exception {
        IllegalStateException failure = new IllegalStateException("restore");
        // The first use binds the new program and the second puts the previous one back.
        gl.useThrowsOnCall.put(2, failure);

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> build("gbuffers_terrain.vsh", VERTEX, "gbuffers_terrain.fsh", LIT));

        assertSame(failure, thrown);
        assertEquals(0, thrown.getSuppressed().length);
        int program = gl.uses.get(0);
        assertEquals(Arrays.asList(program, EXTERNAL, 0), gl.uses);
        assertEquals(0, gl.current);
        assertEquals(Collections.singletonList(program), gl.deleteOrder);
        assertTrue(gl.livePrograms.isEmpty());
    }

    @Test
    void aRestoreThatThrowsAfterBindingKeepsThePreviousProgram() throws Exception {
        gl.useThrowsAfterBindOnCall.put(2, new IllegalStateException("late error"));

        assertThrows(IllegalStateException.class,
                () -> build("gbuffers_terrain.vsh", VERTEX, "gbuffers_terrain.fsh", LIT));

        // The previous program is back, so nothing else is bound over it.
        assertEquals(2, gl.uses.size());
        assertEquals(EXTERNAL, gl.current);
        assertTrue(gl.livePrograms.isEmpty());
    }

    @Test
    void laterFailuresAreKeptOnTheFirstOne() throws Exception {
        IllegalStateException uniform = new IllegalStateException("uniform");
        IllegalStateException restore = new IllegalStateException("restore");
        IllegalStateException unbind = new IllegalStateException("unbind");
        gl.uniformThrows = uniform;
        gl.useThrowsOnCall.put(2, restore);
        gl.useThrowsOnCall.put(3, unbind);

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> build("gbuffers_terrain.vsh", VERTEX, "gbuffers_terrain.fsh", LIT));

        assertSame(uniform, thrown);
        assertArrayEquals(new Throwable[] {restore, unbind}, thrown.getSuppressed());
        // Nothing could be unbound, so the program is still current when it's deleted. GL only flags it then.
        int program = gl.uses.get(0);
        assertEquals(program, gl.current);
        assertEquals(Collections.singletonList(program), gl.deleteOrder);
    }

    @Test
    void aFailingQueryAfterAFailedRestoreIsKept() throws Exception {
        IllegalStateException restore = new IllegalStateException("restore");
        IllegalStateException query = new IllegalStateException("query");
        gl.useThrowsOnCall.put(2, restore);
        // The first query is the one that remembers the previous program.
        gl.currentThrowsOnCall.put(2, query);

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> build("gbuffers_terrain.vsh", VERTEX, "gbuffers_terrain.fsh", LIT));

        assertSame(restore, thrown);
        assertArrayEquals(new Throwable[] {query}, thrown.getSuppressed());
        assertTrue(gl.livePrograms.isEmpty());
    }

    @Test
    void aFailureAfterEarlierProgramsDeletesThemToo() throws Exception {
        // The second program's bind fails, after the first program was built and connected.
        gl.useThrowsOnCall.put(3, new IllegalStateException("bind"));

        assertThrows(IllegalStateException.class, () -> build("gbuffers_terrain.vsh", VERTEX,
                "gbuffers_terrain.fsh", LIT, "gbuffers_water.vsh", VERTEX, "gbuffers_water.fsh", LIT));

        assertTrue(gl.livePrograms.isEmpty());
        assertEquals(EXTERNAL, gl.current);
    }

    @Test
    void connectingOnlyLooksUpBindsAndSets() throws Exception {
        RecordingShaderGl plain = new RecordingShaderGl();
        ShaderPack pack = TestPrograms.pack(temp, "gbuffers_terrain.vsh", VERTEX, "gbuffers_terrain.fsh", FRAGMENT);
        BuiltWorldPrograms.build(PreparedWorldPrograms.prepare(pack, pack.root(), StandardMacros.environment(),
                ShaderMacros.empty()), new ProgramBuilder(EVERYTHING, plain));

        BuiltWorldPrograms programs = build("gbuffers_terrain.vsh", VERTEX, "gbuffers_terrain.fsh", LIT);

        // Two locations, one current program query, two binds and two sets. The GL interface has no texture calls.
        assertEquals(plain.calls + 7, gl.calls);
        assertNotNull(only(programs).inputs());
    }

    @Test
    void deletingTheSetDeletesProgramsWithInputsOnce() throws Exception {
        BuiltWorldPrograms programs = build("gbuffers_terrain.vsh", VERTEX, "gbuffers_terrain.fsh", LIT);
        int id = only(programs).program().id();

        programs.delete();
        programs.delete();

        assertEquals(Collections.singletonList(id), gl.deleteOrder);
        assertFalse(programs.forRole(ShaderProgramRole.TERRAIN_SOLID).ready());
    }
}
