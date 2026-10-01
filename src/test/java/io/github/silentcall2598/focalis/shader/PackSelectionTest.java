// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.shader;

import io.github.silentcall2598.focalis.shader.pack.ProgramStage;
import io.github.silentcall2598.focalis.shader.pack.ShaderMacros;
import io.github.silentcall2598.focalis.shader.pack.ShaderPack;
import io.github.silentcall2598.focalis.shader.pack.ShaderPackException;
import io.github.silentcall2598.focalis.shader.pack.ShaderPackLoader;
import io.github.silentcall2598.focalis.shader.pack.StandardMacros;
import io.github.silentcall2598.focalis.shader.program.PreparedProgram;
import io.github.silentcall2598.focalis.shader.program.PreparedWorldPrograms;
import io.github.silentcall2598.focalis.shader.routing.ShaderProgramRole;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PackSelectionTest {

    private static final String VERTEX = "#version 120\nvoid main() { gl_Position = ftransform(); }\n";
    private static final String FRAGMENT = "#version 120\nvoid main() { gl_FragColor = vec4(1.0); }\n";

    @TempDir
    Path temp;

    private static void write(Path file, String text) throws Exception {
        Files.createDirectories(file.getParent());
        Files.write(file, text.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void findsAPackByItsNameInTheShaderpacksFolder() throws Exception {
        Path shaderpacks = Files.createDirectories(temp.resolve("shaderpacks"));
        Path pack = Files.createDirectory(shaderpacks.resolve("My Pack"));

        assertEquals(pack, PackSelection.resolve(shaderpacks, " My Pack "));
    }

    @Test
    void onlyAcceptsNamesOfEntriesDirectlyInTheShaderpacksFolder() throws Exception {
        Path shaderpacks = Files.createDirectories(temp.resolve("shaderpacks"));
        Files.createDirectories(shaderpacks.resolve("a").resolve("b"));
        Files.createDirectories(temp.resolve("outside"));

        for (String name : Arrays.asList("", "  ", ".", "..", "../outside", "a/b",
                temp.resolve("outside").toAbsolutePath().toString())) {
            ShaderPackException error = assertThrows(ShaderPackException.class,
                    () -> PackSelection.resolve(shaderpacks, name), name);
            assertTrue(error.getMessage().contains("is not a shaderpack name"), error.getMessage());
        }
    }

    @Test
    void aMissingPackIsReportedByName() throws Exception {
        Path shaderpacks = Files.createDirectories(temp.resolve("shaderpacks"));

        ShaderPackException error = assertThrows(ShaderPackException.class,
                () -> PackSelection.resolve(shaderpacks, "Missing"));

        assertTrue(error.getMessage().startsWith("There is no shaderpack named 'Missing'"), error.getMessage());
    }

    @Test
    void preparesTheTestProgramFromTheDevelopmentPack() throws Exception {
        Path shaderpacks = Paths.get(getClass().getResource("/shaderpacks").toURI());

        PreparedProgram program = PackSelection.prepare(
                PackSelection.resolve(shaderpacks, "focalis-depth-view"), ShaderFeature.PROGRAM);

        assertEquals("focalis_post", program.name());
        assertEquals(EnumSet.of(ProgramStage.VERTEX, ProgramStage.FRAGMENT), program.stages().keySet());
        String fragment = program.stages().get(ProgramStage.FRAGMENT).source().text();
        assertTrue(fragment.contains("#define MC_VERSION 11202"));
        assertTrue(fragment.contains("float depthShade(float depth)"));
        assertFalse(fragment.contains("depthtex0"));
    }

    @Test
    void dimensionRoutesTestPackGivesEachDimensionItsOwnFolder() throws Exception {
        Path shaderpacks = Paths.get(getClass().getResource("/shaderpacks").toURI());
        ShaderPack pack = ShaderPackLoader.load(PackSelection.resolve(shaderpacks, "focalis-dimension-routes"));

        assertEquals("world0", pack.programDirectoryFor(0).name());
        assertEquals("world-1", pack.programDirectoryFor(-1).name());
        assertSame(pack.root(), pack.programDirectoryFor(1));
        Map<ShaderProgramRole, String> overworld = new EnumMap<>(ShaderProgramRole.class);
        for (ShaderProgramRole role : EnumSet.range(ShaderProgramRole.SKY_BASIC, ShaderProgramRole.HAND)) {
            overworld.put(role, "world0/gbuffers_textured");
        }
        overworld.remove(ShaderProgramRole.SKY_BASIC);
        overworld.put(ShaderProgramRole.ENTITIES, "world0/gbuffers_entities");
        assertEquals(overworld, preparedNames(pack, 0));
        Map<ShaderProgramRole, String> main = new EnumMap<>(ShaderProgramRole.class);
        for (ShaderProgramRole role : EnumSet.range(ShaderProgramRole.SKY_BASIC, ShaderProgramRole.HAND)) {
            main.put(role, "gbuffers_textured");
        }
        main.put(ShaderProgramRole.SKY_BASIC, "gbuffers_basic");
        for (ShaderProgramRole role : Arrays.asList(ShaderProgramRole.TERRAIN_SOLID,
                ShaderProgramRole.TERRAIN_CUTOUT_MIPPED, ShaderProgramRole.TERRAIN_CUTOUT,
                ShaderProgramRole.TERRAIN_TRANSLUCENT)) {
            main.put(role, "gbuffers_terrain");
        }
        assertEquals(main, preparedNames(pack, 1));
        // world-1 prepares fine and only fails once the driver compiles it.
        assertEquals("world-1/gbuffers_textured", preparedNames(pack, -1).get(ShaderProgramRole.TERRAIN_SOLID));
    }

    private static Map<ShaderProgramRole, String> preparedNames(ShaderPack pack, int dimension) {
        PreparedWorldPrograms programs = PreparedWorldPrograms.prepare(pack, pack.programDirectoryFor(dimension),
                StandardMacros.environment(), ShaderMacros.empty());
        Map<ShaderProgramRole, String> names = new EnumMap<>(ShaderProgramRole.class);
        for (PreparedWorldPrograms.Entry entry : programs.entries().values()) {
            assertNull(entry.problem(), entry.role().toString());
            if (entry.ready()) {
                names.put(entry.role(), entry.program().name());
            }
        }
        return names;
    }

    @Test
    void worldRoutesTestPackGivesEveryWorldStageAProgram() throws Exception {
        Path shaderpacks = Paths.get(getClass().getResource("/shaderpacks").toURI());

        ShaderPack pack = ShaderPackLoader.load(PackSelection.resolve(shaderpacks, "focalis-world-routes"));
        PreparedWorldPrograms programs = PreparedWorldPrograms.prepare(pack, pack.root(), StandardMacros.environment(),
                ShaderMacros.empty());

        Map<ShaderProgramRole, String> expected = new EnumMap<>(ShaderProgramRole.class);
        expected.put(ShaderProgramRole.SKY_BASIC, "gbuffers_skybasic");
        expected.put(ShaderProgramRole.SKY_TEXTURED, "gbuffers_skytextured");
        expected.put(ShaderProgramRole.TERRAIN_SOLID, "gbuffers_terrain");
        expected.put(ShaderProgramRole.TERRAIN_CUTOUT_MIPPED, "gbuffers_terrain");
        expected.put(ShaderProgramRole.TERRAIN_CUTOUT, "gbuffers_terrain");
        expected.put(ShaderProgramRole.TERRAIN_TRANSLUCENT, "gbuffers_water");
        expected.put(ShaderProgramRole.ENTITIES, "gbuffers_entities");
        expected.put(ShaderProgramRole.PARTICLES_LIT, "gbuffers_textured_lit");
        expected.put(ShaderProgramRole.PARTICLES_NORMAL, "gbuffers_textured");
        expected.put(ShaderProgramRole.WEATHER, "gbuffers_textured_lit");
        expected.put(ShaderProgramRole.CLOUDS, "gbuffers_clouds");
        expected.put(ShaderProgramRole.HAND, "gbuffers_textured_lit");
        Map<ShaderProgramRole, String> actual = new EnumMap<>(ShaderProgramRole.class);
        for (PreparedWorldPrograms.Entry entry : programs.entries().values()) {
            assertNull(entry.problem(), entry.role().toString());
            if (entry.ready()) {
                actual.put(entry.role(), entry.program().name());
            }
        }
        assertEquals(expected, actual);
        assertEquals(8, programs.uniquePrograms().size());
        String fragment = programs.forRole(ShaderProgramRole.TERRAIN_SOLID).program().stages()
                .get(ProgramStage.FRAGMENT).source().text();
        assertTrue(fragment.contains("gl_FragColor = texture2D(texture, texCoord) * color;"), fragment);
    }

    @Test
    void regularPacksWithoutTheTestProgramAreRefused() throws Exception {
        Path shaders = Files.createDirectories(temp.resolve("Regular").resolve("shaders"));
        Files.write(shaders.resolve("final.vsh"), "#version 120\nvoid main() {}\n".getBytes(StandardCharsets.UTF_8));
        Files.write(shaders.resolve("final.fsh"), "#version 120\nvoid main() {}\n".getBytes(StandardCharsets.UTF_8));

        ShaderPackException error = assertThrows(ShaderPackException.class,
                () -> PackSelection.prepare(temp.resolve("Regular"), ShaderFeature.PROGRAM));

        assertTrue(error.getMessage().startsWith("'Regular' has no shaders/focalis_post program."),
                error.getMessage());
    }
}
