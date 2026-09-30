// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.render.target;

import org.junit.jupiter.api.Test;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL30;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RenderTargetTest {

    // Whatever Minecraft or another mod had bound before, which creating a target must not change.
    private static final int OUTSIDE_TEXTURE = 5;
    private static final int OUTSIDE_FRAMEBUFFER = 8;

    private RecordingRenderTargetGl gl = new RecordingRenderTargetGl(OUTSIDE_TEXTURE, OUTSIDE_FRAMEBUFFER);

    private RenderTarget create(int width, int height, DepthFormat depth) throws RenderTargetException {
        return RenderTarget.create(gl, width, height, depth);
    }

    private void assertBindingsUntouched() {
        assertEquals(OUTSIDE_TEXTURE, gl.boundTexture);
        assertEquals(OUTSIDE_FRAMEBUFFER, gl.drawFramebuffer);
    }

    private void assertNothingLeakedAndDeletedOnce() {
        assertTrue(gl.liveTextures.isEmpty(), "textures " + gl.liveTextures);
        assertTrue(gl.liveFramebuffers.isEmpty(), "framebuffers " + gl.liveFramebuffers);
        for (Map.Entry<Integer, Integer> deleted : gl.deletes.entrySet()) {
            assertEquals(1, (int) deleted.getValue(), "name " + deleted.getKey());
        }
    }

    private void assertNearestAndClamped(int texture) {
        Map<Integer, Integer> parameters = gl.parameters.get(texture);
        assertEquals(GL11.GL_NEAREST, (int) parameters.get(GL11.GL_TEXTURE_MIN_FILTER));
        assertEquals(GL11.GL_NEAREST, (int) parameters.get(GL11.GL_TEXTURE_MAG_FILTER));
        assertEquals(GL12.GL_CLAMP_TO_EDGE, (int) parameters.get(GL11.GL_TEXTURE_WRAP_S));
        assertEquals(GL12.GL_CLAMP_TO_EDGE, (int) parameters.get(GL11.GL_TEXTURE_WRAP_T));
    }

    @Test
    void createsAColorAndDepthTargetAndPutsBindingsBack() throws Exception {
        RenderTarget target = create(1280, 720, DepthFormat.DEPTH_24);

        assertEquals(1280, target.width());
        assertEquals(720, target.height());
        assertSame(DepthFormat.DEPTH_24, target.depthFormat());
        assertFalse(target.isDeleted());
        int color = target.colorTexture();
        int depth = target.depthTexture();
        int framebuffer = target.framebuffer();
        assertEquals(3, new HashSet<>(Arrays.asList(color, depth, framebuffer)).size());
        assertEquals(new HashSet<>(Arrays.asList(color, depth)), gl.liveTextures);
        assertEquals(Collections.singleton(framebuffer), gl.liveFramebuffers);

        assertArrayEquals(new int[] {GL11.GL_RGBA8, 1280, 720, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE},
                gl.allocations.get(color));
        assertArrayEquals(new int[] {GL14.GL_DEPTH_COMPONENT24, 1280, 720, GL11.GL_DEPTH_COMPONENT,
                GL11.GL_UNSIGNED_INT}, gl.allocations.get(depth));
        assertNearestAndClamped(color);
        assertNearestAndClamped(depth);
        assertEquals(color, (int) gl.attachments.get(framebuffer).get(GL30.GL_COLOR_ATTACHMENT0));
        assertEquals(depth, (int) gl.attachments.get(framebuffer).get(GL30.GL_DEPTH_ATTACHMENT));
        assertEquals(2, gl.attachments.get(framebuffer).size());
        assertBindingsUntouched();
    }

    @Test
    void stencilDepthUsesTheCombinedAttachment() throws Exception {
        RenderTarget target = create(64, 32, DepthFormat.DEPTH_24_STENCIL_8);

        int depth = target.depthTexture();
        assertArrayEquals(new int[] {GL30.GL_DEPTH24_STENCIL8, 64, 32, GL30.GL_DEPTH_STENCIL,
                GL30.GL_UNSIGNED_INT_24_8}, gl.allocations.get(depth));
        assertEquals(depth, (int) gl.attachments.get(target.framebuffer()).get(GL30.GL_DEPTH_STENCIL_ATTACHMENT));
        assertFalse(gl.attachments.get(target.framebuffer()).containsKey(GL30.GL_DEPTH_ATTACHMENT));
    }

    @Test
    void incompleteFramebufferLeavesNothingBehind() {
        gl.status = GL30.GL_FRAMEBUFFER_UNSUPPORTED;

        RenderTargetException error = assertThrows(RenderTargetException.class,
                () -> create(1280, 720, DepthFormat.DEPTH_24));

        assertTrue(error.getMessage().contains("0x8cdd") && error.getMessage().contains("DEPTH_24"),
                error.getMessage());
        assertEquals(3, gl.deletes.size());
        assertNothingLeakedAndDeletedOnce();
        assertBindingsUntouched();
    }

    // Every call creation makes, failing in turn. Whatever was created up to there is deleted exactly once.
    @Test
    void failureAtAnyStepLeavesNothingBehind() {
        for (String step : Arrays.asList("boundTexture#1", "drawFramebuffer#1", "createTexture#1", "bindTexture#1",
                "texParameter#1", "allocateTexture#1", "createTexture#2", "bindTexture#2", "texParameter#8",
                "allocateTexture#2", "createFramebuffer#1", "bindDrawFramebuffer#1", "attachTexture#1",
                "attachTexture#2", "framebufferStatus#1")) {
            gl = new RecordingRenderTargetGl(OUTSIDE_TEXTURE, OUTSIDE_FRAMEBUFFER);
            IllegalStateException failure = new IllegalStateException(step);
            gl.failures.put(step, failure);

            assertSame(failure, assertThrows(IllegalStateException.class,
                    () -> create(1280, 720, DepthFormat.DEPTH_24)), step);

            assertNothingLeakedAndDeletedOnce();
            assertBindingsUntouched();
        }
    }

    @Test
    void linkageErrorAlsoLeavesNothingBehind() {
        NoClassDefFoundError missing = new NoClassDefFoundError("org/lwjgl/opengl/GL30");
        gl.failures.put("attachTexture#1", missing);

        assertSame(missing, assertThrows(NoClassDefFoundError.class, () -> create(16, 16, DepthFormat.DEPTH_24)));

        assertNothingLeakedAndDeletedOnce();
        assertBindingsUntouched();
    }

    // The caller never receives a target whose creation couldn't put the bindings back, so it's freed here.
    @Test
    void failedRestoreAfterABuiltTargetStillFreesIt() {
        IllegalStateException failure = new IllegalStateException("restore failed");
        gl.failures.put("bindDrawFramebuffer#2", failure);

        assertSame(failure, assertThrows(IllegalStateException.class, () -> create(16, 16, DepthFormat.DEPTH_24)));

        assertNothingLeakedAndDeletedOnce();
        assertEquals(3, gl.deletes.size());
    }

    @Test
    void cleanupFailureIsSuppressedOntoTheOriginalOne() {
        gl.status = GL30.GL_FRAMEBUFFER_UNSUPPORTED;
        IllegalStateException cleanup = new IllegalStateException("delete failed");
        gl.failures.put("deleteFramebuffer#1", cleanup);

        RenderTargetException error = assertThrows(RenderTargetException.class,
                () -> create(16, 16, DepthFormat.DEPTH_24));

        assertEquals(Collections.singletonList(cleanup), Arrays.asList(error.getSuppressed()));
        assertTrue(gl.liveTextures.isEmpty());
    }

    @Test
    void zeroNameFromTheDriverIsRejected() {
        gl.createTextureReturnsZero = true;

        assertThrows(IllegalStateException.class, () -> create(16, 16, DepthFormat.DEPTH_24));

        assertNothingLeakedAndDeletedOnce();
        assertBindingsUntouched();
    }

    @Test
    void invalidSizeOrFormatIsRejectedBeforeAnyGlCall() {
        assertThrows(IllegalArgumentException.class, () -> create(0, 720, DepthFormat.DEPTH_24));
        assertThrows(IllegalArgumentException.class, () -> create(1280, -1, DepthFormat.DEPTH_24));
        assertThrows(NullPointerException.class, () -> create(1280, 720, null));

        assertEquals(0, gl.calls);
    }

    @Test
    void deleteFreesEveryObjectOnce() throws Exception {
        RenderTarget target = create(1280, 720, DepthFormat.DEPTH_24);
        int framebuffer = target.framebuffer();

        target.delete();
        int calls = gl.calls;
        target.delete();

        assertTrue(target.isDeleted());
        assertEquals(calls, gl.calls);
        assertEquals(3, gl.deletes.size());
        assertTrue(gl.deletes.containsKey(framebuffer));
        assertNothingLeakedAndDeletedOnce();
        assertBindingsUntouched();
        assertThrows(IllegalStateException.class, target::framebuffer);
        assertThrows(IllegalStateException.class, target::colorTexture);
        assertThrows(IllegalStateException.class, target::depthTexture);
        assertEquals(1280, target.width());
    }

    @Test
    void failedDeleteStillFreesTheRest() throws Exception {
        RenderTarget target = create(16, 16, DepthFormat.DEPTH_24);
        int framebuffer = target.framebuffer();
        IllegalStateException first = new IllegalStateException("framebuffer");
        IllegalStateException second = new IllegalStateException("depth texture");
        gl.failures.put("deleteFramebuffer#1", first);
        gl.failures.put("deleteTexture#2", second);

        assertSame(first, assertThrows(IllegalStateException.class, target::delete));

        assertEquals(Collections.singletonList(second), Arrays.asList(first.getSuppressed()));
        assertTrue(target.isDeleted());
        assertEquals(Collections.singleton(framebuffer), gl.liveFramebuffers);
        assertEquals(1, gl.liveTextures.size());
        int calls = gl.calls;
        target.delete();
        assertEquals(calls, gl.calls);
    }

    @Test
    void matchesOnlyItsOwnSizeAndFormat() throws Exception {
        RenderTarget target = create(1280, 720, DepthFormat.DEPTH_24);

        assertTrue(target.matches(1280, 720, DepthFormat.DEPTH_24));
        assertFalse(target.matches(1600, 900, DepthFormat.DEPTH_24));
        assertFalse(target.matches(1280, 721, DepthFormat.DEPTH_24));
        assertFalse(target.matches(1280, 720, DepthFormat.DEPTH_24_STENCIL_8));
    }

    // A resize means a new target. Deleting the old one must not touch the new one.
    @Test
    void replacingATargetOnlyFreesTheOldOne() throws Exception {
        RenderTarget old = create(1280, 720, DepthFormat.DEPTH_24);
        RenderTarget replacement = create(1600, 900, DepthFormat.DEPTH_24);

        old.delete();

        assertEquals(new HashSet<>(Arrays.asList(replacement.colorTexture(), replacement.depthTexture())),
                gl.liveTextures);
        assertEquals(Collections.singleton(replacement.framebuffer()), gl.liveFramebuffers);
        assertEquals(3, gl.deletes.size());
        assertBindingsUntouched();
    }
}
