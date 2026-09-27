// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.shader.post;

import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

import java.nio.IntBuffer;
import java.util.Arrays;

/**
 * The GL state the post pass changes, recorded before the pass changes anything and put back afterwards, even when
 * the pass fails halfway. Nothing else is queried or touched.
 */
final class PassGlState {

    private final IntBuffer viewport = BufferUtils.createIntBuffer(16); // LWJGL wants room for 16 values
    private final int[] units;
    private final int[] unitTextures;
    private final boolean[] unitRecorded;

    private int readFramebuffer;
    private int drawFramebuffer;
    private int program;
    private int activeTexture;
    private boolean depthTest;
    private boolean blend;
    private boolean alphaTest;

    PassGlState(int... units) {
        for (int unit : units) {
            // Unit 0 holds the block atlas and unit 1 the lightmap.
            if (unit < 2) {
                throw new IllegalArgumentException("Texture unit " + unit + " is reserved");
            }
        }
        this.units = units.clone();
        this.unitTextures = new int[units.length];
        this.unitRecorded = new boolean[units.length];
    }

    /** Queries everything except texture bindings and changes nothing. */
    void record() {
        readFramebuffer = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        drawFramebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        viewport.clear();
        GL11.glGetInteger(GL11.GL_VIEWPORT, viewport);
        program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        activeTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        depthTest = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        blend = GL11.glIsEnabled(GL11.GL_BLEND);
        alphaTest = GL11.glIsEnabled(GL11.GL_ALPHA_TEST);
        Arrays.fill(unitRecorded, false);
    }

    int drawFramebuffer() {
        return drawFramebuffer;
    }

    // Turns off what could keep the full-screen triangle from reaching a pixel unchanged. With the depth test off
    // nothing is written to depth either, so the depth mask can stay as it is.
    void disableFragmentTests() {
        if (depthTest) {
            GlStateManager.disableDepth();
        }
        if (blend) {
            GlStateManager.disableBlend();
        }
        if (alphaTest) {
            GlStateManager.disableAlpha();
        }
    }

    /**
     * Binds a 2D texture on one of the pass's units, remembering what the unit had before the first bind. Raw GL is
     * used on purpose. Every unit change is undone by {@link #restore()}, so GlStateManager's texture cache never
     * sees the pass and stays exactly as it was, even when the unit active before the pass is one it doesn't track.
     */
    void bindTexture(int unit, int texture) {
        int index = indexOf(unit);
        GL13.glActiveTexture(GL13.GL_TEXTURE0 + unit);
        if (!unitRecorded[index]) {
            unitTextures[index] = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            unitRecorded[index] = true;
        }
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
    }

    void restore() {
        GL20.glUseProgram(program);
        for (int i = units.length - 1; i >= 0; i--) {
            if (unitRecorded[i]) {
                GL13.glActiveTexture(GL13.GL_TEXTURE0 + units[i]);
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, unitTextures[i]);
            }
        }
        GL13.glActiveTexture(activeTexture);
        if (depthTest) {
            GlStateManager.enableDepth();
        }
        if (blend) {
            GlStateManager.enableBlend();
        }
        if (alphaTest) {
            GlStateManager.enableAlpha();
        }
        GlStateManager.viewport(viewport.get(0), viewport.get(1), viewport.get(2), viewport.get(3));
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readFramebuffer);
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, drawFramebuffer);
    }

    private int indexOf(int unit) {
        for (int i = 0; i < units.length; i++) {
            if (units[i] == unit) {
                return i;
            }
        }
        throw new IllegalArgumentException("Texture unit " + unit + " doesn't belong to the pass");
    }
}
