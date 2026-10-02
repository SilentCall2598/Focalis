// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.shader.program;

import io.github.silentcall2598.focalis.render.lifecycle.RenderDrawKind;
import io.github.silentcall2598.focalis.render.lifecycle.RenderStage;
import io.github.silentcall2598.focalis.shader.pack.ShaderPack;
import io.github.silentcall2598.focalis.shader.pack.ShaderPackLoader;
import io.github.silentcall2598.focalis.shader.routing.ShaderProgramRole;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
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
import static org.junit.jupiter.api.Assertions.assertNull;
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
    // The folders the session built, in order.
    private final List<String> built = new ArrayList<>();
    private RuntimeException listenerFailure;
    private final FrameInputs frame = new FrameInputs();

    LiveWorldProgramsTest() {
        shaderGl.failCompileWhenSourceContains = "BROKEN";
        frame.capture(0, 854, 480);
    }

    private LiveWorldPrograms session(ShaderPack pack) {
        return LiveWorldPrograms.create(pack, new ProgramBuilder(EVERYTHING, shaderGl), programs -> {
            built.add(programs.directory().path());
            if (listenerFailure != null) {
                throw listenerFailure;
            }
        }, scopes, frame);
    }

    // A pack with only a shaders folder, built by one overworld pass that drew nothing.
    private LiveWorldPrograms build(String... programNames) throws Exception {
        return buildFiles(files("", programNames));
    }

    private LiveWorldPrograms buildFiles(String... pathsAndTexts) throws Exception {
        LiveWorldPrograms live = session(TestPrograms.pack(temp, pathsAndTexts));
        live.worldStart(0);
        live.worldEnd();
        return live;
    }

    // A working vertex and fragment shader for each program in a folder.
    private static String[] files(String folder, String... programNames) {
        List<String> files = new ArrayList<>();
        String prefix = folder.isEmpty() ? "" : folder + "/";
        for (String program : programNames) {
            files.addAll(Arrays.asList(prefix + program + ".vsh", VERTEX, prefix + program + ".fsh", FRAGMENT));
        }
        return files.toArray(new String[0]);
    }

    private static String[] join(String[]... parts) {
        List<String> all = new ArrayList<>();
        for (String[] part : parts) {
            all.addAll(Arrays.asList(part));
        }
        return all.toArray(new String[0]);
    }

    // The main folder has terrain, and entities fall back to its textured program. world0 only has an entities
    // program, world-1 doesn't compile and world7 is empty.
    private ShaderPack dimensionPack() throws Exception {
        Path pack = TestPrograms.packFolder(temp, join(files("", "gbuffers_terrain", "gbuffers_textured"),
                files("world0", "gbuffers_entities"),
                new String[] {"world-1/gbuffers_terrain.vsh", VERTEX, "world-1/gbuffers_terrain.fsh", BROKEN}));
        Files.createDirectories(pack.resolve("shaders").resolve("world7"));
        return ShaderPackLoader.load(pack);
    }

    private static int id(LiveWorldPrograms live, ShaderProgramRole role) {
        return live.selected().programs().forRole(role).program().id();
    }

    private static String name(LiveWorldPrograms live, ShaderProgramRole role) {
        ShaderProgram program = live.selected().programs().forRole(role).program();
        return program == null ? null : program.name();
    }

    // One whole world pass drawing terrain and then entities. Returns every program change it made.
    private List<Integer> drawTerrainAndEntities(LiveWorldPrograms live, int dimension) {
        int before = gl.uses.size();
        live.worldStart(dimension);
        live.stageStart(RenderStage.TERRAIN, RenderDrawKind.TERRAIN_SOLID);
        live.stageEnd(RenderStage.TERRAIN, RenderDrawKind.TERRAIN_SOLID);
        live.stageStart(RenderStage.ENTITIES, RenderDrawKind.ENTITY_PASS_0);
        live.stageEnd(RenderStage.ENTITIES, RenderDrawKind.ENTITY_PASS_0);
        live.worldEnd();
        live.frameEnd();
        assertTrue(scopes.isEmpty());
        assertEquals(EXTERNAL, gl.current);
        return new ArrayList<>(gl.uses.subList(before, gl.uses.size()));
    }

    // The sky with its sun inside it, both bound, and the sun's END never came.
    private int[] openSkyAndSun(LiveWorldPrograms live) {
        live.worldStart(0);
        assertTrue(live.stageStart(RenderStage.SKY, RenderDrawKind.SKY_BASIC));
        assertTrue(live.stageStart(RenderStage.SKY, RenderDrawKind.SKY_TEXTURED));
        int basic = id(live, ShaderProgramRole.SKY_BASIC);
        int textured = id(live, ShaderProgramRole.SKY_TEXTURED);
        assertEquals(Arrays.asList(basic, textured), gl.uses);
        return new int[] {basic, textured};
    }

    @Test
    void eachDimensionBindsTheProgramsOfItsOwnFolder() throws Exception {
        LiveWorldPrograms live = session(dimensionPack());

        List<Integer> overworld = drawTerrainAndEntities(live, 0);
        assertEquals("world0", live.selected().directory().name());
        assertEquals("world0/gbuffers_entities", name(live, ShaderProgramRole.ENTITIES));
        int world0Entities = id(live, ShaderProgramRole.ENTITIES);
        List<Integer> end = drawTerrainAndEntities(live, 1);

        assertEquals("", live.selected().directory().name());
        assertEquals("gbuffers_terrain", name(live, ShaderProgramRole.TERRAIN_SOLID));
        assertEquals("gbuffers_textured", name(live, ShaderProgramRole.ENTITIES));
        // The overworld's terrain had no program in world0 and drew the normal way.
        assertEquals(Arrays.asList(world0Entities, EXTERNAL), overworld);
        assertEquals(Arrays.asList(id(live, ShaderProgramRole.TERRAIN_SOLID), EXTERNAL,
                id(live, ShaderProgramRole.ENTITIES), EXTERNAL), end);
    }

    @Test
    void aWorldFolderNeverBorrowsFromTheShadersFolder() throws Exception {
        LiveWorldPrograms live = session(dimensionPack());

        live.worldStart(0);
        BuiltWorldPrograms world0 = live.selected().programs();

        assertNull(world0.forRole(ShaderProgramRole.TERRAIN_SOLID).program());
        assertNull(world0.forRole(ShaderProgramRole.SKY_TEXTURED).program());
        assertEquals(1, world0.builds().size());
        assertEquals(Collections.singletonList("shaders/world0"), built);
        live.worldEnd();
    }

    @Test
    void aBrokenWorldFolderDrawsTheNormalWayWithoutFallingBack() throws Exception {
        LiveWorldPrograms live = session(dimensionPack());

        List<Integer> nether = drawTerrainAndEntities(live, -1);

        assertEquals(Collections.emptyList(), nether);
        DirectoryPrograms selected = live.selected();
        assertEquals("world-1", selected.directory().name());
        assertFalse(selected.usable());
        assertTrue(selected.programs().forRole(ShaderProgramRole.TERRAIN_SOLID).failure() != null);
        // The main folder wasn't even built.
        assertEquals(Collections.singletonList("shaders/world-1"), built);
    }

    @Test
    void anEmptyWorldFolderBuildsAndBindsNothing() throws Exception {
        LiveWorldPrograms live = session(dimensionPack());

        List<Integer> uses = drawTerrainAndEntities(live, 7);

        assertEquals(Collections.emptyList(), uses);
        assertEquals("world7", live.selected().directory().name());
        assertEquals(0, shaderGl.calls);
        assertEquals(Collections.singletonList("shaders/world7"), built);
    }

    @Test
    void nothingIsBuiltBeforeAWorldPassNeedsIt() throws Exception {
        LiveWorldPrograms live = session(dimensionPack());

        assertEquals(Collections.emptyList(), built);
        assertEquals(0, shaderGl.calls);
        assertNull(live.selected());
        live.worldStart(0);

        assertEquals(Collections.singletonList("shaders/world0"), built);
        assertEquals(1, shaderGl.createdPrograms);
        live.worldEnd();
    }

    @Test
    void eachFolderIsBuiltOnceAndReusedWhenADimensionComesBack() throws Exception {
        LiveWorldPrograms live = session(dimensionPack());

        drawTerrainAndEntities(live, 0);
        DirectoryPrograms overworld = live.selected();
        drawTerrainAndEntities(live, -1);
        DirectoryPrograms nether = live.selected();
        drawTerrainAndEntities(live, 1);
        DirectoryPrograms end = live.selected();
        int created = shaderGl.createdPrograms;
        int compiles = shaderGl.calls;

        List<Integer> overworldAgain = drawTerrainAndEntities(live, 0);
        assertSame(overworld, live.selected());
        drawTerrainAndEntities(live, -1);
        assertSame(nether, live.selected());
        drawTerrainAndEntities(live, 1);
        assertSame(end, live.selected());

        assertEquals(Arrays.asList("shaders/world0", "shaders/world-1", "shaders"), built);
        assertEquals(created, shaderGl.createdPrograms);
        assertEquals(compiles, shaderGl.calls);
        assertEquals(Arrays.asList(overworld.programs().forRole(ShaderProgramRole.ENTITIES).program().id(),
                EXTERNAL), overworldAgain);
    }

    @Test
    void dimensionsWithoutAFolderShareTheShadersFolder() throws Exception {
        LiveWorldPrograms live = session(dimensionPack());

        DirectoryPrograms end = live.worldStart(1);
        live.worldEnd();
        DirectoryPrograms other = live.worldStart(42);
        live.worldEnd();

        assertSame(end, other);
        assertEquals(Collections.singletonList("shaders"), built);
    }

    @Test
    void aWorldPassWithoutAWorldBindsNothingAndKeepsTheCache() throws Exception {
        LiveWorldPrograms live = session(dimensionPack());
        DirectoryPrograms overworld = live.worldStart(0);
        live.worldEnd();
        int created = shaderGl.createdPrograms;

        live.worldStartWithoutWorld();
        assertNull(live.selected());
        assertTrue(live.stageStart(RenderStage.ENTITIES, RenderDrawKind.ENTITY_PASS_0));
        live.stageEnd(RenderStage.ENTITIES, RenderDrawKind.ENTITY_PASS_0);
        live.worldEnd();
        assertEquals(Collections.emptyList(), gl.uses);

        assertSame(overworld, live.worldStart(0));
        live.worldEnd();
        assertEquals(created, shaderGl.createdPrograms);
        assertEquals(Collections.singletonList("shaders/world0"), built);
    }

    @Test
    void aFailedFolderLeavesTheOthersWorking() throws Exception {
        LiveWorldPrograms live = session(dimensionPack());

        drawTerrainAndEntities(live, -1);
        List<Integer> end = drawTerrainAndEntities(live, 1);
        List<Integer> netherAgain = drawTerrainAndEntities(live, -1);
        List<Integer> overworld = drawTerrainAndEntities(live, 0);

        assertEquals(4, end.size());
        assertEquals(Collections.emptyList(), netherAgain);
        assertEquals(2, overworld.size());
        assertEquals(Arrays.asList("shaders/world-1", "shaders", "shaders/world0"), built);
    }

    @Test
    void switchingWithAnOpenScopeRestoresAndThrows() throws Exception {
        LiveWorldPrograms live = session(dimensionPack());
        DirectoryPrograms overworld = live.worldStart(0);
        live.stageStart(RenderStage.ENTITIES, RenderDrawKind.ENTITY_PASS_0);

        assertThrows(IllegalStateException.class, () -> live.worldStart(1));

        assertTrue(scopes.isEmpty());
        assertEquals(EXTERNAL, gl.current);
        assertSame(overworld, live.selected());
        assertEquals(Collections.singletonList("shaders/world0"), built);
    }

    @Test
    void rendererRepairBindsTheSelectedFoldersProgram() throws Exception {
        LiveWorldPrograms live = session(dimensionPack());
        live.worldStart(0);
        int world0 = id(live, ShaderProgramRole.ENTITIES);
        live.worldEnd();
        live.worldStart(1);
        int main = id(live, ShaderProgramRole.ENTITIES);
        live.stageStart(RenderStage.ENTITIES, RenderDrawKind.ENTITY_PASS_0);

        gl.current = world0;
        assertTrue(live.rendererReturned());
        assertEquals(main, gl.current);
        live.stageEnd(RenderStage.ENTITIES, RenderDrawKind.ENTITY_PASS_0);
        live.worldEnd();

        assertEquals(EXTERNAL, gl.current);
    }

    @Test
    void aBuildThatThrowsLeavesNoProgramsOrFolderBehind() throws Exception {
        LiveWorldPrograms live = session(dimensionPack());
        IllegalStateException failure = new IllegalStateException("driver");
        shaderGl.linkThrows = failure;
        shaderGl.linkThrowsFromCall = 2;

        assertSame(failure, assertThrows(IllegalStateException.class, () -> live.worldStart(1)));

        assertTrue(shaderGl.livePrograms.isEmpty());
        assertTrue(live.built().isEmpty());
        assertNull(live.selected());
        assertTrue(scopes.isEmpty());
        assertEquals(Collections.emptyList(), built);
        live.delete();
    }

    @Test
    void aFolderStaysOwnedWhenItsListenerThrows() throws Exception {
        LiveWorldPrograms live = session(dimensionPack());
        listenerFailure = new IllegalStateException("listener");

        assertThrows(IllegalStateException.class, () -> live.worldStart(1));
        assertEquals(1, live.built().size());
        assertEquals(2, shaderGl.livePrograms.size());

        live.delete();
        assertTrue(shaderGl.livePrograms.isEmpty());
    }

    @Test
    void cleanupDeletesEveryFolderOnce() throws Exception {
        LiveWorldPrograms live = session(dimensionPack());
        for (int dimension : new int[] {0, -1, 1, 7, 0}) {
            live.worldStart(dimension);
            live.worldEnd();
        }
        List<BuiltWorldPrograms> sets = new ArrayList<>();
        for (DirectoryPrograms folder : live.built()) {
            sets.add(folder.programs());
        }
        int created = shaderGl.createdPrograms;

        live.delete();
        live.delete();

        assertEquals(3, created);
        assertEquals(created, shaderGl.deleteOrder.size());
        assertEquals(created, shaderGl.deleteOrder.stream().distinct().count());
        assertTrue(shaderGl.livePrograms.isEmpty());
        for (BuiltWorldPrograms set : sets) {
            assertTrue(set.isDeleted());
        }
        assertNull(live.selected());
        assertTrue(live.built().isEmpty());
        assertThrows(IllegalStateException.class, () -> live.worldStart(0));
        assertThrows(IllegalStateException.class, live::worldStartWithoutWorld);
    }

    @Test
    void cleanupDeletesTheOtherFoldersWhenOneThrows() throws Exception {
        LiveWorldPrograms live = session(dimensionPack());
        live.worldStart(0);
        live.worldEnd();
        live.worldStart(1);
        live.worldEnd();
        List<BuiltWorldPrograms> sets = new ArrayList<>();
        for (DirectoryPrograms folder : live.built()) {
            sets.add(folder.programs());
        }
        RuntimeException first = new RuntimeException("first");
        shaderGl.deleteThrowsOnCall.put(1, first);

        assertSame(first, assertThrows(RuntimeException.class, live::delete));

        assertEquals(3, shaderGl.deleteOrder.size());
        for (BuiltWorldPrograms set : sets) {
            assertTrue(set.isDeleted());
        }
        live.delete();
        assertEquals(3, shaderGl.deleteOrder.size());
    }

    @Test
    void cleanupBeforeAnyWorldPassDeletesNothing() throws Exception {
        LiveWorldPrograms live = session(dimensionPack());

        live.delete();

        assertEquals(0, shaderGl.calls);
        assertEquals(Collections.emptyList(), gl.uses);
    }

    private static final String LIT = "#version 120\nuniform sampler2D texture;\nuniform sampler2D lightmap;\n"
            + "void main() { gl_FragColor = vec4(1.0); }\n";

    // Like dimensionPack, with both samplers in every program, and world-1 declaring the lightmap as a float.
    private ShaderPack samplerPack() throws Exception {
        return TestPrograms.pack(temp, "gbuffers_terrain.vsh", VERTEX, "gbuffers_terrain.fsh", LIT,
                "world0/gbuffers_entities.vsh", VERTEX, "world0/gbuffers_entities.fsh", LIT,
                "world-1/gbuffers_terrain.vsh", VERTEX, "world-1/gbuffers_terrain.fsh",
                "#version 120\nuniform float lightmap;\nvoid main() { gl_FragColor = vec4(1.0); }\n");
    }

    @Test
    void samplersAreSetOnceAndNeverWhileDrawingOrReturning() throws Exception {
        LiveWorldPrograms live = session(samplerPack());

        drawTerrainAndEntities(live, 0);
        drawTerrainAndEntities(live, 1);
        int sets = shaderGl.uniformSets.size();
        int locations = shaderGl.locationQueries;
        int lists = shaderGl.activeUniformQueries;
        int binds = shaderGl.uses.size();
        for (int dimension : new int[] {0, 1, 0, 1}) {
            live.worldStart(dimension);
            live.stageStart(RenderStage.ENTITIES, RenderDrawKind.ENTITY_PASS_0);
            gl.current = 0;
            live.rendererReturned();
            live.stageEnd(RenderStage.ENTITIES, RenderDrawKind.ENTITY_PASS_0);
            live.worldEnd();
        }

        assertEquals(4, sets);
        assertEquals(4, locations);
        assertEquals(2, lists);
        assertEquals(sets, shaderGl.uniformSets.size());
        assertEquals(locations, shaderGl.locationQueries);
        assertEquals(lists, shaderGl.activeUniformQueries);
        assertEquals(binds, shaderGl.uses.size());
        for (DirectoryPrograms folder : live.built()) {
            for (BuiltWorldPrograms.Build build : folder.programs().builds()) {
                assertEquals(0, shaderGl.uniformValue(build.program().id(), "texture"));
                assertEquals(1, shaderGl.uniformValue(build.program().id(), "lightmap"));
            }
        }
    }

    @Test
    void aFolderWithAnUnusableSamplerLeavesTheOthersWorking() throws Exception {
        LiveWorldPrograms live = session(samplerPack());

        List<Integer> nether = drawTerrainAndEntities(live, -1);
        List<Integer> end = drawTerrainAndEntities(live, 1);

        assertEquals(Collections.emptyList(), nether);
        BuiltWorldPrograms.Build broken = live.built().iterator().next().programs().builds().get(0);
        assertEquals(ProgramFailure.Kind.INPUTS, broken.failure().kind());
        // The main folder only has terrain, so entities draw the normal way there.
        assertEquals(Arrays.asList(id(live, ShaderProgramRole.TERRAIN_SOLID), EXTERNAL), end);
        assertEquals(1, shaderGl.uniformValue(id(live, ShaderProgramRole.TERRAIN_SOLID), "lightmap"));
        assertEquals(Collections.singleton(id(live, ShaderProgramRole.TERRAIN_SOLID)), shaderGl.livePrograms);
    }

    @Test
    void aWholeWorldPassBindsAndLeavesNothingBehind() throws Exception {
        LiveWorldPrograms live = build("gbuffers_terrain", "gbuffers_clouds");
        int terrain = id(live, ShaderProgramRole.TERRAIN_SOLID);
        int clouds = id(live, ShaderProgramRole.CLOUDS);

        live.worldStart(0);
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

        live.worldStart(0);
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
        live.worldStart(0);
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

        live.worldStart(0);
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
        live.worldStart(0);
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
        live.worldStart(0);
        // Like a mod drawing entities from inside the world pass without an ENTITIES stage around it.
        live.vanillaProgramsStart(RenderStage.ENTITY_OUTLINES, RenderDrawKind.DEFAULT);
        live.vanillaProgramsEnd(RenderStage.ENTITY_OUTLINES, RenderDrawKind.DEFAULT);
        live.worldEnd();
        live.worldStart(0);
        live.vanillaProgramsStart(RenderStage.ENTITY_OUTLINES, RenderDrawKind.DEFAULT);

        assertThrows(IllegalStateException.class, live::frameEnd);

        assertFalse(scopes.isSuspended());
        assertEquals(Collections.emptyList(), gl.uses);
    }

    @Test
    void switchingDuringOutlinesDropsTheSuspensionAndThrows() throws Exception {
        LiveWorldPrograms live = build("gbuffers_entities");
        live.worldStart(0);
        live.vanillaProgramsStart(RenderStage.ENTITY_OUTLINES, RenderDrawKind.DEFAULT);

        assertThrows(IllegalStateException.class, () -> live.worldStart(-1));

        assertFalse(scopes.isSuspended());
        assertEquals(Collections.singletonList("shaders"), built);
    }

    @Test
    void cleanupDuringOutlinesDeletesWithoutBindingAnything() throws Exception {
        LiveWorldPrograms live = build("gbuffers_entities");
        int entities = id(live, ShaderProgramRole.ENTITIES);
        live.worldStart(0);
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
        LiveWorldPrograms live = session(TestPrograms.pack(temp, files("", "gbuffers_entities")));

        // Before the first world pass, and after it ended, like a mod drawing entities for a GUI.
        assertFalse(live.stageStart(RenderStage.ENTITIES, RenderDrawKind.ENTITY_PASS_0));
        live.vanillaProgramsStart(RenderStage.ENTITY_OUTLINES, RenderDrawKind.DEFAULT);
        live.vanillaProgramsEnd(RenderStage.ENTITY_OUTLINES, RenderDrawKind.DEFAULT);
        live.stageEnd(RenderStage.ENTITIES, RenderDrawKind.ENTITY_PASS_0);
        assertFalse(scopes.isSuspended());
        live.worldStart(0);
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
        live.worldStart(0);

        live.frameEnd();

        assertFalse(live.stageStart(RenderStage.TERRAIN, RenderDrawKind.TERRAIN_SOLID));
        assertEquals(Collections.emptyList(), gl.uses);
    }

    @Test
    void worldStartWithAnOpenScopeRestoresAndThrows() throws Exception {
        LiveWorldPrograms live = build("gbuffers_skybasic", "gbuffers_skytextured");
        openSkyAndSun(live);

        // Like a mod rendering another world pass from inside the sun.
        IllegalStateException error = assertThrows(IllegalStateException.class, () -> live.worldStart(0));

        assertTrue(error.getMessage().contains("when a world pass started"), error.getMessage());
        assertTrue(scopes.isEmpty());
        assertEquals(EXTERNAL, gl.current);
    }

    @Test
    void cleanupRestoresBeforeDeletingAndDeletesOnce() throws Exception {
        LiveWorldPrograms live = build("gbuffers_skybasic", "gbuffers_skytextured");
        int[] sky = openSkyAndSun(live);
        BuiltWorldPrograms programs = live.selected().programs();
        events.clear();

        live.delete();
        live.delete();

        assertEquals(Arrays.asList("use " + sky[0] + " after 0 deletes", "use " + EXTERNAL + " after 0 deletes"),
                events);
        assertEquals(Arrays.asList(sky[1], sky[0]), shaderGl.deleteOrder);
        assertTrue(programs.isDeleted());
        assertFalse(programs.forRole(ShaderProgramRole.SKY_BASIC).ready());
    }

    @Test
    void cleanupStillDeletesWhenRestoringThrows() throws Exception {
        LiveWorldPrograms live = build("gbuffers_skybasic", "gbuffers_skytextured");
        int[] sky = openSkyAndSun(live);
        BuiltWorldPrograms programs = live.selected().programs();
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
        assertTrue(programs.isDeleted());
        assertTrue(scopes.isEmpty());
    }

    @Test
    void cleanupAfterEverythingEndedOnlyDeletes() throws Exception {
        LiveWorldPrograms live = build("gbuffers_terrain");
        live.worldStart(0);
        live.worldEnd();

        live.delete();

        assertEquals(Collections.emptyList(), gl.uses);
        assertEquals(1, shaderGl.deleteOrder.size());
    }

    @Test
    void usableOnlyWithAtLeastOneBuiltProgram() throws Exception {
        assertFalse(buildFiles("gbuffers_terrain.vsh", VERTEX, "gbuffers_terrain.fsh", BROKEN).selected().usable());
        assertTrue(buildFiles("gbuffers_terrain.vsh", VERTEX, "gbuffers_terrain.fsh", BROKEN,
                "gbuffers_water.vsh", VERTEX, "gbuffers_water.fsh", FRAGMENT).selected().usable());
    }
}
