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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScopedProgramBindingTest {

    private static final RenderStage SKY = RenderStage.SKY;
    private static final RenderDrawKind BASIC = RenderDrawKind.SKY_BASIC;
    private static final RenderDrawKind TEXTURED = RenderDrawKind.SKY_TEXTURED;
    private static final RenderStage TERRAIN = RenderStage.TERRAIN;
    private static final RenderDrawKind SOLID = RenderDrawKind.TERRAIN_SOLID;
    private static final RenderStage ENTITIES = RenderStage.ENTITIES;
    private static final RenderDrawKind PASS_0 = RenderDrawKind.ENTITY_PASS_0;
    // Suspending stands in for vanilla's entity outlines, which bind their own programs and leave 0 behind.
    private static final RenderStage OUTLINES = RenderStage.ENTITY_OUTLINES;
    private static final RenderDrawKind OUTLINE_KIND = RenderDrawKind.DEFAULT;

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
        assertThrows(NullPointerException.class, () -> binding.suspend(null, OUTLINE_KIND));
        assertThrows(NullPointerException.class, () -> binding.suspend(OUTLINES, null));
        assertThrows(NullPointerException.class, () -> binding.resume(null, OUTLINE_KIND));
        assertThrows(NullPointerException.class, () -> binding.resume(OUTLINES, null));

        assertEquals(0, gl.queries);
        assertUses();
        assertEquals(0, binding.depth());
    }

    // What vanilla's outline shader does to the program, without going through the binding's own GL calls.
    private void vanillaShaderRuns() {
        gl.current = 0;
    }

    @Test
    void suspendStepsAsideAndResumeBindsTheProgramAgain() {
        binding.start(ENTITIES, PASS_0, program(31));

        binding.suspend(OUTLINES, OUTLINE_KIND);
        assertEquals(7, gl.current);
        assertTrue(binding.isSuspended());
        assertEquals(1, binding.depth());
        vanillaShaderRuns();
        binding.resume(OUTLINES, OUTLINE_KIND);
        assertEquals(31, gl.current);
        assertFalse(binding.isSuspended());
        binding.end(ENTITIES, PASS_0);

        // The END still restores the program from before the scope, not what vanilla left behind.
        assertEquals(7, gl.current);
        assertUses(31, 7, 31, 7);
        assertTrue(binding.isEmpty());
    }

    @Test
    void nestedScopesStepAsideToTheProgramFromBeforeTheOutermost() {
        openThree();

        binding.suspend(OUTLINES, OUTLINE_KIND);
        assertEquals(7, gl.current);
        vanillaShaderRuns();
        binding.resume(OUTLINES, OUTLINE_KIND);
        assertEquals(55, gl.current);
        binding.end(TERRAIN, SOLID);
        assertEquals(44, gl.current);
        binding.end(SKY, TEXTURED);
        assertEquals(31, gl.current);
        binding.end(SKY, BASIC);

        assertUses(31, 44, 55, 7, 55, 44, 31, 7);
        assertTrue(binding.isEmpty());
    }

    @Test
    void resumeBindsTheInnermostProgramThroughNullAndSameTargetScopes() {
        binding.start(SKY, BASIC, program(31));
        binding.start(SKY, TEXTURED, program(31));
        binding.start(TERRAIN, SOLID, null);

        binding.suspend(OUTLINES, OUTLINE_KIND);
        vanillaShaderRuns();
        binding.resume(OUTLINES, OUTLINE_KIND);
        assertEquals(31, gl.current);
        binding.end(TERRAIN, SOLID);
        binding.end(SKY, TEXTURED);
        assertEquals(31, gl.current);
        binding.end(SKY, BASIC);

        assertUses(31, 7, 31, 7);
    }

    @Test
    void suspendWithoutAFocalisProgramChangesNothing() {
        binding.suspend(OUTLINES, OUTLINE_KIND);
        binding.resume(OUTLINES, OUTLINE_KIND);
        binding.start(ENTITIES, PASS_0, null);
        binding.suspend(OUTLINES, OUTLINE_KIND);
        vanillaShaderRuns();
        binding.resume(OUTLINES, OUTLINE_KIND);
        binding.end(ENTITIES, PASS_0);

        // A null scope still leaves whatever vanilla bound alone.
        assertEquals(0, gl.current);
        assertUses();
        // Only the START looked at the current program.
        assertEquals(1, gl.queries);
    }

    @Test
    void programSomethingElseBoundIsNeitherTakenAwayNorBoundOver() {
        binding.start(ENTITIES, PASS_0, program(31));
        gl.current = 55;

        binding.suspend(OUTLINES, OUTLINE_KIND);
        assertEquals(55, gl.current);
        vanillaShaderRuns();
        binding.resume(OUTLINES, OUTLINE_KIND);
        assertEquals(0, gl.current);
        binding.end(ENTITIES, PASS_0);

        assertEquals(7, gl.current);
        assertUses(31, 7);
    }

    @Test
    void suspendingTwiceIsRejectedAndDropsTheScopes() {
        binding.start(ENTITIES, PASS_0, program(31));
        binding.suspend(OUTLINES, OUTLINE_KIND);
        vanillaShaderRuns();

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> binding.suspend(OUTLINES, OUTLINE_KIND));

        assertTrue(error.getMessage().contains("while suspended"), error.getMessage());
        assertTrue(binding.isEmpty());
        assertFalse(binding.isSuspended());
        // The program went back when the scopes stepped aside, so nothing is bound over what vanilla left.
        assertEquals(0, gl.current);
        assertUses(31, 7);
    }

    @Test
    void resumeWithoutSuspensionIsRejectedAndUnwinds() {
        binding.start(ENTITIES, PASS_0, program(31));

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> binding.resume(OUTLINES, OUTLINE_KIND));

        assertTrue(error.getMessage().contains("without a suspension"), error.getMessage());
        assertTrue(binding.isEmpty());
        assertEquals(7, gl.current);
        assertUses(31, 7);
    }

    @Test
    void resumeForAnotherStageIsRejected() {
        binding.start(ENTITIES, PASS_0, program(31));
        binding.suspend(OUTLINES, OUTLINE_KIND);

        assertThrows(IllegalStateException.class, () -> binding.resume(ENTITIES, PASS_0));

        assertTrue(binding.isEmpty());
        assertFalse(binding.isSuspended());
        assertUses(31, 7);
    }

    @Test
    void startAndEndWhileSuspendedAreRejected() {
        binding.start(ENTITIES, PASS_0, program(31));
        binding.suspend(OUTLINES, OUTLINE_KIND);
        assertThrows(IllegalStateException.class, () -> binding.start(TERRAIN, SOLID, program(44)));
        assertTrue(binding.isEmpty());

        binding.start(ENTITIES, PASS_0, program(31));
        binding.suspend(OUTLINES, OUTLINE_KIND);
        assertThrows(IllegalStateException.class, () -> binding.end(ENTITIES, PASS_0));
        assertTrue(binding.isEmpty());
        assertFalse(binding.isSuspended());

        assertUses(31, 7, 31, 7);
        assertEquals(7, gl.current);
    }

    @Test
    void abortWhileSuspendedLeavesNoScopeOrProgramBehind() {
        openThree();
        binding.suspend(OUTLINES, OUTLINE_KIND);
        vanillaShaderRuns();

        binding.abort();

        assertTrue(binding.isEmpty());
        assertFalse(binding.isSuspended());
        assertEquals(0, gl.current);
        assertUses(31, 44, 55, 7);
        // Usable again afterwards.
        binding.start(TERRAIN, SOLID, program(31));
        binding.end(TERRAIN, SOLID);
        assertEquals(0, gl.current);
    }

    @Test
    void failedSuspendUnwindsLikeAnyOtherFailure() {
        binding.start(ENTITIES, PASS_0, program(31));
        RuntimeException failure = new RuntimeException("bind");
        gl.useThrowsOnCall.put(2, failure);

        assertSame(failure, assertThrows(RuntimeException.class, () -> binding.suspend(OUTLINES, OUTLINE_KIND)));

        assertTrue(binding.isEmpty());
        assertFalse(binding.isSuspended());
        assertEquals(7, gl.current);
        assertUses(31, 7, 7);
    }

    @Test
    void failedQueryWhileSuspendingUnwinds() {
        binding.start(ENTITIES, PASS_0, program(31));
        gl.queryThrows = new IllegalStateException("query");

        assertThrows(IllegalStateException.class, () -> binding.suspend(OUTLINES, OUTLINE_KIND));

        assertTrue(binding.isEmpty());
        assertFalse(binding.isSuspended());
        assertEquals(7, gl.current);
        assertUses(31, 7);
    }

    @Test
    void failedResumeStillEndsWithTheExternalProgram() {
        openThree();
        binding.suspend(OUTLINES, OUTLINE_KIND);
        vanillaShaderRuns();
        RuntimeException failure = new RuntimeException("bind");
        // Three binds and the suspension so far, so the fifth use is the resume.
        gl.useThrowsOnCall.put(5, failure);

        assertSame(failure, assertThrows(RuntimeException.class, () -> binding.resume(OUTLINES, OUTLINE_KIND)));

        assertTrue(binding.isEmpty());
        assertFalse(binding.isSuspended());
        assertEquals(7, gl.current);
        assertUses(31, 44, 55, 7, 55, 44, 31, 7);
    }
}
