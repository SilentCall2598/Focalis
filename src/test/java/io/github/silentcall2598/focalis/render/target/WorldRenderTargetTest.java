// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.render.target;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.lwjgl.opengl.GL30;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldRenderTargetTest {

    // Minecraft's framebuffer and its depth renderbuffer, bound for both reading and drawing like vanilla leaves it.
    private static final int MINECRAFT = 1;
    private static final int MINECRAFT_DEPTH = 2;

    private final RecordingRenderTargetGl state = new RecordingRenderTargetGl(0, MINECRAFT);
    private final RecordingWorldTargetGl gl = new RecordingWorldTargetGl(state, MINECRAFT_DEPTH);
    private final List<Integer> created = new ArrayList<>();
    private final List<String> stops = new ArrayList<>();
    private final WorldTargetMonitor monitor = new WorldTargetMonitor() {
        @Override
        public void targetCreated(int framebuffer, int colorTexture, int depthTexture, int width, int height,
                String depthFormat) {
            created.add(framebuffer);
        }
    };
    private final WorldRenderTarget world = new WorldRenderTarget(state, gl, monitor, stops::add);

    WorldRenderTargetTest() {
        state.readFramebuffer = MINECRAFT;
    }

    @AfterEach
    void clearRedirect() {
        MainFramebufferRedirect.clear();
    }

    private WorldRenderTarget.Start start(int width, int height, DepthFormat depth) {
        return world.start(MINECRAFT, MINECRAFT_DEPTH, width, height, depth);
    }

    private void pass(int width, int height, DepthFormat depth) {
        assertEquals(WorldRenderTarget.Start.REDIRECTED, start(width, height, depth));
        world.end();
    }

    private int currentTarget() {
        return created.get(created.size() - 1);
    }

    private void assertMinecraftBound() {
        assertEquals(MINECRAFT, state.readFramebuffer);
        assertEquals(MINECRAFT, state.drawFramebuffer);
        assertEquals(MINECRAFT, MainFramebufferRedirect.substitute(MINECRAFT));
    }

    @Test
    void firstPassCreatesTheTargetDrawsIntoItAndCopiesBack() {
        assertEquals(WorldRenderTarget.Start.REDIRECTED, start(854, 480, DepthFormat.DEPTH_24));

        assertEquals(1, created.size());
        int target = currentTarget();
        assertEquals(Collections.singleton(target), state.liveFramebuffers);
        assertEquals(target, state.drawFramebuffer);
        assertEquals(target, state.readFramebuffer);
        assertEquals(target, MainFramebufferRedirect.substitute(MINECRAFT));
        assertEquals(7, MainFramebufferRedirect.substitute(7));
        assertTrue(world.isActive());

        world.end();

        assertEquals(1, gl.copies.size());
        assertArrayEquals(new int[] {target, MINECRAFT, 854, 480}, gl.copies.get(0));
        assertMinecraftBound();
        assertFalse(world.isActive());
    }

    @Test
    void matchingTargetIsReused() {
        pass(854, 480, DepthFormat.DEPTH_24);
        pass(854, 480, DepthFormat.DEPTH_24);
        pass(854, 480, DepthFormat.DEPTH_24);

        assertEquals(1, created.size());
        assertTrue(state.deletes.isEmpty());
        assertEquals(3, gl.copies.size());
    }

    @Test
    void newWidthReplacesTheTarget() {
        assertReplacedBy(1280, 480, DepthFormat.DEPTH_24);
    }

    @Test
    void newHeightReplacesTheTarget() {
        assertReplacedBy(854, 720, DepthFormat.DEPTH_24);
    }

    @Test
    void stencilChangeReplacesTheTarget() {
        assertReplacedBy(854, 480, DepthFormat.DEPTH_24_STENCIL_8);
    }

    private void assertReplacedBy(int width, int height, DepthFormat depth) {
        pass(854, 480, DepthFormat.DEPTH_24);
        int old = currentTarget();

        assertEquals(WorldRenderTarget.Start.REDIRECTED, start(width, height, depth));

        int replacement = currentTarget();
        assertNotEquals(old, replacement);
        assertEquals(1, (int) state.deletes.get(old));
        assertFalse(state.deletedWhileBound);
        assertEquals(Collections.singleton(replacement), state.liveFramebuffers);
        assertEquals(replacement, state.drawFramebuffer);
        world.end();
        assertArrayEquals(new int[] {replacement, MINECRAFT, width, height}, gl.copies.get(1));
    }

    // The old target stays until the replacement is known to have failed, then goes with the redirect.
    @Test
    void failedReplacementFallsBackToVanilla() {
        pass(854, 480, DepthFormat.DEPTH_24);
        state.status = GL30.GL_FRAMEBUFFER_UNSUPPORTED;

        assertEquals(WorldRenderTarget.Start.STOPPED, start(1280, 720, DepthFormat.DEPTH_24));

        assertEquals(1, stops.size());
        assertTrue(stops.get(0).contains("incomplete"), stops.get(0));
        assertTrue(state.liveFramebuffers.isEmpty());
        assertTrue(state.liveTextures.isEmpty());
        assertFalse(state.deletedWhileBound);
        assertFalse(world.isActive());
        assertMinecraftBound();
        assertStaysStopped();
    }

    @Test
    void failedFirstTargetFallsBackToVanilla() {
        state.status = GL30.GL_FRAMEBUFFER_UNSUPPORTED;

        assertEquals(WorldRenderTarget.Start.STOPPED, start(854, 480, DepthFormat.DEPTH_24));

        assertEquals(1, stops.size());
        assertTrue(created.isEmpty());
        assertTrue(state.liveFramebuffers.isEmpty());
        assertMinecraftBound();
        assertStaysStopped();
    }

    // Later passes neither retry nor report again.
    private void assertStaysStopped() {
        int calls = state.calls;
        for (int i = 0; i < 5; i++) {
            assertEquals(WorldRenderTarget.Start.STOPPED, start(854, 480, DepthFormat.DEPTH_24));
        }
        assertEquals(calls, state.calls);
        assertEquals(1, stops.size());
        assertTrue(world.stopReason() != null);
    }

    @Test
    void unsupportedSetupNeverCreatesATarget() {
        world.stop("The world target needs OpenGL 3.0");

        assertEquals(WorldRenderTarget.Start.STOPPED, start(854, 480, DepthFormat.DEPTH_24));

        assertEquals(0, state.calls);
        assertTrue(created.isEmpty());
        assertEquals(Collections.singletonList("The world target needs OpenGL 3.0"), stops);
        world.stop("again");
        assertEquals(1, stops.size());
    }

    @Test
    void passDrawingElsewhereIsLeftAlone() {
        state.drawFramebuffer = 9;

        assertEquals(WorldRenderTarget.Start.OTHER_TARGET, start(854, 480, DepthFormat.DEPTH_24));

        assertTrue(created.isEmpty());
        assertEquals(9, state.drawFramebuffer);
        assertFalse(world.isActive());
        assertNull(world.stopReason());
    }

    @Test
    void replacedDepthBufferStopsBeforeCreatingAnything() {
        gl.depthRenderbuffer = 0;

        assertEquals(WorldRenderTarget.Start.STOPPED, start(854, 480, DepthFormat.DEPTH_24));

        assertTrue(created.isEmpty());
        assertTrue(stops.get(0).contains("depth buffer was replaced"), stops.get(0));
        assertMinecraftBound();
    }

    @Test
    void depthBufferIsOnlyCheckedWhenMinecraftsFramebufferChanges() {
        pass(854, 480, DepthFormat.DEPTH_24);
        pass(854, 480, DepthFormat.DEPTH_24);
        assertEquals(1, gl.depthQueries);

        state.readFramebuffer = 3;
        state.drawFramebuffer = 3;
        gl.depthRenderbuffer = 4;
        assertEquals(WorldRenderTarget.Start.REDIRECTED, world.start(3, 4, 854, 480, DepthFormat.DEPTH_24));
        world.end();

        assertEquals(2, gl.depthQueries);
        assertEquals(3, state.drawFramebuffer);
    }

    @Test
    void passesCantNestOrEndTwice() {
        assertThrows(IllegalStateException.class, world::end);

        start(854, 480, DepthFormat.DEPTH_24);
        assertThrows(IllegalStateException.class, () -> start(854, 480, DepthFormat.DEPTH_24));
        assertTrue(world.isActive());
        assertThrows(IllegalStateException.class, () -> world.stop("mid pass"));
        world.end();

        assertThrows(IllegalStateException.class, world::end);
        assertMinecraftBound();
    }

    @Test
    void deleteFreesTheTargetOnce() {
        pass(854, 480, DepthFormat.DEPTH_24);
        int target = currentTarget();

        world.delete();
        int calls = state.calls;
        world.delete();

        assertEquals(1, (int) state.deletes.get(target));
        assertTrue(state.liveFramebuffers.isEmpty());
        assertTrue(state.liveTextures.isEmpty());
        assertEquals(calls, state.calls);
        assertMinecraftBound();
    }

    // Minecraft's framebuffer goes back before the target is deleted, so it's never deleted while bound.
    @Test
    void deleteDuringAPassPutsMinecraftsFramebufferBackFirst() {
        start(854, 480, DepthFormat.DEPTH_24);

        world.delete();

        assertFalse(world.isActive());
        assertFalse(state.deletedWhileBound);
        assertTrue(state.liveFramebuffers.isEmpty());
        assertMinecraftBound();
        assertTrue(gl.copies.isEmpty());
    }

    @Test
    void failedBindAtStartPutsMinecraftsFramebufferBack() {
        IllegalStateException failure = new IllegalStateException("bind failed");
        state.failures.put("world.bindFramebuffer#1", failure);

        assertSame(failure, assertThrows(IllegalStateException.class,
                () -> start(854, 480, DepthFormat.DEPTH_24)));

        assertFalse(world.isActive());
        assertMinecraftBound();
        world.delete();
        assertFalse(state.deletedWhileBound);
        assertTrue(state.liveFramebuffers.isEmpty());
    }

    @Test
    void failedCopyAtEndPutsMinecraftsFramebufferBack() {
        IllegalStateException failure = new IllegalStateException("copy failed");
        IllegalStateException restore = new IllegalStateException("restore failed");
        state.failures.put("world.copyColorAndDepth#1", failure);
        start(854, 480, DepthFormat.DEPTH_24);

        assertSame(failure, assertThrows(IllegalStateException.class, world::end));

        assertFalse(world.isActive());
        assertMinecraftBound();
        world.delete();
        assertFalse(state.deletedWhileBound);

        // A failed restore is kept with the original failure.
        WorldRenderTarget second = new WorldRenderTarget(state, gl, monitor, stops::add);
        state.failures.put("world.copyColorAndDepth#2", failure);
        state.failures.put("world.bindReadFramebuffer#4", restore);
        second.start(MINECRAFT, MINECRAFT_DEPTH, 854, 480, DepthFormat.DEPTH_24);
        assertSame(failure, assertThrows(IllegalStateException.class, second::end));
        assertSame(restore, failure.getSuppressed()[0]);
        assertFalse(second.isActive());
        second.delete();
    }
}
