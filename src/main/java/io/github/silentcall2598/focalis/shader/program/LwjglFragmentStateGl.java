// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.shader.program;

import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;

/**
 * {@link FragmentStateGl} on LWJGL 2 and GlStateManager. Each change goes through GlStateManager first, so vanilla's
 * next call compares against the right value, and then straight to GL. GlStateManager skips calls that match its
 * cache, and its blend cache keeps old alpha factors after the two factor blendFunc, so on its own it could leave GL
 * unchanged.
 */
final class LwjglFragmentStateGl implements FragmentStateGl {

    static final LwjglFragmentStateGl INSTANCE = new LwjglFragmentStateGl();

    private LwjglFragmentStateGl() {
    }

    @Override
    public boolean alphaTestEnabled() {
        return GL11.glIsEnabled(GL11.GL_ALPHA_TEST);
    }

    @Override
    public int alphaFunction() {
        return GL11.glGetInteger(GL11.GL_ALPHA_TEST_FUNC);
    }

    @Override
    public float alphaReference() {
        return GL11.glGetFloat(GL11.GL_ALPHA_TEST_REF);
    }

    @Override
    public void alphaTest(boolean enabled) {
        if (enabled) {
            GlStateManager.enableAlpha();
            GL11.glEnable(GL11.GL_ALPHA_TEST);
        } else {
            GlStateManager.disableAlpha();
            GL11.glDisable(GL11.GL_ALPHA_TEST);
        }
    }

    @Override
    public void alphaFunction(int function, float reference) {
        GlStateManager.alphaFunc(function, reference);
        GL11.glAlphaFunc(function, reference);
    }

    @Override
    public boolean blendEnabled() {
        return GL11.glIsEnabled(GL11.GL_BLEND);
    }

    @Override
    public void blendFactors(int[] into) {
        into[0] = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB);
        into[1] = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
        into[2] = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA);
        into[3] = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
    }

    @Override
    public void blend(boolean enabled) {
        if (enabled) {
            GlStateManager.enableBlend();
            GL11.glEnable(GL11.GL_BLEND);
        } else {
            GlStateManager.disableBlend();
            GL11.glDisable(GL11.GL_BLEND);
        }
    }

    // Separate factors are core since GL 1.4, and world programs already need 2.0.
    @Override
    public void blendFactors(int sourceColor, int destinationColor, int sourceAlpha, int destinationAlpha) {
        GlStateManager.tryBlendFuncSeparate(sourceColor, destinationColor, sourceAlpha, destinationAlpha);
        GL14.glBlendFuncSeparate(sourceColor, destinationColor, sourceAlpha, destinationAlpha);
    }
}
