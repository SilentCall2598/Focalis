// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.shader.pack;

import org.junit.jupiter.api.Test;

import java.nio.file.Paths;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Loads a real pack from disk, given with {@code ./gradlew test -PsmokeTestShaderPack=<folder or zip>}. It's
 * skipped otherwise, so third-party packs never have to live in the repository.
 */
class ShaderPackSmokeTest {

    @Test
    void loadsTheGivenPackWithoutFailing() throws Exception {
        String location = System.getProperty("focalis.smokeTestShaderPack");
        assumeTrue(location != null && !location.isEmpty(), "No smoke test pack given");

        long start = System.nanoTime();
        ShaderPack pack = ShaderPackLoader.load(Paths.get(location));
        long millis = (System.nanoTime() - start) / 1_000_000;

        assertFalse(pack.root().programs().isEmpty() && pack.dimensionDirectories().isEmpty());
        System.out.println("Loaded '" + pack.name() + "' in " + millis + " ms, " + pack.files().size() + " files");
        report(pack, pack.root());
        for (Map.Entry<String, ProgramDirectory> directory : pack.dimensionDirectories().entrySet()) {
            report(pack, directory.getValue());
        }
        System.out.println("Dimension mapping: " + pack.dimensionProperties().dimensionsByFolder());
        System.out.println("Not interpreted yet: " + pack.uninterpretedMetadata());
        System.out.println(pack.issues().size() + " issues");
        for (PackIssue issue : pack.issues()) {
            System.out.println("  " + issue);
        }
    }

    private static void report(ShaderPack pack, ProgramDirectory directory) throws IncludeException {
        int largest = 0;
        for (ProgramSource program : directory.programs()) {
            if (program.problem() == null) {
                for (ProgramStage stage : program.stages().keySet()) {
                    largest = Math.max(largest, pack.resolve(program, stage).lineCount());
                }
            }
        }
        String name = directory.name().isEmpty() ? "shaders" : directory.name();
        System.out.println(name + ": " + directory.programs().size() + " programs, largest resolved stage "
                + largest + " lines");
    }
}
