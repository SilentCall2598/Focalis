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
import java.util.Collections;
import java.util.List;

import static io.github.silentcall2598.focalis.shader.program.TestPrograms.FRAGMENT;
import static io.github.silentcall2598.focalis.shader.program.TestPrograms.VERTEX;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LiveWorldProgramsTest {

    private static final ShaderCapabilities EVERYTHING = new ShaderCapabilities(true, true, true);
    private static final String BROKEN = "#version 120\nBROKEN\nvoid main() {}\n";
    private static final int EXTERNAL = 7;

    @TempDir
    Path temp;

    private final RecordingShaderGl shaderGl = new RecordingShaderGl();
    private final RecordingProgramBindingGl gl = new RecordingProgramBindingGl(EXTERNAL);
    // Every program change along with how many programs were deleted before it.
    private final List<String> events = new ArrayList<>();
    private final ScopedProgramBinding scopes = new ScopedProgramBinding(new ProgramBindingGl() {
        @Override
        public int currentProgram() {
            return gl.currentProgram();
        }

        @Override
        public void useProgram(int program) {
            events.add("use " + program + " after " + shaderGl.deleteOrder.size() + " deletes");
            gl.useProgram(program);
        }
    });

    LiveWorldProgramsTest() {
        shaderGl.failCompileWhenSourceContains = "BROKEN";
    }

    private LiveWorldPrograms build(String... programNames) throws Exception {
        List<String> files = new ArrayList<>();
        for (String program : programNames) {
            files.addAll(Arrays.asList(program + ".vsh", VERTEX, program + ".fsh", FRAGMENT));
        }
        return buildFiles(files.toArray(new String[0]));
    }

    private LiveWorldPrograms buildFiles(String... pathsAndTexts) throws Exception {
        ShaderPack pack = TestPrograms.pack(temp, pathsAndTexts);
        PreparedWorldPrograms prepared = PreparedWorldPrograms.prepare(pack, pack.root(),
                StandardMacros.environment(), ShaderMacros.empty());
        return LiveWorldPrograms.build(prepared, new ProgramBuilder(EVERYTHING, shaderGl), scopes);
    }

    private static int id(LiveWorldPrograms live, ShaderProgramRole role) {
        return live.programs().forRole(role).program().id();
    }

    // The sky with its sun inside it, both bound, and the sun's END never came.
    private int[] openSkyAndSun(LiveWorldPrograms live) {
        live.worldStart();
        assertTrue(live.stageStart(RenderStage.SKY, RenderDrawKind.SKY_BASIC));
        assertTrue(live.stageStart(RenderStage.SKY, RenderDrawKind.SKY_TEXTURED));
        int basic = id(live, ShaderProgramRole.SKY_BASIC);
        int textured = id(live, ShaderProgramRole.SKY_TEXTURED);
        assertEquals(Arrays.asList(basic, textured), gl.uses);
        return new int[] {basic, textured};
    }

    @Test
    void aWholeWorldPassBindsAndLeavesNothingBehind() throws Exception {
        LiveWorldPrograms live = build("gbuffers_terrain", "gbuffers_clouds");
        int terrain = id(live, ShaderProgramRole.TERRAIN_SOLID);
        int clouds = id(live, ShaderProgramRole.CLOUDS);

        live.worldStart();
        live.stageStart(RenderStage.TERRAIN, RenderDrawKind.TERRAIN_SOLID);
        live.stageEnd(RenderStage.TERRAIN, RenderDrawKind.TERRAIN_SOLID);
        live.stageStart(RenderStage.CLOUDS, RenderDrawKind.DEFAULT);
        live.stageEnd(RenderStage.CLOUDS, RenderDrawKind.DEFAULT);
        live.worldEnd();
        live.frameEnd();

        assertEquals(Arrays.asList(terrain, EXTERNAL, clouds, EXTERNAL), gl.uses);
        assertTrue(scopes.isEmpty());
    }

    @Test
    void entityOutlinesHandTheProgramToVanillaAndBack() throws Exception {
        LiveWorldPrograms live = build("gbuffers_entities");
        int entities = id(live, ShaderProgramRole.ENTITIES);

        live.worldStart();
        live.stageStart(RenderStage.ENTITIES, RenderDrawKind.ENTITY_PASS_0);
        live.vanillaProgramsStart(RenderStage.ENTITY_OUTLINES, RenderDrawKind.DEFAULT);
        assertEquals(EXTERNAL, gl.current);
        gl.current = 0;
        live.vanillaProgramsEnd(RenderStage.ENTITY_OUTLINES, RenderDrawKind.DEFAULT);
        assertEquals(entities, gl.current);
        live.stageEnd(RenderStage.ENTITIES, RenderDrawKind.ENTITY_PASS_0);
        live.worldEnd();

        assertEquals(Arrays.asList(entities, EXTERNAL, entities, EXTERNAL), gl.uses);
        assertTrue(scopes.isEmpty());
    }

    @Test
    void rendererThatLeftAnotherProgramGetsTheStageProgramBack() throws Exception {
        LiveWorldPrograms live = build("gbuffers_entities");
        int entities = id(live, ShaderProgramRole.ENTITIES);
        live.worldStart();
        live.stageStart(RenderStage.ENTITIES, RenderDrawKind.ENTITY_PASS_0);

        // A mod renderer that binds its own shader and releases it to 0, then one that leaves its own bound.
        gl.current = 0;
        assertTrue(live.rendererReturned());
        assertEquals(entities, gl.current);
        assertFalse(live.rendererReturned());
        gl.current = 55;
        assertTrue(live.rendererReturned());
        live.stageEnd(RenderStage.ENTITIES, RenderDrawKind.ENTITY_PASS_0);
        live.worldEnd();

        assertEquals(EXTERNAL, gl.current);
        assertEquals(Arrays.asList(entities, entities, entities, EXTERNAL), gl.uses);
    }

    @Test
    void renderersOutsideTheWorldPassOrInsideTheOutlinesAreLeftAlone() throws Exception {
        LiveWorldPrograms live = build("gbuffers_entities");
        int entities = id(live, ShaderProgramRole.ENTITIES);
        gl.current = 0;
        assertFalse(live.rendererReturned());
        assertEquals(0, gl.current);
        gl.current = EXTERNAL;

        live.worldStart();
        live.stageStart(RenderStage.ENTITIES, RenderDrawKind.ENTITY_PASS_0);
        live.vanillaProgramsStart(RenderStage.ENTITY_OUTLINES, RenderDrawKind.DEFAULT);
        gl.current = 0;
        assertFalse(live.rendererReturned());
        assertEquals(0, gl.current);
        live.vanillaProgramsEnd(RenderStage.ENTITY_OUTLINES, RenderDrawKind.DEFAULT);
        live.stageEnd(RenderStage.ENTITIES, RenderDrawKind.ENTITY_PASS_0);
        live.worldEnd();
        gl.current = 0;
        assertFalse(live.rendererReturned());

        assertEquals(Arrays.asList(entities, EXTERNAL, entities, EXTERNAL), gl.uses);
    }

    @Test
    void worldEndDuringOutlinesDropsTheSuspensionAndThrows() throws Exception {
        LiveWorldPrograms live = build("gbuffers_entities");
        int entities = id(live, ShaderProgramRole.ENTITIES);
        live.worldStart();
        live.stageStart(RenderStage.ENTITIES, RenderDrawKind.ENTITY_PASS_0);
        live.vanillaProgramsStart(RenderStage.ENTITY_OUTLINES, RenderDrawKind.DEFAULT);
        gl.current = 0;

        assertThrows(IllegalStateException.class, live::worldEnd);

        assertTrue(scopes.isEmpty());
        assertFalse(scopes.isSuspended());
        // The external program already went back when the scope stepped aside.
        assertEquals(Arrays.asList(entities, EXTERNAL), gl.uses);
        assertEquals(0, gl.current);
    }

    @Test
    void suspensionWithoutAnOpenScopeStillHasToEndBeforeTheWorldPass() throws Exception {
        LiveWorldPrograms live = build("gbuffers_entities");
        live.worldStart();
        // Like a mod drawing entities from inside the world pass without an ENTITIES stage around it.
        live.vanillaProgramsStart(RenderStage.ENTITY_OUTLINES, RenderDrawKind.DEFAULT);
        live.vanillaProgramsEnd(RenderStage.ENTITY_OUTLINES, RenderDrawKind.DEFAULT);
        live.worldEnd();
        live.worldStart();
        live.vanillaProgramsStart(RenderStage.ENTITY_OUTLINES, RenderDrawKind.DEFAULT);

        assertThrows(IllegalStateException.class, live::frameEnd);

        assertFalse(scopes.isSuspended());
        assertEquals(Collections.emptyList(), gl.uses);
    }

    @Test
    void cleanupDuringOutlinesDeletesWithoutBindingAnything() throws Exception {
        LiveWorldPrograms live = build("gbuffers_entities");
        int entities = id(live, ShaderProgramRole.ENTITIES);
        live.worldStart();
        live.stageStart(RenderStage.ENTITIES, RenderDrawKind.ENTITY_PASS_0);
        live.vanillaProgramsStart(RenderStage.ENTITY_OUTLINES, RenderDrawKind.DEFAULT);

        live.delete();

        assertEquals(Arrays.asList(entities, EXTERNAL), gl.uses);
        assertEquals(Collections.singletonList(entities), shaderGl.deleteOrder);
        assertTrue(scopes.isEmpty());
        assertFalse(scopes.isSuspended());
    }

    @Test
    void stagesOutsideTheWorldPassAreLeftAlone() throws Exception {
        LiveWorldPrograms live = build("gbuffers_entities");

        // Before the first world pass, and after it ended, like a mod drawing entities for a GUI.
        assertFalse(live.stageStart(RenderStage.ENTITIES, RenderDrawKind.ENTITY_PASS_0));
        live.vanillaProgramsStart(RenderStage.ENTITY_OUTLINES, RenderDrawKind.DEFAULT);
        live.vanillaProgramsEnd(RenderStage.ENTITY_OUTLINES, RenderDrawKind.DEFAULT);
        live.stageEnd(RenderStage.ENTITIES, RenderDrawKind.ENTITY_PASS_0);
        assertFalse(scopes.isSuspended());
        live.worldStart();
        live.worldEnd();
        assertFalse(live.stageStart(RenderStage.ENTITIES, RenderDrawKind.ENTITY_PASS_0));
        live.stageEnd(RenderStage.ENTITIES, RenderDrawKind.ENTITY_PASS_0);
        live.frameEnd();

        assertEquals(Collections.emptyList(), gl.uses);
        assertEquals(0, gl.queries);
    }

    @Test
    void worldEndWithAnOpenScopeRestoresAndThrows() throws Exception {
        LiveWorldPrograms live = build("gbuffers_skybasic", "gbuffers_skytextured");
        int[] sky = openSkyAndSun(live);

        IllegalStateException error = assertThrows(IllegalStateException.class, live::worldEnd);

        assertTrue(error.getMessage().contains("at the end of a world pass"), error.getMessage());
        assertTrue(scopes.isEmpty());
        assertEquals(EXTERNAL, gl.current);
        assertEquals(Arrays.asList(sky[0], sky[1], sky[0], EXTERNAL), gl.uses);
    }

    @Test
    void frameEndCatchesAWorldPassThatNeverEnded() throws Exception {
        LiveWorldPrograms live = build("gbuffers_skybasic", "gbuffers_skytextured");
        openSkyAndSun(live);

        IllegalStateException error = assertThrows(IllegalStateException.class, live::frameEnd);

        assertTrue(error.getMessage().contains("at the end of a frame"), error.getMessage());
        assertTrue(scopes.isEmpty());
        assertEquals(EXTERNAL, gl.current);
        // The pass is over either way, so stages after it are left alone.
        assertFalse(live.stageStart(RenderStage.SKY, RenderDrawKind.SKY_BASIC));
    }

    @Test
    void frameEndAfterAWorldPassWithoutItsEndStopsBinding() throws Exception {
        LiveWorldPrograms live = build("gbuffers_terrain");
        live.worldStart();

        live.frameEnd();

        assertFalse(live.stageStart(RenderStage.TERRAIN, RenderDrawKind.TERRAIN_SOLID));
        assertEquals(Collections.emptyList(), gl.uses);
    }

    @Test
    void worldStartWithAnOpenScopeRestoresAndThrows() throws Exception {
        LiveWorldPrograms live = build("gbuffers_skybasic", "gbuffers_skytextured");
        openSkyAndSun(live);

        // Like a mod rendering another world pass from inside the sun.
        IllegalStateException error = assertThrows(IllegalStateException.class, live::worldStart);

        assertTrue(error.getMessage().contains("when a world pass started"), error.getMessage());
        assertTrue(scopes.isEmpty());
        assertEquals(EXTERNAL, gl.current);
    }

    @Test
    void cleanupRestoresBeforeDeletingAndDeletesOnce() throws Exception {
        LiveWorldPrograms live = build("gbuffers_skybasic", "gbuffers_skytextured");
        int[] sky = openSkyAndSun(live);
        events.clear();

        live.delete();
        live.delete();

        assertEquals(Arrays.asList("use " + sky[0] + " after 0 deletes", "use " + EXTERNAL + " after 0 deletes"),
                events);
        assertEquals(Arrays.asList(sky[1], sky[0]), shaderGl.deleteOrder);
        assertTrue(live.programs().isDeleted());
        assertFalse(live.programs().forRole(ShaderProgramRole.SKY_BASIC).ready());
    }

    @Test
    void cleanupStillDeletesWhenRestoringThrows() throws Exception {
        LiveWorldPrograms live = build("gbuffers_skybasic", "gbuffers_skytextured");
        int[] sky = openSkyAndSun(live);
        RuntimeException restore = new RuntimeException("restore");
        RuntimeException delete = new RuntimeException("delete");
        // Two binds so far, so the third use is the first restore. The first delete throws too.
        gl.useThrowsOnCall.put(3, restore);
        shaderGl.deleteThrowsOnCall.put(1, delete);

        RuntimeException thrown = assertThrows(RuntimeException.class, live::delete);

        assertSame(restore, thrown);
        assertArrayEquals(new Throwable[] {delete}, restore.getSuppressed());
        // Both restores and both deletes were still attempted.
        assertEquals(Arrays.asList(sky[0], sky[1], sky[0], EXTERNAL), gl.uses);
        assertEquals(Arrays.asList(sky[1], sky[0]), shaderGl.deleteOrder);
        assertTrue(live.programs().isDeleted());
        assertTrue(scopes.isEmpty());
    }

    @Test
    void cleanupAfterEverythingEndedOnlyDeletes() throws Exception {
        LiveWorldPrograms live = build("gbuffers_terrain");
        live.worldStart();
        live.worldEnd();

        live.delete();

        assertEquals(Collections.emptyList(), gl.uses);
        assertEquals(1, shaderGl.deleteOrder.size());
    }

    @Test
    void usableOnlyWithAtLeastOneBuiltProgram() throws Exception {
        assertFalse(buildFiles("gbuffers_terrain.vsh", VERTEX, "gbuffers_terrain.fsh", BROKEN).usable());
        assertTrue(buildFiles("gbuffers_terrain.vsh", VERTEX, "gbuffers_terrain.fsh", BROKEN,
                "gbuffers_water.vsh", VERTEX, "gbuffers_water.fsh", FRAGMENT).usable());
    }
}
