// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.shader.program;

import io.github.silentcall2598.focalis.render.lifecycle.RenderDrawKind;
import io.github.silentcall2598.focalis.render.lifecycle.RenderStage;
import io.github.silentcall2598.focalis.shader.routing.ShaderProgramRole;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static io.github.silentcall2598.focalis.shader.program.TestPrograms.VERTEX;
import static io.github.silentcall2598.focalis.shader.program.WorldProgramInputsTest.FRAME;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** How the frame uniforms reach the programs while world passes bind them. */
class FrameUniformBindingTest {

    private static final ShaderCapabilities EVERYTHING = new ShaderCapabilities(true, true, true);
    private static final int EXTERNAL = 7;
    private static final String FRAME_LIT = "#version 120\nuniform sampler2D texture;\nuniform sampler2D lightmap;\n"
            + "uniform float viewWidth;\nuniform int frameCounter;\nuniform float frameTimeCounter;\n"
            + "void main() { gl_FragColor = vec4(1.0); }\n";
    private static final long MS = 1_000_000L;

    @TempDir
    Path temp;

    // Binds the programs too, so every uniform lands on the program the scopes made current.
    private final RecordingShaderGl gl = new RecordingShaderGl();
    private final FrameInputs frame = new FrameInputs();
    private long now;
    private LiveWorldPrograms live;

    FrameUniformBindingTest() {
        gl.current = EXTERNAL;
        gl.failCompileWhenSourceContains = "BROKEN";
    }

    private void session(String... pathsAndTexts) throws Exception {
        live = LiveWorldPrograms.create(TestPrograms.pack(temp, pathsAndTexts), new ProgramBuilder(EVERYTHING, gl),
                programs -> {
                }, new ScopedProgramBinding(gl), frame);
    }

    private static String[] programs(String fragment, String... names) {
        List<String> files = new ArrayList<>();
        for (String name : names) {
            files.addAll(Arrays.asList(name + ".vsh", VERTEX, name + ".fsh", fragment));
        }
        return files.toArray(new String[0]);
    }

    // The next frame starts 16 ms after the last one.
    private void nextFrame() {
        frame.capture(now, 1280, 720);
        now += 16 * MS;
    }

    private void draw(RenderStage stage, RenderDrawKind kind) {
        live.stageStart(stage, kind);
        live.stageEnd(stage, kind);
    }

    private BuiltWorldPrograms selected() {
        return live.selected().programs();
    }

    private ShaderProgram program(ShaderProgramRole role) {
        ShaderProgram program = selected().forRole(role).program();
        assertNotNull(program, role.toString());
        return program;
    }

    private WorldProgramInputs inputs(ShaderProgramRole role) {
        return selected().forRole(role).build().inputs();
    }

    private int setsOf(ShaderProgram program) {
        int sets = 0;
        for (String set : gl.uniformSets) {
            sets += set.startsWith(program.id() + ":") ? 1 : 0;
        }
        return sets;
    }

    private float[] held(ShaderProgram program) {
        int id = program.id();
        return new float[] {gl.floatValue(id, "viewWidth"), gl.floatValue(id, "viewHeight"),
                gl.floatValue(id, "aspectRatio"), gl.uniformValue(id, "frameCounter"), gl.floatValue(id, "frameTime"),
                gl.floatValue(id, "frameTimeCounter")};
    }

    private float[] expected() {
        return new float[] {frame.viewWidth(), frame.viewHeight(), frame.aspectRatio(), frame.frameCounter(),
                frame.frameTime(), frame.frameTimeCounter()};
    }

    @Test
    void everyProgramAndWorldPassInAFrameGetsTheSameValues() throws Exception {
        session(programs(FRAME, "gbuffers_terrain", "gbuffers_entities"));
        for (int i = 0; i < 3; i++) {
            nextFrame();
        }

        live.worldStart(0);
        draw(RenderStage.TERRAIN, RenderDrawKind.TERRAIN_SOLID);
        draw(RenderStage.ENTITIES, RenderDrawKind.ENTITY_PASS_0);
        live.worldEnd();
        // Anaglyph draws the world twice in one frame.
        live.worldStart(0);
        draw(RenderStage.ENTITIES, RenderDrawKind.ENTITY_PASS_0);
        draw(RenderStage.TERRAIN, RenderDrawKind.TERRAIN_SOLID);
        live.worldEnd();

        ShaderProgram terrain = program(ShaderProgramRole.TERRAIN_SOLID);
        ShaderProgram entities = program(ShaderProgramRole.ENTITIES);
        assertArrayEquals(expected(), held(terrain));
        assertArrayEquals(expected(), held(entities));
        assertEquals(2, gl.uniformValue(terrain.id(), "frameCounter"));
        assertEquals(6, setsOf(terrain));
        assertEquals(6, setsOf(entities));
        assertEquals(0, gl.uniformErrors);
    }

    @Test
    void bindingAProgramAgainInTheSameFrameSetsNothing() throws Exception {
        session(programs(FRAME, "gbuffers_terrain", "gbuffers_entities"));
        nextFrame();
        live.worldStart(0);
        ShaderProgram terrain = program(ShaderProgramRole.TERRAIN_SOLID);
        ShaderProgram entities = program(ShaderProgramRole.ENTITIES);

        for (RenderDrawKind layer : Arrays.asList(RenderDrawKind.TERRAIN_SOLID, RenderDrawKind.TERRAIN_CUTOUT_MIPPED,
                RenderDrawKind.TERRAIN_CUTOUT)) {
            draw(RenderStage.TERRAIN, layer);
        }
        live.stageStart(RenderStage.ENTITIES, RenderDrawKind.ENTITY_PASS_0);
        // A renderer leaves its own program bound, and vanilla's outlines bind theirs.
        gl.current = 99;
        assertTrue(live.rendererReturned());
        live.vanillaProgramsStart(RenderStage.ENTITY_OUTLINES, RenderDrawKind.DEFAULT);
        live.vanillaProgramsEnd(RenderStage.ENTITY_OUTLINES, RenderDrawKind.DEFAULT);
        live.stageEnd(RenderStage.ENTITIES, RenderDrawKind.ENTITY_PASS_0);
        draw(RenderStage.TRANSLUCENT, RenderDrawKind.TERRAIN_TRANSLUCENT);
        live.worldEnd();

        assertEquals(6, setsOf(terrain));
        assertEquals(6, setsOf(entities));
        assertEquals(EXTERNAL, gl.current);
    }

    @Test
    void aNestedScopeWithTheSameProgramSetsNothingMore() throws Exception {
        // Both sky roles fall back to gbuffers_basic.
        session(programs(FRAME, "gbuffers_basic"));
        nextFrame();
        live.worldStart(0);
        ShaderProgram basic = program(ShaderProgramRole.SKY_BASIC);

        live.stageStart(RenderStage.SKY, RenderDrawKind.SKY_BASIC);
        draw(RenderStage.SKY, RenderDrawKind.SKY_TEXTURED);
        live.stageEnd(RenderStage.SKY, RenderDrawKind.SKY_BASIC);
        live.worldEnd();

        assertSame(basic, program(ShaderProgramRole.SKY_TEXTURED));
        assertEquals(6, setsOf(basic));
        assertEquals(Arrays.asList(basic.id(), EXTERNAL), gl.uses);
    }

    @Test
    void aProgramFirstUsedLaterGetsThatFramesValuesAndUnusedOnesGetNothing() throws Exception {
        session(programs(FRAME, "gbuffers_terrain", "gbuffers_entities"));
        nextFrame();
        live.worldStart(0);
        draw(RenderStage.TERRAIN, RenderDrawKind.TERRAIN_SOLID);
        live.worldEnd();
        ShaderProgram entities = program(ShaderProgramRole.ENTITIES);
        assertEquals(0, setsOf(entities));
        assertEquals(0, inputs(ShaderProgramRole.ENTITIES).updatedFrame());

        // Frames without a world still count.
        for (int i = 0; i < 4; i++) {
            nextFrame();
            live.worldStartWithoutWorld();
            draw(RenderStage.ENTITIES, RenderDrawKind.ENTITY_PASS_0);
            live.worldEnd();
        }
        assertEquals(0, setsOf(entities));
        nextFrame();
        live.worldStart(0);
        draw(RenderStage.ENTITIES, RenderDrawKind.ENTITY_PASS_0);
        live.worldEnd();

        assertEquals(5, gl.uniformValue(entities.id(), "frameCounter"));
        assertArrayEquals(expected(), held(entities));
        assertEquals(0, gl.uniformValue(program(ShaderProgramRole.TERRAIN_SOLID).id(), "frameCounter"));
    }

    @Test
    void aCachedProgramGetsCurrentValuesWhenItsDimensionComesBack() throws Exception {
        List<String> files = new ArrayList<>(Arrays.asList(programs(FRAME_LIT, "gbuffers_terrain")));
        files.addAll(Arrays.asList(programs(FRAME_LIT, "world-1/gbuffers_terrain")));
        files.addAll(Arrays.asList("world1/gbuffers_terrain.vsh", VERTEX, "world1/gbuffers_terrain.fsh",
                "#version 120\nBROKEN\n"));
        session(files.toArray(new String[0]));

        nextFrame();
        live.worldStart(0);
        draw(RenderStage.TERRAIN, RenderDrawKind.TERRAIN_SOLID);
        live.worldEnd();
        ShaderProgram overworld = program(ShaderProgramRole.TERRAIN_SOLID);
        int locations = gl.locationQueries;

        nextFrame();
        live.worldStart(-1);
        draw(RenderStage.TERRAIN, RenderDrawKind.TERRAIN_SOLID);
        live.worldEnd();
        ShaderProgram nether = program(ShaderProgramRole.TERRAIN_SOLID);
        assertEquals(1, gl.uniformValue(nether.id(), "frameCounter"));
        // The End's folder fails to build and draws the normal way, which leaves the frame alone.
        nextFrame();
        live.worldStart(1);
        draw(RenderStage.TERRAIN, RenderDrawKind.TERRAIN_SOLID);
        live.worldEnd();
        nextFrame();
        live.worldStart(0);
        draw(RenderStage.TERRAIN, RenderDrawKind.TERRAIN_SOLID);
        live.worldEnd();

        assertSame(overworld, program(ShaderProgramRole.TERRAIN_SOLID));
        assertEquals(3, gl.uniformValue(overworld.id(), "frameCounter"));
        assertEquals(frame.frameTimeCounter(), gl.floatValue(overworld.id(), "frameTimeCounter"));
        assertEquals(1, gl.uniformValue(nether.id(), "frameCounter"));
        assertEquals(3, live.built().size());
        // Only the nether's build looked anything up after the first frame.
        assertEquals(locations + 5, gl.locationQueries);
        for (ShaderProgram program : Arrays.asList(overworld, nether)) {
            assertEquals(0, gl.uniformValue(program.id(), "texture"));
            assertEquals(1, gl.uniformValue(program.id(), "lightmap"));
        }
        assertEquals(0, gl.uniformErrors);
    }

    @Test
    void aFailedUpdateUnwindsEveryScopeAndLeavesTheProgramUnmarked() throws Exception {
        session(programs(FRAME, "gbuffers_skybasic", "gbuffers_skytextured"));
        nextFrame();
        live.worldStart(0);
        WorldProgramInputs sun = inputs(ShaderProgramRole.SKY_TEXTURED);
        live.stageStart(RenderStage.SKY, RenderDrawKind.SKY_BASIC);
        IllegalStateException failure = new IllegalStateException("uniform");
        IllegalStateException restore = new IllegalStateException("restore");
        gl.uniformThrows = failure;
        gl.uniformThrowsFromCall = gl.uniformCalls + 2;
        // The bind of the sun's program, then putting the sky's back, which fails, then the external one.
        gl.useThrowsOnCall.put(gl.uses.size() + 2, restore);

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> live.stageStart(RenderStage.SKY, RenderDrawKind.SKY_TEXTURED));

        assertSame(failure, thrown);
        assertArrayEquals(new Throwable[] {restore}, thrown.getSuppressed());
        assertEquals(0, sun.updatedFrame());
        assertEquals(EXTERNAL, gl.current);
        // The feature fails next, which deletes everything without leaving a program bound.
        live.delete();
        assertTrue(gl.livePrograms.isEmpty());
        assertEquals(EXTERNAL, gl.current);
    }
}
