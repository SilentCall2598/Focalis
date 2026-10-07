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
 * linked again, so the samplers are set once right after linking. The frame uniforms and the world values are set the
 * first time the program is bound in a frame, and the camera uniforms and the values in eye space the first time it's
 * bound with a new camera snapshot. The fog is draw state, so it's set every time a stage binds the program. Every
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
    private static final EnvironmentUniform[] ENVIRONMENT_UNIFORMS = EnvironmentUniform.values();

    private final ShaderGl gl;
    private final int[] locations;
    private final int[] frameLocations;
    private final int[] cameraLocations;
    private final int[] environmentLocations;
    private final boolean anyFrameUniform;
    private final boolean anyCameraUniform;
    private final boolean anyWorldValue;
    private final boolean anyPassValue;
    private final boolean anyFogValue;
    private final List<String> unprovided;
    // The FrameInputs sequence the frame uniforms were last set for, 0 before the first time.
    private long updatedFrame;
    // The CameraSnapshot sequence the camera uniforms were last set for, 0 before the first time.
    private long updatedCamera;
    private long cameraUpdates;
    // The EnvironmentInputs frame and pass the world and eye space values were last set for, 0 before the first time.
    private long updatedWorldValues;
    private long updatedPassValues;
    private long fogUpdates;

    private WorldProgramInputs(ShaderGl gl, int[] locations, int[] frameLocations, int[] cameraLocations,
            int[] environmentLocations, List<String> unprovided) {
        this.gl = gl;
        this.locations = locations;
        this.frameLocations = frameLocations;
        this.cameraLocations = cameraLocations;
        this.environmentLocations = environmentLocations;
        this.unprovided = unprovided;
        this.anyFrameUniform = any(frameLocations);
        this.anyCameraUniform = any(cameraLocations);
        this.anyWorldValue = any(environmentLocations, EnvironmentUniform.Update.FRAME);
        this.anyPassValue = any(environmentLocations, EnvironmentUniform.Update.PASS);
        this.anyFogValue = any(environmentLocations, EnvironmentUniform.Update.BIND);
    }

    private static boolean any(int[] locations) {
        for (int location : locations) {
            if (location >= 0) {
                return true;
            }
        }
        return false;
    }

    private static boolean any(int[] locations, EnvironmentUniform.Update update) {
        for (EnvironmentUniform uniform : ENVIRONMENT_UNIFORMS) {
            if (uniform.update() == update && locations[uniform.ordinal()] >= 0) {
                return true;
            }
        }
        return false;
    }

    /**
     * Finds the samplers and the frame, camera and environment uniforms the program uses and points each sampler at
     * its texture unit. Everything but the samplers is left for {@link #update}. Must run on the client thread with
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
        int[] environmentLocations = new int[ENVIRONMENT_UNIFORMS.length];
        Arrays.fill(environmentLocations, -1);
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
            EnvironmentUniform environment = EnvironmentUniform.named(name);
            if (environment != null) {
                check(program, uniform, name, array, environment.type().glType, environment.type().description);
                environmentLocations[environment.ordinal()] = gl.uniformLocation(id, name);
                continue;
            }
            unprovided.add(name);
        }
        assign(gl, id, locations);
        return new WorldProgramInputs(gl, locations, frameLocations, cameraLocations, environmentLocations,
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
     * Whether this program can draw with {@code camera}, the environment values and the fog. A program that uses no
     * camera uniform always can as far as the camera goes. One that does needs a snapshot, and one with the inverse
     * matrices it reads. A program with environment values needs the ones of {@code camera}'s world pass, and one
     * with fog values needs fog that can be read. Otherwise its stage draws the vanilla way, since the values it would
     * get are missing or would belong to another camera.
     */
    boolean accepts(@Nullable CameraSnapshot camera, EnvironmentInputs environment, FogSource fog) {
        if ((anyWorldValue || anyPassValue) && !environment.readyFor(camera)) {
            return false;
        }
        if (anyFogValue && !fog.available()) {
            return false;
        }
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
     * Sets the frame uniforms and world values to the ones of the current frame, and the camera uniforms and values
     * in eye space to those of {@code camera}, each unless this program already got them. The fog is set every time.
     * The program has to be current, the frame captured and everything {@link #accepts accepted}. Nothing else in GL
     * changes.
     */
    void update(FrameInputs frame, @Nullable CameraSnapshot camera, EnvironmentInputs environment, FogSource fog) {
        long sequence = frame.sequence();
        if (sequence == 0) {
            throw new IllegalStateException("A world program needed the frame values before any frame was captured");
        }
        if (!accepts(camera, environment, fog)) {
            throw new IllegalStateException("A world program was bound with camera, environment or fog values it"
                    + " can't use");
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
        if (anyWorldValue && environment.frame() != updatedWorldValues) {
            setEnvironment(EnvironmentUniform.Update.FRAME, environment, fog);
            updatedWorldValues = environment.frame();
        }
        if (anyPassValue && environment.pass() != updatedPassValues) {
            setEnvironment(EnvironmentUniform.Update.PASS, environment, fog);
            updatedPassValues = environment.pass();
        }
        if (anyFogValue) {
            setEnvironment(EnvironmentUniform.Update.BIND, environment, fog);
            fogUpdates++;
        }
    }

    private void setEnvironment(EnvironmentUniform.Update update, EnvironmentInputs environment, FogSource fog) {
        for (EnvironmentUniform uniform : ENVIRONMENT_UNIFORMS) {
            int location = environmentLocations[uniform.ordinal()];
            if (location >= 0 && uniform.update() == update) {
                set(uniform, location, environment, fog);
            }
        }
    }

    private void set(EnvironmentUniform uniform, int location, EnvironmentInputs environment, FogSource fog) {
        switch (uniform) {
            case WORLD_TIME:
                gl.uniform1i(location, environment.worldTime());
                break;
            case WORLD_DAY:
                gl.uniform1i(location, environment.worldDay());
                break;
            case MOON_PHASE:
                gl.uniform1i(location, environment.moonPhase());
                break;
            case SUN_ANGLE:
                gl.uniform1f(location, environment.sunAngle());
                break;
            case SHADOW_ANGLE:
                gl.uniform1f(location, environment.shadowAngle());
                break;
            case RAIN_STRENGTH:
                gl.uniform1f(location, environment.rainStrength());
                break;
            case SKY_COLOR:
                gl.uniform3f(location, environment.skyRed(), environment.skyGreen(), environment.skyBlue());
                break;
            case EYE_BRIGHTNESS:
                gl.uniform2i(location, environment.blockBrightness(), environment.skyBrightness());
                break;
            case SUN_POSITION:
                set3(location, environment.sunPosition());
                break;
            case MOON_POSITION:
                set3(location, environment.moonPosition());
                break;
            case SHADOW_LIGHT_POSITION:
                set3(location, environment.sunUp() ? environment.sunPosition() : environment.moonPosition());
                break;
            case UP_POSITION:
                set3(location, environment.upPosition());
                break;
            case IS_EYE_IN_WATER:
                gl.uniform1i(location, environment.medium());
                break;
            case FOG_MODE:
                // With fog off nothing is fogged, and 0 is no GL fog mode.
                gl.uniform1i(location, fog.enabled() ? fog.mode() : 0);
                break;
            case FOG_START:
                gl.uniform1f(location, fog.start());
                break;
            case FOG_END:
                gl.uniform1f(location, fog.end());
                break;
            case FOG_DENSITY:
                gl.uniform1f(location, fog.density());
                break;
            case FOG_COLOR:
                gl.uniform3f(location, fog.red(), fog.green(), fog.blue());
                break;
            default:
                throw new IllegalStateException("No value for " + uniform);
        }
    }

    private void set3(int location, float[] value) {
        gl.uniform3f(location, value[0], value[1], value[2]);
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

    /** The uniform location of an environment uniform in this program, or -1 when the program doesn't use it. */
    public int location(EnvironmentUniform uniform) {
        return environmentLocations[uniform.ordinal()];
    }

    /** The EnvironmentInputs frame the world values were last set for, or 0 if they never were. */
    public long updatedWorldValues() {
        return updatedWorldValues;
    }

    /** The EnvironmentInputs pass the values in eye space were last set for, or 0 if they never were. */
    public long updatedPassValues() {
        return updatedPassValues;
    }

    /** How many times the fog values were set, once for every bind of a program that uses them. */
    public long fogUpdates() {
        return fogUpdates;
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
