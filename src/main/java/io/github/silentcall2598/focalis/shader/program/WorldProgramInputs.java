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
 * program is bound in a frame, and the camera uniforms the first time it's bound with a new camera snapshot. Every
 * location is looked up once, right after linking.
 */
public final class WorldProgramInputs {

    private static final int GL_FLOAT = 0x1406;
    private static final int GL_INT = 0x1404;
    private static final int GL_BOOL = 0x8B56;
    private static final int GL_FLOAT_VEC2 = 0x8B50;
    private static final int GL_FLOAT_VEC3 = 0x8B51;
    private static final int GL_FLOAT_VEC4 = 0x8B52;
    private static final int GL_INT_VEC2 = 0x8B53;
    private static final int GL_INT_VEC3 = 0x8B54;
    private static final int GL_INT_VEC4 = 0x8B55;
    private static final int GL_FLOAT_MAT2 = 0x8B5A;
    private static final int GL_FLOAT_MAT3 = 0x8B5B;
    private static final int GL_FLOAT_MAT4 = 0x8B5C;
    private static final int GL_SAMPLER_1D = 0x8B5D;
    private static final int GL_SAMPLER_2D = 0x8B5E;
    private static final int GL_SAMPLER_3D = 0x8B5F;
    private static final int GL_SAMPLER_CUBE = 0x8B60;
    private static final int GL_SAMPLER_2D_SHADOW = 0x8B62;
    // values() copies the array, and updates run every frame.
    private static final FrameUniform[] FRAME_UNIFORMS = FrameUniform.values();
    private static final CameraUniform[] CAMERA_UNIFORMS = CameraUniform.values();

    private final ShaderGl gl;
    private final int[] locations;
    private final int[] frameLocations;
    private final int[] cameraLocations;
    private final boolean anyFrameUniform;
    private final boolean anyCameraUniform;
    private final List<String> unprovided;
    // The FrameInputs sequence the frame uniforms were last set for, 0 before the first time.
    private long updatedFrame;
    // The CameraSnapshot sequence the camera uniforms were last set for, 0 before the first time.
    private long updatedCamera;
    private long cameraUpdates;

    private WorldProgramInputs(ShaderGl gl, int[] locations, int[] frameLocations, int[] cameraLocations,
            List<String> unprovided) {
        this.gl = gl;
        this.locations = locations;
        this.frameLocations = frameLocations;
        this.cameraLocations = cameraLocations;
        this.unprovided = unprovided;
        this.anyFrameUniform = any(frameLocations);
        this.anyCameraUniform = any(cameraLocations);
    }

    private static boolean any(int[] locations) {
        for (int location : locations) {
            if (location >= 0) {
                return true;
            }
        }
        return false;
    }

    /**
     * Finds the samplers, frame and camera uniforms the program uses and points each sampler at its texture unit. The
     * frame and camera uniforms are left for {@link #update}. Must run on the client thread with
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
        int[] cameraLocations = new int[CAMERA_UNIFORMS.length];
        Arrays.fill(cameraLocations, -1);
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
            CameraUniform camera = CameraUniform.named(name);
            if (camera != null) {
                check(program, uniform, name, array, camera.matrix() ? GL_FLOAT_MAT4 : GL_FLOAT_VEC3,
                        camera.matrix() ? "a mat4" : "a vec3");
                cameraLocations[camera.ordinal()] = gl.uniformLocation(id, name);
                continue;
            }
            unprovided.add(name);
        }
        assign(gl, id, locations);
        return new WorldProgramInputs(gl, locations, frameLocations, cameraLocations,
                Collections.unmodifiableList(unprovided));
    }

    private static void check(ShaderProgram program, ActiveUniform uniform, String name, boolean array, int type,
            String expected) throws ProgramBuildException {
        if (uniform.type != type || array) {
            throw new ProgramBuildException(ProgramFailure.inputs(program.name(), program.name() + " declares "
                    + name + " as " + describe(uniform, array) + ", but it has to be " + expected));
        }
    }

    /**
     * Whether this program can draw with {@code camera}. A program that uses no camera uniform always can. One that
     * does needs a snapshot, and one with the inverse matrices it reads. Otherwise its stage draws the vanilla way,
     * since the values it would get are missing or would belong to another camera.
     */
    boolean accepts(@Nullable CameraSnapshot camera) {
        if (!anyCameraUniform) {
            return true;
        }
        if (camera == null) {
            return false;
        }
        if (location(CameraUniform.MODEL_VIEW_INVERSE) >= 0 && camera.modelViewInverse == null) {
            return false;
        }
        return location(CameraUniform.PROJECTION_INVERSE) < 0 || camera.projectionInverse != null;
    }

    /**
     * Sets the frame uniforms to the values of the current frame and the camera uniforms to those of
     * {@code camera}, each unless this program already got them. The program has to be current, the frame captured
     * and {@code camera} {@link #accepts accepted}. Nothing else in GL changes.
     */
    void update(FrameInputs frame, @Nullable CameraSnapshot camera) {
        long sequence = frame.sequence();
        if (sequence == 0) {
            throw new IllegalStateException("A world program needed the frame values before any frame was captured");
        }
        if (!accepts(camera)) {
            throw new IllegalStateException("A world program was bound with camera values it can't use");
        }
        if (sequence != updatedFrame) {
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
        if (anyCameraUniform && camera.sequence() != updatedCamera) {
            for (CameraUniform uniform : CAMERA_UNIFORMS) {
                int location = cameraLocations[uniform.ordinal()];
                if (location >= 0) {
                    set(uniform, location, camera);
                }
            }
            updatedCamera = camera.sequence();
            cameraUpdates++;
        }
    }

    // The positions stay doubles until here, since a vec3 only holds floats.
    private void set(CameraUniform uniform, int location, CameraSnapshot camera) {
        switch (uniform) {
            case CAMERA_POSITION:
                gl.uniform3f(location, (float) camera.x(), (float) camera.y(), (float) camera.z());
                break;
            case PREVIOUS_CAMERA_POSITION:
                gl.uniform3f(location, (float) camera.previousX(), (float) camera.previousY(),
                        (float) camera.previousZ());
                break;
            case MODEL_VIEW:
                gl.uniformMatrix4(location, camera.modelView);
                break;
            case MODEL_VIEW_INVERSE:
                gl.uniformMatrix4(location, camera.modelViewInverse);
                break;
            case PREVIOUS_MODEL_VIEW:
                gl.uniformMatrix4(location, camera.previousModelView);
                break;
            case PROJECTION:
                gl.uniformMatrix4(location, camera.projection);
                break;
            case PROJECTION_INVERSE:
                gl.uniformMatrix4(location, camera.projectionInverse);
                break;
            case PREVIOUS_PROJECTION:
                gl.uniformMatrix4(location, camera.previousProjection);
                break;
            default:
                throw new IllegalStateException("No value for " + uniform);
        }
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
            case GL_BOOL:
                type = "a bool";
                break;
            case GL_FLOAT_VEC2:
                type = "a vec2";
                break;
            case GL_FLOAT_VEC3:
                type = "a vec3";
                break;
            case GL_FLOAT_VEC4:
                type = "a vec4";
                break;
            case GL_INT_VEC2:
                type = "an ivec2";
                break;
            case GL_INT_VEC3:
                type = "an ivec3";
                break;
            case GL_INT_VEC4:
                type = "an ivec4";
                break;
            case GL_FLOAT_MAT2:
                type = "a mat2";
                break;
            case GL_FLOAT_MAT3:
                type = "a mat3";
                break;
            case GL_FLOAT_MAT4:
                type = "a mat4";
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

    /** The uniform location of a camera uniform in this program, or -1 when the program doesn't use it. */
    public int location(CameraUniform uniform) {
        return cameraLocations[uniform.ordinal()];
    }

    /** The FrameInputs sequence the frame uniforms were last set for, or 0 if they never were. */
    long updatedFrame() {
        return updatedFrame;
    }

    /** The CameraSnapshot sequence the camera uniforms were last set for, or 0 if they never were. */
    public long updatedCamera() {
        return updatedCamera;
    }

    /** How many times the camera uniforms were set, which can only grow by one per camera snapshot. */
    public long cameraUpdates() {
        return cameraUpdates;
    }

    /** Active uniforms Focalis doesn't set yet. They keep the zero they got when the program was linked. */
    public List<String> unprovided() {
        return unprovided;
    }
}
