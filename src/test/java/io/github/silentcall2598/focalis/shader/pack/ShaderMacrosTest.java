// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.shader.pack;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static io.github.silentcall2598.focalis.shader.pack.PackFixtures.shaderFiles;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ShaderMacrosTest {

    @Test
    void environmentDefinesMcVersionAndNothingThatClaimsIris() {
        ShaderMacros environment = StandardMacros.environment();

        assertEquals("11202", environment.value("MC_VERSION"));
        assertFalse(environment.isDefined("IS_IRIS"));
        assertFalse(environment.asMap().keySet().stream().anyMatch(name -> name.startsWith("IRIS_")));
    }

    @Test
    void definesGoRightAfterVersionWithEnvironmentBeforeOptions() throws Exception {
        ResolvedSource source = resolve("// header\n\n#version 120\nvoid main() {}\n");

        ResolvedSource defined = source.withDefines(StandardMacros.environment(),
                ShaderMacros.empty().with("SHADOW_QUALITY", "2").with("BLOOM", ""));

        assertEquals(Arrays.asList("// header", "", "#version 120", "#define MC_VERSION 11202",
                "#define SHADOW_QUALITY 2", "#define BLOOM", "void main() {}"), lines(defined));
        assertNull(defined.origin(4).file());
        assertEquals("final.fsh:4", defined.origin(7).toString());
    }

    @Test
    void definesGoFirstWhenThereIsNoVersionLine() throws Exception {
        ResolvedSource defined = resolve("/* notes\n#version 999 in a comment\n*/\nvoid main() {}\n")
                .withDefines(StandardMacros.environment(), ShaderMacros.empty());

        assertEquals("#define MC_VERSION 11202", lines(defined).get(0));
    }

    @Test
    void definesAreNotHiddenByCommentsOnTheVersionLine() throws Exception {
        ResolvedSource closedFirst = resolve("/* license */ #version 120\nvoid main() {}\n")
                .withDefines(StandardMacros.environment(), ShaderMacros.empty());
        ResolvedSource opensAfter = resolve("#version 120 /* note\nstill note */\nvoid main() {}\n")
                .withDefines(StandardMacros.environment(), ShaderMacros.empty());

        assertEquals(Arrays.asList("/* license */ #version 120", "#define MC_VERSION 11202", "void main() {}"),
                lines(closedFirst));
        assertEquals(Arrays.asList("#version 120 ", "#define MC_VERSION 11202", "/* note", "still note */",
                "void main() {}"), lines(opensAfter));
    }

    @Test
    void optionsCannotRedefineEnvironmentMacros() throws Exception {
        ResolvedSource source = resolve("#version 120\n");

        assertThrows(IllegalArgumentException.class, () -> source.withDefines(StandardMacros.environment(),
                ShaderMacros.empty().with("MC_VERSION", "11300")));
    }

    private static ResolvedSource resolve(String text) throws IncludeException {
        return new IncludeResolver(shaderFiles("final.fsh", text)::get).resolve(ShaderPath.of("final.fsh"));
    }

    private static List<String> lines(ResolvedSource source) {
        return Arrays.asList(source.text().split("\n", -1)).subList(0, source.lineCount());
    }
}
