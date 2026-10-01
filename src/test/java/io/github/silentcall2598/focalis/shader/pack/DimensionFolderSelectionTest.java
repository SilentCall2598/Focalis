// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.shader.pack;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static io.github.silentcall2598.focalis.shader.pack.PackFixtures.FRAGMENT;
import static io.github.silentcall2598.focalis.shader.pack.PackFixtures.VERTEX;
import static io.github.silentcall2598.focalis.shader.pack.PackFixtures.directoryPack;
import static io.github.silentcall2598.focalis.shader.pack.PackFixtures.files;
import static io.github.silentcall2598.focalis.shader.pack.PackFixtures.zipPack;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class DimensionFolderSelectionTest {

    @TempDir
    Path temp;

    // A program in each of these folders, so every one of them exists in the pack.
    private static Map<String, String> programsIn(String... folders) {
        List<String> pathsAndTexts = new ArrayList<>();
        for (String folder : folders) {
            String prefix = folder.isEmpty() ? "shaders/" : "shaders/" + folder + "/";
            pathsAndTexts.addAll(Arrays.asList(prefix + "gbuffers_basic.vsh", VERTEX,
                    prefix + "gbuffers_basic.fsh", FRAGMENT));
        }
        return files(pathsAndTexts.toArray(new String[0]));
    }

    private ShaderPack folderPack(Map<String, String> files) throws Exception {
        return ShaderPackLoader.load(directoryPack(temp, "pack", files));
    }

    @Test
    void eachDimensionUsesTheFolderNamedExactlyAfterIt() throws Exception {
        ShaderPack pack = folderPack(programsIn("", "world0", "world-1", "world7", "world-42"));

        assertSame(pack.dimensionDirectories().get("world0"), pack.programDirectoryFor(0));
        assertSame(pack.dimensionDirectories().get("world-1"), pack.programDirectoryFor(-1));
        assertSame(pack.dimensionDirectories().get("world7"), pack.programDirectoryFor(7));
        assertSame(pack.dimensionDirectories().get("world-42"), pack.programDirectoryFor(-42));
        assertEquals("shaders/world-1", pack.programDirectoryFor(-1).path());
    }

    @Test
    void dimensionsWithoutAFolderUseTheShadersFolder() throws Exception {
        ShaderPack pack = folderPack(programsIn("", "world0"));

        assertSame(pack.root(), pack.programDirectoryFor(1));
        assertSame(pack.root(), pack.programDirectoryFor(-1));
        assertSame(pack.root(), pack.programDirectoryFor(Integer.MIN_VALUE));
        assertEquals("shaders", pack.programDirectoryFor(1).path());
    }

    @Test
    void world0IsOnlyForDimension0() throws Exception {
        ShaderPack pack = folderPack(programsIn("", "world0"));

        assertSame(pack.root(), pack.programDirectoryFor(1));
        assertSame(pack.root(), pack.programDirectoryFor(10));
    }

    @Test
    void foldersThatOnlyLookLikeADimensionAreNeverSelected() throws Exception {
        ShaderPack pack = folderPack(programsIn("", "world07", "world-0", "World1", "world+2", "overworld", "nether",
                "world1_old"));

        for (int dimension : new int[] {0, 1, 2, 7, -1}) {
            assertSame(pack.root(), pack.programDirectoryFor(dimension), "dimension " + dimension);
        }
    }

    @Test
    void foldersNamedInDimensionPropertiesAreNotSelectedYet() throws Exception {
        Map<String, String> files = programsIn("", "overworld");
        files.put("shaders/dimension.properties", "dimension.overworld=minecraft:overworld\n");
        ShaderPack pack = folderPack(files);

        assertNotNull(pack.dimensionDirectories().get("overworld"));
        assertSame(pack.root(), pack.programDirectoryFor(0));
    }

    @Test
    void anEmptyWorldFolderStillReplacesTheShadersFolder() throws Exception {
        Path packDir = directoryPack(temp, "pack", programsIn(""));
        Files.createDirectories(packDir.resolve("shaders").resolve("world1"));
        ShaderPack pack = ShaderPackLoader.load(packDir);

        ProgramDirectory end = pack.programDirectoryFor(1);
        assertEquals("world1", end.name());
        assertTrue(end.programs().isEmpty());
        assertSame(pack.root(), pack.programDirectoryFor(0));
    }

    @Test
    void aWorldFolderWithOnlyOtherFilesStillReplacesTheShadersFolder() throws Exception {
        Map<String, String> files = programsIn("");
        files.put("shaders/world-1/textures/noise.png", "png");
        files.put("shaders/world-1/notes.txt", "nothing here");
        ShaderPack pack = folderPack(files);

        assertEquals("world-1", pack.programDirectoryFor(-1).name());
        assertTrue(pack.programDirectoryFor(-1).programs().isEmpty());
    }

    @Test
    void emptyFoldersThatAreNotExactWorldFoldersAreIgnored() throws Exception {
        Path packDir = directoryPack(temp, "pack", programsIn(""));
        for (String folder : Arrays.asList("world01", "lib", "World2", "world")) {
            Files.createDirectories(packDir.resolve("shaders").resolve(folder));
        }
        Files.createDirectories(packDir.resolve("shaders").resolve("lib").resolve("world3"));
        ShaderPack pack = ShaderPackLoader.load(packDir);

        assertEquals(Collections.emptySet(), pack.dimensionDirectories().keySet());
        assertSame(pack.root(), pack.programDirectoryFor(1));
        assertSame(pack.root(), pack.programDirectoryFor(3));
    }

    @Test
    void aPackWithProgramsOnlyInAWorldFolderLoads() throws Exception {
        ShaderPack pack = folderPack(programsIn("world-1"));

        assertTrue(pack.root().programs().isEmpty());
        assertNotNull(pack.programDirectoryFor(-1).find("gbuffers_basic"));
        assertSame(pack.root(), pack.programDirectoryFor(0));
    }

    @Test
    void aPackWithOnlyEmptyWorldFoldersIsStillRejected() throws Exception {
        Path packDir = directoryPack(temp, "pack", files("shaders/lib/common.glsl", "float x;\n"));
        Files.createDirectories(packDir.resolve("shaders").resolve("world0"));

        assertThrows(ShaderPackException.class, () -> ShaderPackLoader.load(packDir));
    }

    @Test
    void zipFolderEntriesCountAsPresentFolders() throws Exception {
        Map<String, String> entries = programsIn("");
        entries.put("shaders/world7/", "");
        entries.put("shaders/world0/textures/", "");
        entries.put("shaders/World1/", "");
        ShaderPack pack = ShaderPackLoader.load(zipPack(temp, "pack.zip", entries));

        assertEquals("world7", pack.programDirectoryFor(7).name());
        assertTrue(pack.programDirectoryFor(7).programs().isEmpty());
        assertEquals("world0", pack.programDirectoryFor(0).name());
        assertSame(pack.root(), pack.programDirectoryFor(1));
    }

    @Test
    void zipFoldersAreAlsoFoundFromTheFilesInThem() throws Exception {
        Map<String, String> entries = programsIn("", "world0");
        entries.put("shaders/world-1/textures/noise.png", "png");
        ShaderPack pack = ShaderPackLoader.load(zipPack(temp, "pack.zip", entries));

        assertNotNull(pack.programDirectoryFor(0).find("gbuffers_basic"));
        assertEquals("world-1", pack.programDirectoryFor(-1).name());
        assertTrue(pack.programDirectoryFor(-1).programs().isEmpty());
        assertSame(pack.root(), pack.programDirectoryFor(1));
    }

    @Test
    void zipFoldersOutsideTheShadersFolderDoNotCount() throws Exception {
        Map<String, String> entries = programsIn("");
        entries.put("world1/", "");
        entries.put("other/shaders/world2/", "");
        entries.put("shaders/lib/world3/", "");
        ShaderPack pack = ShaderPackLoader.load(zipPack(temp, "pack.zip", entries));

        assertEquals(Collections.emptySet(), pack.dimensionDirectories().keySet());
    }

    @Test
    void zipFolderEntriesThatEscapeThePackAreStillRejected() throws Exception {
        Map<String, String> entries = programsIn("");
        entries.put("shaders/../../world1/", "");

        assertThrows(ShaderPackException.class, () -> ShaderPackLoader.load(zipPack(temp, "pack.zip", entries)));
    }

    @Test
    void dimensionPropertiesStillReportsAListedFolderWithoutPrograms() throws Exception {
        Path packDir = directoryPack(temp, "pack", files("shaders/gbuffers_basic.vsh", VERTEX,
                "shaders/gbuffers_basic.fsh", FRAGMENT,
                "shaders/dimension.properties", "dimension.world1=minecraft:the_end\n"));
        Files.createDirectories(packDir.resolve("shaders").resolve("world1"));
        ShaderPack pack = ShaderPackLoader.load(packDir);

        assertEquals(1, pack.issues().size(), pack.issues().toString());
        assertTrue(pack.issues().get(0).toString().contains("world1, which has no programs"),
                pack.issues().toString());
        assertNull(pack.dimensionDirectories().get("world1").find("gbuffers_basic"));
    }

    @Test
    void zipFolderEntriesWithBackslashesCountAsFolders() throws Exception {
        Map<String, String> entries = programsIn("");
        entries.put("shaders\\world1\\", "");
        ShaderPack pack = ShaderPackLoader.load(zipPack(temp, "pack.zip", entries));

        assertEquals("world1", pack.programDirectoryFor(1).name());
        assertTrue(pack.programDirectoryFor(1).programs().isEmpty());
        assertFalse(pack.files().contains(ShaderPath.of("world1")));
    }

    @Test
    void aLinkedWorldFolderCountsAsMissing() throws Exception {
        Path packDir = directoryPack(temp, "pack", programsIn("", "world0"));
        Path shaders = packDir.resolve("shaders");
        try {
            Files.createSymbolicLink(shaders.resolve("world-1"), shaders.resolve("world0"));
        } catch (IOException | UnsupportedOperationException e) {
            assumeTrue(false, "symbolic links aren't available here");
        }
        ShaderPack pack = ShaderPackLoader.load(packDir);

        assertSame(pack.root(), pack.programDirectoryFor(-1));
        assertNull(pack.dimensionDirectories().get("world-1"));
    }
}
