// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.shader.program;

import io.github.silentcall2598.focalis.shader.pack.ProgramStates;

import javax.annotation.Nullable;

/**
 * What one built program needs while it's bound besides its uniforms. That's the alpha test and blend mode its pack
 * program name sets, and the custom textures on the units its samplers read. Immutable.
 */
public final class ProgramDrawState {

    static final ProgramDrawState NONE = new ProgramDrawState(null, null, new int[0], new int[0]);

    @Nullable
    private final ProgramStates.AlphaTest alphaTest;
    @Nullable
    private final ProgramStates.Blend blend;
    // Parallel, one entry per unit the program samples, each unit once.
    private final int[] units;
    private final int[] textures;

    ProgramDrawState(@Nullable ProgramStates.AlphaTest alphaTest, @Nullable ProgramStates.Blend blend, int[] units,
            int[] textures) {
        this.alphaTest = alphaTest;
        this.blend = blend;
        this.units = units;
        this.textures = textures;
    }

    static ProgramDrawState of(@Nullable ProgramStates.AlphaTest alphaTest, @Nullable ProgramStates.Blend blend,
            int[] units, int[] textures) {
        if (alphaTest == null && blend == null && units.length == 0) {
            return NONE;
        }
        return new ProgramDrawState(alphaTest, blend, units.clone(), textures.clone());
    }

    /** The alpha test the program sets, or null when it leaves the test alone. */
    @Nullable
    public ProgramStates.AlphaTest alphaTest() {
        return alphaTest;
    }

    /** The blend mode the program sets, or null when it leaves blending alone. */
    @Nullable
    public ProgramStates.Blend blend() {
        return blend;
    }

    public int textureCount() {
        return units.length;
    }

    public int unit(int index) {
        return units[index];
    }

    public int texture(int index) {
        return textures[index];
    }

    public boolean isEmpty() {
        return this == NONE;
    }
}
