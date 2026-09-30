// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.render.target;

import org.lwjgl.opengl.GL30;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Keeps texture and framebuffer objects and bindings like a driver would, so tests can prove nothing leaks and every
 * binding is put back. Deleting a bound object unbinds it, as in GL.
 */
final class RecordingRenderTargetGl implements RenderTargetGl {

    final Set<Integer> liveTextures = new HashSet<>();
    final Set<Integer> liveFramebuffers = new HashSet<>();
    // How often each name was deleted. Textures and framebuffers never share a name here.
    final Map<Integer, Integer> deletes = new HashMap<>();
    // Texture name to internal format, width, height, format and type.
    final Map<Integer, int[]> allocations = new HashMap<>();
    final Map<Integer, Map<Integer, Integer>> parameters = new HashMap<>();
    final Map<Integer, Map<Integer, Integer>> attachments = new HashMap<>();
    int boundTexture;
    int drawFramebuffer;
    int status = GL30.GL_FRAMEBUFFER_COMPLETE;
    boolean createTextureReturnsZero;
    int calls;
    // A RuntimeException or an Error thrown by a call like "allocateTexture#2", counting each method from 1.
    final Map<String, Throwable> failures = new HashMap<>();

    private final Map<String, Integer> counts = new HashMap<>();
    private int nextName = 100;

    RecordingRenderTargetGl(int boundTexture, int drawFramebuffer) {
        this.boundTexture = boundTexture;
        this.drawFramebuffer = drawFramebuffer;
    }

    @Override
    public int createTexture() {
        step("createTexture");
        if (createTextureReturnsZero) {
            return 0;
        }
        int name = nextName++;
        liveTextures.add(name);
        return name;
    }

    @Override
    public void deleteTexture(int texture) {
        step("deleteTexture");
        deletes.merge(texture, 1, Integer::sum);
        liveTextures.remove(texture);
        if (boundTexture == texture) {
            boundTexture = 0;
        }
    }

    @Override
    public int boundTexture() {
        step("boundTexture");
        return boundTexture;
    }

    @Override
    public void bindTexture(int texture) {
        step("bindTexture");
        boundTexture = texture;
    }

    @Override
    public void texParameter(int name, int value) {
        step("texParameter");
        parameters.computeIfAbsent(boundTexture, texture -> new HashMap<>()).put(name, value);
    }

    @Override
    public void allocateTexture(int internalFormat, int width, int height, int format, int type) {
        step("allocateTexture");
        allocations.put(boundTexture, new int[] {internalFormat, width, height, format, type});
    }

    @Override
    public int createFramebuffer() {
        step("createFramebuffer");
        int name = nextName++;
        liveFramebuffers.add(name);
        attachments.put(name, new HashMap<Integer, Integer>());
        return name;
    }

    @Override
    public void deleteFramebuffer(int framebuffer) {
        step("deleteFramebuffer");
        deletes.merge(framebuffer, 1, Integer::sum);
        liveFramebuffers.remove(framebuffer);
        if (drawFramebuffer == framebuffer) {
            drawFramebuffer = 0;
        }
    }

    @Override
    public int drawFramebuffer() {
        step("drawFramebuffer");
        return drawFramebuffer;
    }

    @Override
    public void bindDrawFramebuffer(int framebuffer) {
        step("bindDrawFramebuffer");
        drawFramebuffer = framebuffer;
    }

    @Override
    public void attachTexture(int attachment, int texture) {
        step("attachTexture");
        attachments.get(drawFramebuffer).put(attachment, texture);
    }

    @Override
    public int framebufferStatus() {
        step("framebufferStatus");
        return status;
    }

    private void step(String method) {
        calls++;
        int call = counts.merge(method, 1, Integer::sum);
        Throwable failure = failures.get(method + "#" + call);
        if (failure instanceof Error) {
            throw (Error) failure;
        }
        if (failure != null) {
            throw (RuntimeException) failure;
        }
    }
}
