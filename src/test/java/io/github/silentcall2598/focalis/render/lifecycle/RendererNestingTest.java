// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.render.lifecycle;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RendererNestingTest {

    private final RendererNesting nesting = new RendererNesting();

    @Test
    void eachRendererOnItsOwnIsOutermost() {
        nesting.entered();
        assertTrue(nesting.returned());
        nesting.entered();
        assertTrue(nesting.returned());
        assertEquals(0, nesting.depth());
    }

    // Like a block entity that draws an entity, which draws a block entity as an item.
    @Test
    void onlyTheOutermostReturnCounts() {
        nesting.entered();
        nesting.entered();
        nesting.entered();

        assertFalse(nesting.returned());
        assertFalse(nesting.returned());
        assertTrue(nesting.returned());
    }

    @Test
    void returnWithoutAStartIsOutermost() {
        assertTrue(nesting.returned());
        assertEquals(0, nesting.depth());
    }

    @Test
    void resetDropsRenderersThatNeverReturned() {
        nesting.entered();
        nesting.entered();

        nesting.reset();
        nesting.entered();

        assertTrue(nesting.returned());
    }
}
