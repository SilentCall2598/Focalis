// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.shader.session;

import io.github.silentcall2598.focalis.render.lifecycle.RenderStage;
import io.github.silentcall2598.focalis.shader.material.MaterialIds;
import io.github.silentcall2598.focalis.shader.pack.MaterialMappings;
import io.github.silentcall2598.focalis.shader.program.EnvironmentInputs;
import net.minecraft.client.shader.Framebuffer;

import javax.annotation.Nullable;
import java.nio.file.Path;

public interface ClientView {

    Path shaderpacks();

    @Nullable
    Object world();

    int dimension();

    @Nullable
    Object viewEntity();

    /**
     * The view entity's interpolated feet position. Eye height and the third-person camera offset are in the
     * model-view matrix.
     */
    void viewPosition(float partialTicks, double[] into);

    int perspective();

    int displayWidth();

    int displayHeight();

    Framebuffer framebuffer();

    int framebufferWidth();

    int framebufferHeight();

    boolean framebuffersEnabled();

    boolean anaglyph();

    boolean openGl30();

    boolean framebufferHookRan();

    boolean registriesComplete();

    MaterialIds resolveMaterials(MaterialMappings mappings);

    boolean vanillaDraws(RenderStage stage);

    void readMatrix(int matrix, float[] into);

    boolean captureFrameValues(EnvironmentInputs environment, long frame, float partialTicks);

    int medium(float partialTicks);

    int precipitation(double x, double y, double z);
}
