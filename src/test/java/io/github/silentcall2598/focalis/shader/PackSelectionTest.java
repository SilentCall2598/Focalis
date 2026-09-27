// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.shader;

import io.github.silentcall2598.focalis.shader.pack.ProgramStage;
import io.github.silentcall2598.focalis.shader.pack.ShaderPackException;
import io.github.silentcall2598.focalis.shader.program.PreparedProgram;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PackSelectionTest {

    @TempDir
    Path temp;

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
