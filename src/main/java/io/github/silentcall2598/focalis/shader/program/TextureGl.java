// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.shader.program;

import java.nio.ByteBuffer;

interface TextureGl {

    int maxTextureUnits();

    int maxTextureSize();

    int createTexture();

    void deleteTexture(int texture);

    int activeUnit();

    void activeUnit(int unit);

    int boundTexture();

    /** Binds on the active unit without Minecraft's cache knowing. Only for bindings that are put back right away. */
    void bindTexture(int texture);

    /**
     * Makes a unit active and binds a 2D texture on it that stays bound while other code draws. Minecraft's texture
     * cache follows for the units it tracks, so code binding through GlStateManager meanwhile sees the real binding.
     */
    void bindOnUnit(int unit, int texture);

    /** Makes a unit active again after {@link #bindOnUnit}, and Minecraft's cached active unit with it when it can. */
    void restoreUnit(int unit);

    /**
     * Fills the 2D texture bound on the active unit with RGBA pixels, one byte each, and sets its filtering and
     * wrapping. Leaves the pixel unpack state the way it found it.
     */
    void upload(int width, int height, ByteBuffer pixels, boolean linear, boolean clamp);
}
