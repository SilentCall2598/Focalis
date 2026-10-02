// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.shader.program;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * The inputs Focalis gives one built world program. Uniforms keep their values for as long as the program isn't
 * linked again, so the samplers are set once right after linking and nothing is set while the world draws.
 */
public final class WorldProgramInputs {

    private static final int GL_FLOAT = 0x1406;
    private static final int GL_INT = 0x1404;
    private static final int GL_SAMPLER_1D = 0x8B5D;
    private static final int GL_SAMPLER_2D = 0x8B5E;
    private static final int GL_SAMPLER_3D = 0x8B5F;
    private static final int GL_SAMPLER_CUBE = 0x8B60;
    private static final int GL_SAMPLER_2D_SHADOW = 0x8B62;

    private final int[] locations;
    private final List<String> unprovided;

    private WorldProgramInputs(int[] locations, List<String> unprovided) {
        this.locations = locations;
        this.unprovided = unprovided;
    }

    /**
     * Finds the samplers the program uses and points each at its texture unit. Must run on the client thread with
     * the context current and no Focalis program scope open. The program is bound for a moment and whatever was
     * current before is bound again. That is tried even when something throws, and if putting it back fails while
     * the program is still current, no program is left bound instead.
     *
     * @throws ProgramBuildException if the program declares a supported sampler with another type
     */
    static WorldProgramInputs connect(ShaderGl gl, ShaderProgram program) throws ProgramBuildException {
        int id = program.id();
        int[] locations = new int[WorldSampler.values().length];
        Arrays.fill(locations, -1);
        List<String> unprovided = new ArrayList<>();
        for (ActiveUniform uniform : gl.activeUniforms(id)) {
            // Drivers can report an array with only its first element used as name[0] with size 1.
            boolean array = uniform.name.endsWith("[0]") || uniform.size != 1;
            String name = uniform.name.endsWith("[0]")
                    ? uniform.name.substring(0, uniform.name.length() - 3) : uniform.name;
            if (name.startsWith("gl_")) {
                continue;
            }
            WorldSampler sampler = WorldSampler.named(name);
            if (sampler == null) {
                unprovided.add(name);
                continue;
            }
            if (uniform.type != GL_SAMPLER_2D || array) {
                throw new ProgramBuildException(ProgramFailure.inputs(program.name(), program.name() + " declares "
                        + name + " as " + describe(uniform, array) + ", but it has to be a sampler2D"));
            }
            locations[sampler.ordinal()] = gl.uniformLocation(id, name);
        }
        assign(gl, id, locations);
        return new WorldProgramInputs(locations, Collections.unmodifiableList(unprovided));
    }

    // Uniforms can only be set on the current program, and GL 2.1 has no way around that.
    private static void assign(ShaderGl gl, int program, int[] locations) {
        boolean any = false;
        for (int location : locations) {
            any |= location >= 0;
        }
        if (!any) {
            return;
        }
        int previous = gl.currentProgram();
        Throwable failure = null;
        // The bind is inside too, since a wrapper that reports errors after the call can throw once it already bound.
        try {
            gl.useProgram(program);
            for (WorldSampler sampler : WorldSampler.values()) {
                int location = locations[sampler.ordinal()];
                if (location >= 0) {
                    gl.uniform1i(location, sampler.unit());
                }
            }
        } catch (RuntimeException | LinkageError e) {
            failure = e;
        }
        try {
            gl.useProgram(previous);
        } catch (RuntimeException | LinkageError e) {
            failure = collect(failure, e);
            failure = unbindAfterFailedRestore(gl, program, failure);
        }
        if (failure instanceof RuntimeException) {
            throw (RuntimeException) failure;
        }
        if (failure instanceof LinkageError) {
            throw (LinkageError) failure;
        }
    }

    // The caller deletes the program next, and GL only flags a current program for deletion. So when it's still
    // current, nothing is bound instead. If even that fails it stays current until something else binds a program.
    private static Throwable unbindAfterFailedRestore(ShaderGl gl, int program, Throwable failure) {
        try {
            if (gl.currentProgram() == program) {
                gl.useProgram(0);
            }
        } catch (RuntimeException | LinkageError e) {
            return collect(failure, e);
        }
        return failure;
    }

    private static Throwable collect(@Nullable Throwable first, Throwable next) {
        if (first == null) {
            return next;
        }
        if (first != next) {
            first.addSuppressed(next);
        }
        return first;
    }

    private static String describe(ActiveUniform uniform, boolean array) {
        String type;
        switch (uniform.type) {
            case GL_FLOAT:
                type = "a float";
                break;
            case GL_INT:
                type = "an int";
                break;
            case GL_SAMPLER_1D:
                type = "a sampler1D";
                break;
            case GL_SAMPLER_2D:
                type = "a sampler2D";
                break;
            case GL_SAMPLER_3D:
                type = "a sampler3D";
                break;
            case GL_SAMPLER_CUBE:
                type = "a samplerCube";
                break;
            case GL_SAMPLER_2D_SHADOW:
                type = "a sampler2DShadow";
                break;
            default:
                type = "type 0x" + Integer.toHexString(uniform.type);
        }
        return array ? "an array with " + type : type;
    }

    /** The uniform location of a sampler in this program, or -1 when the program doesn't use it. */
    public int location(WorldSampler sampler) {
        return locations[sampler.ordinal()];
    }

    /** Active uniforms Focalis doesn't set yet. They keep the zero they got when the program was linked. */
    public List<String> unprovided() {
        return unprovided;
    }
}
