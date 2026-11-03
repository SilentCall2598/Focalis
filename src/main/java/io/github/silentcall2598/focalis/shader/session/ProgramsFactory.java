// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.shader.session;

import io.github.silentcall2598.focalis.render.state.GlContextInfo;
import io.github.silentcall2598.focalis.shader.pack.PackConfiguration;
import io.github.silentcall2598.focalis.shader.program.CameraInputs;
import io.github.silentcall2598.focalis.shader.program.EnvironmentInputs;
import io.github.silentcall2598.focalis.shader.program.FogSource;
import io.github.silentcall2598.focalis.shader.program.FrameInputs;
import io.github.silentcall2598.focalis.shader.program.LiveWorldPrograms;
import io.github.silentcall2598.focalis.shader.program.ShaderCapabilities;

import java.util.function.Supplier;

interface ProgramsFactory {

    LiveWorldPrograms create(PackConfiguration configuration, LiveWorldPrograms.BuildListener listener,
            FrameInputs frame, CameraInputs camera, EnvironmentInputs environment, FogSource fog);

    static ProgramsFactory driver(Supplier<GlContextInfo> glContext) {
        return (configuration, listener, frame, camera, environment, fog) -> LiveWorldPrograms.create(configuration,
                ShaderCapabilities.from(glContext.get()), listener, frame, camera, environment, fog);
    }
}
