// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.shader.program;

import io.github.silentcall2598.focalis.shader.pack.ProgramStage;
import io.github.silentcall2598.focalis.shader.pack.ResolvedSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static io.github.silentcall2598.focalis.shader.program.TestPrograms.VERTEX;
import static io.github.silentcall2598.focalis.shader.program.TestPrograms.prepareFinal;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class DriverLogLocatorTest {

    @TempDir
    Path temp;

    // Copied from a real NVIDIA 616.92 compile of exactly this fragment shader and include.
    private static final String NVIDIA_LOG = "0(5) : error C0000: syntax error, unexpected '}', expecting ',' or ';'"
            + " at token \"}\"\n0(3) : error C1108: function \"tint\" has no statements\n";

    @Test
    void nvidiaMessagesPointBackIntoIncludedFiles() throws Exception {
        ResolvedSource fragment = prepareFinal(temp, "final.vsh", VERTEX,
                "final.fsh", "#version 120\n#include \"/lib/bad.glsl\"\nvoid main() { gl_FragColor = tint(); }\n",
                "lib/bad.glsl", "vec4 tint() {\n    return vec4(1.0)\n}\n")
                .stages().get(ProgramStage.FRAGMENT).source();

        List<DriverLogMessage> messages = DriverLogLocator.locate(NVIDIA_LOG, fragment);

        assertEquals("lib/bad.glsl:3", String.valueOf(messages.get(0).location()));
        assertEquals("error C0000: syntax error, unexpected '}', expecting ',' or ';' at token \"}\"",
                messages.get(0).message());
        assertEquals("lib/bad.glsl:1", String.valueOf(messages.get(1).location()));
    }

    @Test
    void linesThatCantBeMappedKeepTheirTextWithoutALocation() throws Exception {
        ResolvedSource vertex = prepareFinal(temp, "final.vsh", VERTEX, "final.fsh", TestPrograms.FRAGMENT)
                .stages().get(ProgramStage.VERTEX).source();

        List<DriverLogMessage> messages = DriverLogLocator.locate(
                "ERROR: 0:3: 'x' : undeclared identifier\n1(3) : error C0000: other string\n"
                        + "0(99) : error C0000: past the end\n0(2) : error C0201: unsupported version 999\n", vertex);

        assertNull(messages.get(0).location());
        assertEquals("ERROR: 0:3: 'x' : undeclared identifier", messages.get(0).message());
        assertNull(messages.get(1).location());
        assertNull(messages.get(2).location());
        // Line 2 is the MC_VERSION define Focalis added, which belongs to no pack file.
        assertNull(messages.get(3).location().file());
    }
}
