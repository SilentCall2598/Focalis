// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.diagnostics.qa;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GlBoundaryTest {

    private static final int[] UNITS = {2, 3};

    private static GlBoundary boundary(int drawFramebuffer, int activeUnit, int unit2Texture, boolean blend) {
        return new GlBoundary(7, drawFramebuffer, new int[] {0, 0, 854, 480}, 0, activeUnit, UNITS,
                new int[] {unit2Texture, 0}, true, blend, true);
    }

    @Test
    void identicalStateHasNoDifferences() {
        assertTrue(boundary(7, 0, 12, false).differences(boundary(7, 0, 12, false)).isEmpty());
    }

    @Test
    void everyChangedValueIsReportedWithBothSides() {
        List<String> differences = boundary(7, 0, 12, false).differences(boundary(0, 9, 40, true));

        assertEquals(Arrays.asList("draw framebuffer 7 -> 0", "active texture unit 0 -> 9",
                "texture on unit 2 12 -> 40", "blend false -> true"), differences);
    }
}
