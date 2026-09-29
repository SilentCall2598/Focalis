// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.shader.program;

import io.github.silentcall2598.focalis.render.lifecycle.RenderDrawKind;
import io.github.silentcall2598.focalis.render.lifecycle.RenderStage;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScopedProgramBindingTest {

    private static final RenderStage SKY = RenderStage.SKY;
    private static final RenderDrawKind BASIC = RenderDrawKind.SKY_BASIC;
    private static final RenderDrawKind TEXTURED = RenderDrawKind.SKY_TEXTURED;
    private static final RenderStage TERRAIN = RenderStage.TERRAIN;
    private static final RenderDrawKind SOLID = RenderDrawKind.TERRAIN_SOLID;

    // A program some other mod or Minecraft had active before Focalis did anything.
    private final RecordingProgramBindingGl gl = new RecordingProgramBindingGl(7);
    private final ScopedProgramBinding binding = new ScopedProgramBinding(gl);
    private final RecordingShaderGl programs = new RecordingShaderGl();

    private ShaderProgram program(int id) {
        return new ShaderProgram(programs, id, "program" + id, "");
    }

    private void assertUses(Integer... expected) {
        assertEquals(Arrays.asList(expected), gl.uses);
    }

    // Opens SKY_BASIC 31, SKY_TEXTURED 44 and TERRAIN_SOLID 55 inside each other.
    private void openThree() {
        binding.start(SKY, BASIC, program(31));
        binding.start(SKY, TEXTURED, program(44));
        binding.start(TERRAIN, SOLID, program(55));
        assertUses(31, 44, 55);
    }

    @Test
    void bindsAndRestoresTheExternalProgram() {
        binding.start(TERRAIN, SOLID, program(31));
        assertEquals(31, gl.current);
        assertEquals(1, binding.depth());

        binding.end(TERRAIN, SOLID);

        assertEquals(7, gl.current);
        assertUses(31, 7);
        assertTrue(binding.isEmpty());
    }

    @Test
    void targetThatIsAlreadyActiveIsNotBoundAgain() {
        gl.current = 31;

        binding.start(TERRAIN, SOLID, program(31));
        binding.end(TERRAIN, SOLID);

        assertUses();
        assertEquals(31, gl.current);
        assertEquals(0, binding.depth());
    }

    @Test
    void nullTargetChangesNothingButStillHasToMatch() {
        binding.start(TERRAIN, SOLID, null);
        assertEquals(1, binding.depth());
        binding.end(TERRAIN, SOLID);

        assertUses();
        assertEquals(7, gl.current);
        assertEquals(0, binding.depth());
    }

    @Test
    void nestedScopesRestoreInReverse() {
        binding.start(SKY, BASIC, program(31));
        binding.start(SKY, TEXTURED, program(44));
        assertEquals(44, gl.current);
        binding.end(SKY, TEXTURED);
        assertEquals(31, gl.current);
        binding.end(SKY, BASIC);

        assertUses(31, 44, 31, 7);
        assertEquals(7, gl.current);
        assertTrue(binding.isEmpty());
    }

    @Test
    void nestedNullTargetLeavesTheOuterProgramAlone() {
        binding.start(SKY, BASIC, program(31));
        binding.start(SKY, TEXTURED, null);
        binding.end(SKY, TEXTURED);
        assertEquals(31, gl.current);
        assertUses(31);

        binding.end(SKY, BASIC);

        assertUses(31, 7);
    }

    @Test
    void nestedSameTargetIsNotBoundTwice() {
        ShaderProgram shared = program(31);

        binding.start(SKY, BASIC, shared);
        binding.start(SKY, TEXTURED, shared);
        binding.end(SKY, TEXTURED);
        binding.end(SKY, BASIC);

        assertUses(31, 7);
    }

    @Test
    void endWithoutStartIsRejected() {
        IllegalStateException error = assertThrows(IllegalStateException.class, () -> binding.end(SKY, BASIC));

        assertTrue(error.getMessage().contains("SKY/SKY_BASIC"), error.getMessage());
        assertUses();
        assertEquals(0, gl.queries);
        assertEquals(0, binding.depth());
    }

    @Test
    void mismatchedEndUnwindsEverything() {
        binding.start(SKY, BASIC, program(31));

        IllegalStateException error = assertThrows(IllegalStateException.class, () -> binding.end(SKY, TEXTURED));

        assertTrue(error.getMessage().contains("SKY/SKY_TEXTURED") && error.getMessage().contains("SKY/SKY_BASIC"),
                error.getMessage());
        assertUses(31, 7);
        assertEquals(7, gl.current);
        assertEquals(0, binding.depth());
    }

    // Deep enough to grow the arrays, with changed, unchanged and null scopes mixed in.
    @Test
    void deepNestingKeepsEveryScope() {
        RenderStage[] stages = RenderStage.values();
        RenderDrawKind[] kinds = RenderDrawKind.values();
        List<Integer> expected = new ArrayList<>();
        int current = 7;
        List<Integer> restores = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            ShaderProgram target = i % 5 == 4 ? null : program(i % 5 == 3 ? current : 100 + i);
            binding.start(stages[i % stages.length], kinds[i % kinds.length], target);
            if (target != null && target.id() != current) {
                expected.add(target.id());
                restores.add(0, current);
                current = target.id();
            } else {
                restores.add(0, null);
            }
        }
        assertEquals(40, binding.depth());
        for (int i = 39; i >= 0; i--) {
            binding.end(stages[i % stages.length], kinds[i % kinds.length]);
        }
        for (Integer restore : restores) {
            if (restore != null) {
                expected.add(restore);
            }
        }

        assertEquals(expected, gl.uses);
        assertEquals(7, gl.current);
        assertEquals(0, binding.depth());
    }

    @Test
    void deletedTargetFailsBeforeAnyGlCall() {
        ShaderProgram deleted = program(31);
        deleted.delete();

        assertThrows(IllegalStateException.class, () -> binding.start(SKY, BASIC, deleted));

        assertEquals(0, gl.queries);
        assertUses();
        assertEquals(0, binding.depth());
    }

    @Test
    void failedCurrentProgramQueryOpensNoScope() {
        IllegalStateException lost = new IllegalStateException("context lost");
        gl.queryThrows = lost;

        assertSame(lost, assertThrows(IllegalStateException.class, () -> binding.start(SKY, BASIC, program(31))));

        assertUses();
        assertEquals(0, binding.depth());
    }

    @Test
    void failedBindUnwindsEveryScope() {
        IllegalStateException failed = new IllegalStateException("bind failed");
        gl.useThrowsOnCall.put(2, failed);
        binding.start(SKY, BASIC, program(31));

        assertSame(failed, assertThrows(IllegalStateException.class,
                () -> binding.start(SKY, TEXTURED, program(44))));

        // The failed bind counts as a change, so its scope restores 31 before the outer one restores 7.
        assertUses(31, 44, 31, 7);
        assertEquals(7, gl.current);
        assertEquals(0, binding.depth());
    }

    @Test
    void linkageErrorFromBindAlsoUnwinds() {
        NoClassDefFoundError missing = new NoClassDefFoundError("org/lwjgl/opengl/GL20");
        gl.useThrowsOnCall.put(2, missing);
        binding.start(SKY, BASIC, program(31));

        assertSame(missing, assertThrows(NoClassDefFoundError.class,
                () -> binding.start(SKY, TEXTURED, program(44))));

        assertUses(31, 44, 31, 7);
        assertEquals(7, gl.current);
        assertEquals(0, binding.depth());
    }

    @Test
    void failedRestoreStillUnwindsTheOuterScopes() {
        openThree();
        IllegalStateException restore = new IllegalStateException("restore 44 failed");
        IllegalStateException cleanup = new IllegalStateException("restore 31 failed");
        gl.useThrowsOnCall.put(4, restore);
        gl.useThrowsOnCall.put(5, cleanup);

        assertSame(restore, assertThrows(IllegalStateException.class, () -> binding.end(TERRAIN, SOLID)));

        assertUses(31, 44, 55, 44, 31, 7);
        assertEquals(Collections.singletonList(cleanup), Arrays.asList(restore.getSuppressed()));
        assertEquals(7, gl.current);
        assertEquals(0, binding.depth());
    }

    @Test
    void abortRestoresNewestFirst() {
        openThree();

        binding.abort();
        binding.abort();

        assertUses(31, 44, 55, 44, 31, 7);
        assertEquals(7, gl.current);
        assertEquals(0, binding.depth());
    }

    @Test
    void abortKeepsGoingAfterFailures() {
        openThree();
        IllegalStateException first = new IllegalStateException("first");
        NoClassDefFoundError second = new NoClassDefFoundError("second");
        gl.useThrowsOnCall.put(4, first);
        gl.useThrowsOnCall.put(5, second);

        assertSame(first, assertThrows(IllegalStateException.class, binding::abort));

        assertEquals(Collections.<Throwable>singletonList(second), Arrays.asList(first.getSuppressed()));
        assertUses(31, 44, 55, 44, 31, 7);
        assertEquals(7, gl.current);
        assertEquals(0, binding.depth());
    }

    // Focalis bound nothing on START, so whatever another mod binds inside the scope is left alone.
    @Test
    void scopesThatChangedNothingDontRestore() {
        gl.current = 31;
        binding.start(TERRAIN, SOLID, program(31));
        gl.current = 99;
        binding.end(TERRAIN, SOLID);
        assertEquals(99, gl.current);

        binding.start(TERRAIN, SOLID, null);
        gl.current = 123;
        binding.end(TERRAIN, SOLID);
        assertEquals(123, gl.current);

        assertUses();
    }

    @Test
    void nullStageOrKindIsRejectedBeforeAnyGlCall() {
        ShaderProgram target = program(31);

        assertThrows(NullPointerException.class, () -> binding.start(null, BASIC, target));
        assertThrows(NullPointerException.class, () -> binding.start(SKY, null, target));
        assertThrows(NullPointerException.class, () -> binding.end(null, BASIC));
        assertThrows(NullPointerException.class, () -> binding.end(SKY, null));

        assertEquals(0, gl.queries);
        assertUses();
        assertEquals(0, binding.depth());
    }
}
