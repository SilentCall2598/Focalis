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
 * linked again, so the samplers are set once right after linking. The frame uniforms are set the first time the
 * program is bound in a frame. Every location is looked up once, right after linking.
 */
public final class WorldProgramInputs {

    private static final int GL_FLOAT = 0x1406;
    private static final int GL_INT = 0x1404;
    private static final int GL_SAMPLER_1D = 0x8B5D;
    private static final int GL_SAMPLER_2D = 0x8B5E;
    private static final int GL_SAMPLER_3D = 0x8B5F;
    private static final int GL_SAMPLER_CUBE = 0x8B60;
    private static final int GL_SAMPLER_2D_SHADOW = 0x8B62;
    // values() copies the array, and updates run every frame.
    private static final FrameUniform[] FRAME_UNIFORMS = FrameUniform.values();

    private final ShaderGl gl;
    private final int[] locations;
    private final int[] frameLocations;
    private final boolean anyFrameUniform;
    private final List<String> unprovided;
    // The FrameInputs sequence the frame uniforms were last set for, 0 before the first time.
    private long updatedFrame;

    private WorldProgramInputs(ShaderGl gl, int[] locations, int[] frameLocations, List<String> unprovided) {
        this.gl = gl;
        this.locations = locations;
        this.frameLocations = frameLocations;
        this.unprovided = unprovided;
        boolean any = false;
        for (int location : frameLocations) {
            any |= location >= 0;
        }
        this.anyFrameUniform = any;
    }

    /**
     * Finds the samplers and frame uniforms the program uses and points each sampler at its texture unit. The frame
     * uniforms are left for {@link #update}. Must run on the client thread with
     * the context current and no Focalis program scope open. The program is bound for a moment and whatever was
     * current before is bound again. That is tried even when something throws, and if putting it back fails while
     * the program is still current, no program is left bound instead.
     *
     * @throws ProgramBuildException if the program declares a supported uniform with another type or as an array
     */
    static WorldProgramInputs connect(ShaderGl gl, ShaderProgram program) throws ProgramBuildException {
        int id = program.id();
        int[] locations = new int[WorldSampler.values().length];
        Arrays.fill(locations, -1);
        int[] frameLocations = new int[FrameUniform.values().length];
        Arrays.fill(frameLocations, -1);
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
            if (sampler != null) {
                check(program, uniform, name, array, GL_SAMPLER_2D, "a sampler2D");
                locations[sampler.ordinal()] = gl.uniformLocation(id, name);
                continue;
            }
            FrameUniform frame = FrameUniform.named(name);
            if (frame != null) {
                check(program, uniform, name, array, frame.integer() ? GL_INT : GL_FLOAT,
                        frame.integer() ? "an int" : "a float");
                frameLocations[frame.ordinal()] = gl.uniformLocation(id, name);
                continue;
            }
            unprovided.add(name);
        }
        assign(gl, id, locations);
        return new WorldProgramInputs(gl, locations, frameLocations, Collections.unmodifiableList(unprovided));
    }

    private static void check(ShaderProgram program, ActiveUniform uniform, String name, boolean array, int type,
            String expected) throws ProgramBuildException {
        if (uniform.type != type || array) {
            throw new ProgramBuildException(ProgramFailure.inputs(program.name(), program.name() + " declares "
                    + name + " as " + describe(uniform, array) + ", but it has to be " + expected));
        }
    }

    /**
     * Sets the frame uniforms to the values of the current frame, unless this program already got them. The program
     * has to be current and the frame captured. Nothing else in GL changes.
     */
    void update(FrameInputs frame) {
        long sequence = frame.sequence();
        if (sequence == 0) {
            throw new IllegalStateException("A world program needed the frame values before any frame was captured");
        }
        if (sequence == updatedFrame) {
            return;
        }
        if (anyFrameUniform) {
            for (FrameUniform uniform : FRAME_UNIFORMS) {
                int location = frameLocations[uniform.ordinal()];
                if (location >= 0) {
                    set(uniform, location, frame);
                }
            }
        }
        // Only marked once every value is in, so a failed update is never taken for a finished one.
        updatedFrame = sequence;
    }

    private void set(FrameUniform uniform, int location, FrameInputs frame) {
        switch (uniform) {
            case VIEW_WIDTH:
                gl.uniform1f(location, frame.viewWidth());
                break;
            case VIEW_HEIGHT:
                gl.uniform1f(location, frame.viewHeight());
                break;
            case ASPECT_RATIO:
                gl.uniform1f(location, frame.aspectRatio());
                break;
            case FRAME_COUNTER:
                gl.uniform1i(location, frame.frameCounter());
                break;
            case FRAME_TIME:
                gl.uniform1f(location, frame.frameTime());
                break;
            case FRAME_TIME_COUNTER:
                gl.uniform1f(location, frame.frameTimeCounter());
                break;
            default:
                throw new IllegalStateException("No value for " + uniform);
        }
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

    /** The uniform location of a frame uniform in this program, or -1 when the program doesn't use it. */
    public int location(FrameUniform uniform) {
        return frameLocations[uniform.ordinal()];
    }

    /** The FrameInputs sequence the frame uniforms were last set for, or 0 if they never were. */
    long updatedFrame() {
        return updatedFrame;
    }

    /** Active uniforms Focalis doesn't set yet. They keep the zero they got when the program was linked. */
    public List<String> unprovided() {
        return unprovided;
    }
}
