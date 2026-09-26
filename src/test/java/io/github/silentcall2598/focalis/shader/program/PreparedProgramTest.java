// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.shader.program;

import io.github.silentcall2598.focalis.shader.pack.ProgramStage;
import io.github.silentcall2598.focalis.shader.pack.ShaderMacros;
import io.github.silentcall2598.focalis.shader.pack.ShaderPack;
import io.github.silentcall2598.focalis.shader.pack.StandardMacros;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static io.github.silentcall2598.focalis.shader.program.TestPrograms.FRAGMENT;
import static io.github.silentcall2598.focalis.shader.program.TestPrograms.VERTEX;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class PreparedProgramTest {

    @TempDir
    Path temp;

    @Test
    void everyStageGetsTheEnvironmentAndOptionDefinesRightAfterVersion() throws Exception {
        ShaderPack pack = TestPrograms.pack(temp, "world-1/final.vsh", VERTEX, "world-1/final.fsh", FRAGMENT);

        PreparedProgram program = PreparedProgram.prepare(pack,
                pack.dimensionDirectories().get("world-1").find("final"), StandardMacros.environment(),
                ShaderMacros.empty().with("SHADOW_QUALITY", "2"));

        assertEquals("world-1/final", program.name());
        for (PreparedProgram.Stage stage : program.stages().values()) {
            String text = stage.source().text();
            assertEquals("#version 120\n#define MC_VERSION 11202\n#define SHADOW_QUALITY 2\n",
                    text.substring(0, text.indexOf("void")), stage.file().toString());
            assertFalse(text.contains("IS_IRIS"));
        }
        assertEquals("world-1/final.fsh", program.stages().get(ProgramStage.FRAGMENT).file().toString());
    }
}
