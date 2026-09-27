// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.diagnostics.qa;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The GL state the post pass promises to leave as it found it, as one comparable value. Deliberately nothing more,
 * since this isn't meant to track OpenGL in general.
 */
final class GlBoundary {

    // Only loaded once something captures, so comparing boundaries works without LWJGL around.
    private static final class Buffers {
        static final IntBuffer VIEWPORT = BufferUtils.createIntBuffer(16); // LWJGL wants room for 16 values
    }

    final int readFramebuffer;
    final int drawFramebuffer;
    final int[] viewport;
    final int program;
    final int activeUnit;
    final int[] units;
    final int[] bindings;
    final boolean depthTest;
    final boolean blend;
    final boolean alphaTest;

    GlBoundary(int readFramebuffer, int drawFramebuffer, int[] viewport, int program, int activeUnit, int[] units,
            int[] bindings, boolean depthTest, boolean blend, boolean alphaTest) {
        this.readFramebuffer = readFramebuffer;
        this.drawFramebuffer = drawFramebuffer;
        this.viewport = viewport.clone();
        this.program = program;
        this.activeUnit = activeUnit;
        this.units = units.clone();
        this.bindings = bindings.clone();
        this.depthTest = depthTest;
        this.blend = blend;
        this.alphaTest = alphaTest;
    }

    // Client thread only. Reading a unit's binding means switching to it, and the real active unit is put back
    // with raw GL, so GlStateManager's cache is never involved.
    static GlBoundary capture(int... units) {
        IntBuffer buffer = Buffers.VIEWPORT;
        buffer.clear();
        GL11.glGetInteger(GL11.GL_VIEWPORT, buffer);
        int[] viewport = {buffer.get(0), buffer.get(1), buffer.get(2), buffer.get(3)};
        int active = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        int[] bindings = new int[units.length];
        for (int i = 0; i < units.length; i++) {
            GL13.glActiveTexture(GL13.GL_TEXTURE0 + units[i]);
            bindings[i] = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        }
        GL13.glActiveTexture(active);
        return new GlBoundary(GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING),
                GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING), viewport,
                GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM), active - GL13.GL_TEXTURE0, units, bindings,
                GL11.glIsEnabled(GL11.GL_DEPTH_TEST), GL11.glIsEnabled(GL11.GL_BLEND),
                GL11.glIsEnabled(GL11.GL_ALPHA_TEST));
    }

    /** One readable line per value that differs, empty when {@code other} matches. */
    List<String> differences(GlBoundary other) {
        List<String> differences = new ArrayList<>();
        compare(differences, "read framebuffer", readFramebuffer, other.readFramebuffer);
        compare(differences, "draw framebuffer", drawFramebuffer, other.drawFramebuffer);
        if (!Arrays.equals(viewport, other.viewport)) {
            differences.add("viewport " + Arrays.toString(viewport) + " -> " + Arrays.toString(other.viewport));
        }
        compare(differences, "program", program, other.program);
        compare(differences, "active texture unit", activeUnit, other.activeUnit);
        for (int i = 0; i < units.length && i < other.bindings.length; i++) {
            compare(differences, "texture on unit " + units[i], bindings[i], other.bindings[i]);
        }
        compare(differences, "depth test", depthTest, other.depthTest);
        compare(differences, "blend", blend, other.blend);
        compare(differences, "alpha test", alphaTest, other.alphaTest);
        return differences;
    }

    private static void compare(List<String> differences, String name, Object before, Object after) {
        if (!before.equals(after)) {
            differences.add(name + " " + before + " -> " + after);
        }
    }
}
