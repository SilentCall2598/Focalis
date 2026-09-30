// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.render.target;

import java.util.ArrayList;
import java.util.List;

/**
 * The world target's calls on the same recorded state as {@link RecordingRenderTargetGl}, since both work on one GL
 * context. Failures and call counts go through it too, under names like "world.copyColorAndDepth#1".
 */
final class RecordingWorldTargetGl implements WorldTargetGl {

    final RecordingRenderTargetGl state;
    // What the depth attachment query answers for whatever is bound for drawing.
    int depthRenderbuffer;
    int depthQueries;
    // Read framebuffer, draw framebuffer, width and height of every copy.
    final List<int[]> copies = new ArrayList<>();

    RecordingWorldTargetGl(RecordingRenderTargetGl state, int depthRenderbuffer) {
        this.state = state;
        this.depthRenderbuffer = depthRenderbuffer;
    }

    @Override
    public int readFramebuffer() {
        state.step("world.readFramebuffer");
        return state.readFramebuffer;
    }

    @Override
    public int drawFramebuffer() {
        state.step("world.drawFramebuffer");
        return state.drawFramebuffer;
    }

    @Override
    public void bindFramebuffer(int framebuffer) {
        state.step("world.bindFramebuffer");
        state.readFramebuffer = framebuffer;
        state.drawFramebuffer = framebuffer;
    }

    @Override
    public void bindReadFramebuffer(int framebuffer) {
        state.step("world.bindReadFramebuffer");
        state.readFramebuffer = framebuffer;
    }

    @Override
    public void bindDrawFramebuffer(int framebuffer) {
        state.step("world.bindDrawFramebuffer");
        state.drawFramebuffer = framebuffer;
    }

    @Override
    public int drawDepthRenderbuffer() {
        state.step("world.drawDepthRenderbuffer");
        depthQueries++;
        return depthRenderbuffer;
    }

    @Override
    public void copyColorAndDepth(int width, int height) {
        state.step("world.copyColorAndDepth");
        copies.add(new int[] {state.readFramebuffer, state.drawFramebuffer, width, height});
    }
}
