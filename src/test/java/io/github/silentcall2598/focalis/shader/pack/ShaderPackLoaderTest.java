// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.shader.pack;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static io.github.silentcall2598.focalis.shader.pack.PackFixtures.FRAGMENT;
import static io.github.silentcall2598.focalis.shader.pack.PackFixtures.VERTEX;
import static io.github.silentcall2598.focalis.shader.pack.PackFixtures.directoryPack;
import static io.github.silentcall2598.focalis.shader.pack.PackFixtures.files;
import static io.github.silentcall2598.focalis.shader.pack.PackFixtures.zipPack;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class ShaderPackLoaderTest {

    @TempDir
    Path temp;

    @Test
    void loadsTheDevelopmentPackFromAFolder() throws Exception {
        Path folder = Paths.get(getClass().getResource("/shaderpacks/focalis-depth-view").toURI());
        ShaderPack pack = ShaderPackLoader.load(folder);

        ProgramSource fin = pack.root().find("final");
        assertNotNull(fin);
        assertEquals(Arrays.asList(ProgramStage.VERTEX, ProgramStage.FRAGMENT), stages(fin));
        assertNull(fin.problem());
        assertTrue(pack.issues().isEmpty(), pack.issues().toString());

        ResolvedSource fragment = pack.resolve(fin, ProgramStage.FRAGMENT);
        int includedLine = lineContaining(fragment, "float linearDepth");
        assertEquals("lib/depth.glsl", String.valueOf(fragment.origin(includedLine).file()));
    }

    @Test
    void loadsAZipPackAndStopsReadingIt() throws Exception {
        Path zip = zipPack(temp, "Tiny.zip", files(
                "shaders/final.vsh", VERTEX,
                "shaders/final.fsh", "#version 120\n#include \"/lib/color.glsl\"\n",
                "shaders/lib/color.glsl", "vec3 tint;\n",
                "readme.txt", "not part of the shaders folder"));

        ShaderPack pack = ShaderPackLoader.load(zip);
        Files.delete(zip);

        assertEquals("Tiny.zip", pack.name());
        ProgramSource fin = pack.root().find("final");
        assertNotNull(fin);
        assertEquals("#version 120\nvec3 tint;\n", pack.resolve(fin, ProgramStage.FRAGMENT).text());
    }

    @Test
    void rejectsZipEntriesThatEscapeThePack() throws Exception {
        Path parentEscape = zipPack(temp, "escape.zip", files(
                "shaders/final.vsh", VERTEX, "shaders/final.fsh", FRAGMENT, "../outside.fsh", FRAGMENT));
        Path nestedEscape = zipPack(temp, "nested.zip", files(
                "shaders/final.vsh", VERTEX, "shaders/../../outside.fsh", FRAGMENT));

        assertTrue(assertThrows(ShaderPackException.class, () -> ShaderPackLoader.load(parentEscape))
                .getMessage().contains("unsafe entry"));
        assertTrue(assertThrows(ShaderPackException.class, () -> ShaderPackLoader.load(nestedEscape))
                .getMessage().contains("unsafe entry"));
    }

    @Test
    void explainsAShadersFolderNestedOneLevelTooDeep() throws Exception {
        Path zip = zipPack(temp, "Pack-main.zip", files("Pack-main/shaders/final.fsh", FRAGMENT));

        ShaderPackException error = assertThrows(ShaderPackException.class, () -> ShaderPackLoader.load(zip));

        assertTrue(error.getMessage().contains("at Pack-main/shaders "), error.getMessage());
    }

    @Test
    void missingProgramsAreAbsentAndHalfWrittenOnesAreReported() throws Exception {
        ShaderPack pack = ShaderPackLoader.load(directoryPack(temp, "partial", files(
                "shaders/final.vsh", VERTEX, "shaders/final.fsh", FRAGMENT,
                "shaders/composite.fsh", FRAGMENT)));

        assertNull(pack.root().find("gbuffers_terrain"));
        assertEquals(Collections.singletonList(ProgramStage.FRAGMENT), stages(pack.root().find("composite")));
        List<String> issues = issueTexts(pack);
        assertTrue(issues.stream().anyMatch(issue -> issue.contains("composite needs both")), issues.toString());
    }

    @Test
    void aBrokenIncludeOnlyDisablesItsOwnProgram() throws Exception {
        ShaderPack pack = ShaderPackLoader.load(directoryPack(temp, "broken", files(
                "shaders/final.vsh", VERTEX, "shaders/final.fsh", FRAGMENT,
                "shaders/composite.vsh", VERTEX,
                "shaders/composite.fsh", "#version 120\n#include \"/lib/missing.glsl\"\n")));

        assertNull(pack.root().find("final").problem());
        assertNotNull(pack.root().find("composite").problem());
        PackIssue issue = pack.issues().get(0);
        assertEquals("composite.fsh:2", String.valueOf(issue.location()));
    }

    @Test
    void findsDimensionFoldersAndIgnoresOtherFolders() throws Exception {
        ShaderPack pack = ShaderPackLoader.load(directoryPack(temp, "dimensions", files(
                "shaders/final.vsh", VERTEX, "shaders/final.fsh", FRAGMENT,
                "shaders/world0/gbuffers_terrain.fsh", FRAGMENT, "shaders/world0/gbuffers_terrain.vsh", VERTEX,
                "shaders/world-1/final.fsh", FRAGMENT, "shaders/world-1/final.vsh", VERTEX,
                "shaders/world0/extra/final.fsh", FRAGMENT,
                "shaders/program/final.fsh", FRAGMENT,
                "shaders/aether/final.fsh", FRAGMENT, "shaders/aether/final.vsh", VERTEX,
                "shaders/dimension.properties", "dimension.aether=aether:the_aether\n")));

        assertEquals(Arrays.asList("aether", "world-1", "world0"), sorted(pack.dimensionDirectories().keySet()));
        assertEquals(Integer.valueOf(-1), pack.dimensionDirectories().get("world-1").worldId());
        assertNull(pack.dimensionDirectories().get("aether").worldId());
        assertNotNull(pack.dimensionDirectories().get("world0").find("gbuffers_terrain"));
        assertNull(pack.root().find("gbuffers_terrain"));
        assertEquals(Collections.singletonList("aether:the_aether"),
                pack.dimensionProperties().dimensionsByFolder().get("aether"));
    }

    @Test
    void malformedDimensionPropertiesLinesAreReportedAndSkipped() throws Exception {
        ShaderPack pack = ShaderPackLoader.load(directoryPack(temp, "bad-dimensions", files(
                "shaders/final.vsh", VERTEX, "shaders/final.fsh", FRAGMENT,
                "shaders/world1/final.vsh", VERTEX, "shaders/world1/final.fsh", FRAGMENT,
                "shaders/dimension.properties", "# comment\nnot a mapping\ndimension.=x\ndimension.world2=\n"
                        + "dimension.../up=x\ndimension.world1=minecraft:the_end\n")));

        List<String> issues = issueTexts(pack);
        assertEquals(4, issues.size(), issues.toString());
        assertTrue(issues.get(0).startsWith("dimension.properties:2"), issues.toString());
        assertEquals(Collections.singleton("world1"), pack.dimensionProperties().dimensionsByFolder().keySet());
    }

    @Test
    void dimensionPropertiesDirectivesAreReportedInsteadOfEvaluated() throws Exception {
        ShaderPack pack = ShaderPackLoader.load(directoryPack(temp, "directives", files(
                "shaders/world-1/final.vsh", VERTEX, "shaders/world-1/final.fsh", FRAGMENT,
                "shaders/world1/final.vsh", VERTEX, "shaders/world1/final.fsh", FRAGMENT,
                "shaders/dimension.properties", "#ifdef NETHER_ONLY\ndimension.world-1=minecraft:the_nether\n#else\n"
                        + "dimension.world1=minecraft:the_end\n#endif\n")));

        List<String> issues = issueTexts(pack);
        assertEquals(1, issues.size(), issues.toString());
        assertTrue(issues.get(0).startsWith("dimension.properties:1"), issues.toString());
        assertEquals(Arrays.asList("world-1", "world1"),
                sorted(pack.dimensionProperties().dimensionsByFolder().keySet()));
    }

    @Test
    void keepsMetadataItDoesNotInterpretYet() throws Exception {
        ShaderPack pack = ShaderPackLoader.load(directoryPack(temp, "metadata", files(
                "shaders/final.vsh", VERTEX, "shaders/final.fsh", FRAGMENT,
                "shaders/shaders.properties", "sliders=EXPOSURE\n",
                "shaders/textures/noise.png", "not really a png")));

        ShaderPath properties = ShaderPath.of("shaders.properties");
        assertEquals(Collections.singleton(properties), pack.uninterpretedMetadata());
        assertEquals("sliders=EXPOSURE\n", pack.text(properties));
        assertTrue(pack.files().contains(ShaderPath.of("textures/noise.png")));
        assertNull(pack.text(ShaderPath.of("textures/noise.png")));
    }

    @Test
    void dropsAByteOrderMarkAtTheStartOfAFile() throws Exception {
        String byteOrderMark = String.valueOf((char) 0xFEFF);
        ShaderPack pack = ShaderPackLoader.load(directoryPack(temp, "bom", files(
                "shaders/final.vsh", VERTEX, "shaders/final.fsh", byteOrderMark + FRAGMENT)));

        String fragment = pack.resolve(pack.root().find("final"), ProgramStage.FRAGMENT).text();

        assertTrue(fragment.startsWith("#version 120"), fragment);
    }

    @Test
    void rejectsAFolderPackWithoutPrograms() throws Exception {
        Path empty = directoryPack(temp, "empty", files("shaders/lib/common.glsl", "float x;\n"));
        Path noShaders = directoryPack(temp, "no-shaders", files("readme.txt", "hi"));

        assertThrows(ShaderPackException.class, () -> ShaderPackLoader.load(empty));
        assertThrows(ShaderPackException.class, () -> ShaderPackLoader.load(noShaders));
    }

    @Test
    void rejectsALinkThatPointsOutsideTheShadersFolder() throws Exception {
        Path pack = directoryPack(temp, "linked", files(
                "shaders/final.vsh", VERTEX, "shaders/final.fsh", FRAGMENT));
        Path outside = Files.write(temp.resolve("secret.txt"), "secret".getBytes(StandardCharsets.UTF_8));
        assumeTrue(createLink(pack.resolve("shaders/lib.glsl"), outside), "creating symbolic links isn't allowed here");

        ShaderPackException error = assertThrows(ShaderPackException.class, () -> ShaderPackLoader.load(pack));

        assertTrue(error.getMessage().contains("links outside"), error.getMessage());
    }

    private static boolean createLink(Path link, Path target) {
        try {
            Files.createSymbolicLink(link, target);
            return true;
        } catch (IOException | UnsupportedOperationException e) {
            return false;
        }
    }

    private static List<ProgramStage> stages(ProgramSource program) {
        return program.stages().keySet().stream().collect(Collectors.toList());
    }

    private static List<String> issueTexts(ShaderPack pack) {
        return pack.issues().stream().map(PackIssue::toString).collect(Collectors.toList());
    }

    private static List<String> sorted(Collection<String> values) {
        return values.stream().sorted().collect(Collectors.toList());
    }

    private static int lineContaining(ResolvedSource source, String text) {
        String[] lines = source.text().split("\n");
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].contains(text)) {
                return i + 1;
            }
        }
        throw new AssertionError("No line contains " + text);
    }
}
