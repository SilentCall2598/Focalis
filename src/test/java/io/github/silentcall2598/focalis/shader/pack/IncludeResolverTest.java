// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.shader.pack;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static io.github.silentcall2598.focalis.shader.pack.PackFixtures.shaderFiles;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IncludeResolverTest {

    @Test
    void relativeIncludeResolvesAgainstTheIncludingFile() throws Exception {
        ResolvedSource source = resolve("world0/final.fsh", shaderFiles(
                "world0/final.fsh", "#include \"parts/tone.glsl\"\nmain\n",
                "world0/parts/tone.glsl", "tone\n"));

        assertEquals("tone\nmain\n", source.text());
    }

    @Test
    void absoluteIncludeResolvesFromTheShadersFolder() throws Exception {
        ResolvedSource source = resolve("world0/final.fsh", shaderFiles(
                "world0/final.fsh", "#include \"/lib/common.glsl\"\n",
                "world0/lib/common.glsl", "wrong\n",
                "lib/common.glsl", "right\n"));

        assertEquals("right\n", source.text());
    }

    @Test
    void nestedIncludesExpandInPlaceAndKeepTheirOrigins() throws Exception {
        ResolvedSource source = resolve("final.fsh", shaderFiles(
                "final.fsh", "#version 120\n  # include \"/lib/a.glsl\"  // tone mapping\nmain\n",
                "lib/a.glsl", "a1\r\n#include \"b.glsl\"\r\na3\r\n",
                "lib/b.glsl", "b1"));

        assertEquals("#version 120\na1\nb1\na3\nmain\n", source.text());
        assertEquals("final.fsh:1", source.origin(1).toString());
        assertEquals("lib/a.glsl:1", source.origin(2).toString());
        assertEquals("lib/b.glsl:1", source.origin(3).toString());
        assertEquals("lib/a.glsl:3", source.origin(4).toString());
        assertEquals("final.fsh:3", source.origin(5).toString());
    }

    @Test
    void includeCycleIsRejectedWithTheWholeChain() {
        IncludeException error = assertThrows(IncludeException.class, () -> resolve("final.fsh", shaderFiles(
                "final.fsh", "#include \"/a.glsl\"\n",
                "a.glsl", "#include \"/b.glsl\"\n",
                "b.glsl", "\n#include \"/a.glsl\"\n")));

        assertTrue(error.reason().contains("a.glsl -> b.glsl -> a.glsl"), error.getMessage());
        assertEquals("b.glsl:2", String.valueOf(error.location()));
    }

    @Test
    void includeDepthIsLimitedEvenWithoutACycle() {
        String[] files = new String[(IncludeResolver.MAX_DEPTH + 1) * 2];
        for (int i = 0; i <= IncludeResolver.MAX_DEPTH; i++) {
            files[i * 2] = "level" + i + ".glsl";
            files[i * 2 + 1] = "#include \"/level" + (i + 1) + ".glsl\"\n";
        }

        IncludeException error = assertThrows(IncludeException.class, () -> resolve("level0.glsl", shaderFiles(files)));

        assertTrue(error.reason().contains("deeper than " + IncludeResolver.MAX_DEPTH), error.getMessage());
    }

    @Test
    void includeEscapingTheShadersFolderIsRejected() {
        IncludeException error = assertThrows(IncludeException.class, () -> resolve("world0/final.fsh", shaderFiles(
                "world0/final.fsh", "#include \"../../options.txt\"\n")));

        assertTrue(error.reason().contains("outside the shaders directory"), error.getMessage());
        assertEquals("world0/final.fsh:1", String.valueOf(error.location()));
    }

    @Test
    void missingAndMalformedIncludesPointAtTheDirective() {
        IncludeException missing = assertThrows(IncludeException.class, () -> resolve("final.fsh", shaderFiles(
                "final.fsh", "a\n#include \"/lib/gone.glsl\"\n")));
        IncludeException malformed = assertThrows(IncludeException.class, () -> resolve("final.fsh", shaderFiles(
                "final.fsh", "#include <lib/common.glsl>\n")));

        assertEquals("final.fsh:2", String.valueOf(missing.location()));
        assertTrue(missing.reason().contains("lib/gone.glsl"), missing.getMessage());
        assertEquals("final.fsh:1", String.valueOf(malformed.location()));
    }

    @Test
    void includeInsideABlockCommentIsLeftAlone() throws Exception {
        String text = "/*\n#include \"/lib/missing.glsl\"\n*/\n/* #include \"/lib/missing.glsl\" */\nmain\n";

        assertEquals(text, resolve("final.fsh", shaderFiles("final.fsh", text)).text());
    }

    @Test
    void includeInsideALineCommentIsLeftAlone() throws Exception {
        String text = "// #include \"/lib/missing.glsl\"\nfloat x = a / b; // #include \"/lib/missing.glsl\"\n";

        assertEquals(text, resolve("final.fsh", shaderFiles("final.fsh", text)).text());
    }

    @Test
    void realIncludeAfterABlockCommentIsExpanded() throws Exception {
        ResolvedSource source = resolve("final.fsh", shaderFiles(
                "final.fsh", "/* notes\n  more */\n#include \"/lib/a.glsl\"\nmain\n",
                "lib/a.glsl", "a\n"));

        assertEquals("/* notes\n  more */\na\nmain\n", source.text());
        assertEquals("lib/a.glsl:1", source.origin(3).toString());
    }

    @Test
    void commentsSharingTheDirectiveLineKeepTheirStartAndEnd() throws Exception {
        String a = "lib/a.glsl";
        ResolvedSource closedFirst = resolve("final.fsh", shaderFiles(
                "final.fsh", "/* tone */ #include \"/lib/a.glsl\"\n", a, "a\n"));
        ResolvedSource endsOnLine = resolve("final.fsh", shaderFiles(
                "final.fsh", "/* long\nnote */ #include \"/lib/a.glsl\"\nmain\n", a, "a\n"));
        ResolvedSource opensOnLine = resolve("final.fsh", shaderFiles(
                "final.fsh", "#include \"/lib/a.glsl\" /* start\n#include \"/lib/missing.glsl\"\n*/\nmain\n",
                a, "a\n"));

        assertEquals("a\n", closedFirst.text());
        assertEquals("/* long\nnote */ \na\nmain\n", endsOnLine.text());
        assertEquals("final.fsh:2", endsOnLine.origin(2).toString());
        assertEquals("a\n/* start\n#include \"/lib/missing.glsl\"\n*/\nmain\n", opensOnLine.text());
    }

    @Test
    void commentMarkersInPathsAndOddSpotsAreHandled() throws Exception {
        // "//" inside a quoted path isn't a comment, and "/*/" opens a comment without closing it.
        ResolvedSource source = resolve("final.fsh", shaderFiles(
                "final.fsh", "#include \"/lib//a.glsl\" /* why */\n/*/\n#include \"/lib/missing.glsl\"\n*/\n",
                "lib/a.glsl", "a\n"));

        assertEquals("a\n/*/\n#include \"/lib/missing.glsl\"\n*/\n", source.text());
    }

    private static ResolvedSource resolve(String file, Map<ShaderPath, String> files) throws IncludeException {
        return new IncludeResolver(files::get).resolve(ShaderPath.of(file));
    }
}
