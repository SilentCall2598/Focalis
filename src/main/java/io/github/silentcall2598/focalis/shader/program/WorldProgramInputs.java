// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.shader.program;

import io.github.silentcall2598.focalis.shader.pack.CustomUniforms;
import io.github.silentcall2598.focalis.shader.pack.PackTextures;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

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
    private static final FrameUniform[] FRAME_UNIFORMS = FrameUniform.values();
    private static final CameraUniform[] CAMERA_UNIFORMS = CameraUniform.values();
    private static final EnvironmentUniform[] ENVIRONMENT_UNIFORMS = EnvironmentUniform.values();

    private static final String ENTITY_ID = "entityId";
    private static final String BLOCK_ENTITY_ID = "blockEntityId";
    private static final String ENTITY_COLOR = "entityColor";

    private final ShaderGl gl;
    private final int entityIdLocation;
    private final int blockEntityIdLocation;
    private final int entityColorLocation;
    private boolean objectSet;
    private int setEntityId;
    private int setBlockEntityId;
    private float setRed;
    private float setGreen;
    private float setBlue;
    private float setAlpha;
    private final int[] locations;
    private final int[] frameLocations;
    private final int[] cameraLocations;
    private final int[] environmentLocations;
    private final boolean anyFrameUniform;
    private final boolean anyCameraUniform;
    private final boolean anyWorldValue;
    private final boolean anyPassValue;
    private final boolean anyFogValue;
    private boolean atlasSet;
    private int setAtlasWidth;
    private int setAtlasHeight;
    private final int[] customLocations;
    private final int[] customSlots;
    private final boolean[] customBools;
    private final List<String> unprovided;
    private final int[] textureUnits;
    private final int[] textures;
    @Nullable
    private CustomUniformValues updatedCustomValues;
    private long updatedCustom;
    private long customUpdates;
    private long updatedFrame;
    private long updatedCamera;
    private long cameraUpdates;
    private long updatedWorldValues;
    private long updatedPassValues;
    private long fogUpdates;

    private WorldProgramInputs(ShaderGl gl, int[] locations, int[] frameLocations, int[] cameraLocations,
            int[] environmentLocations, int[] objectLocations, int[] customLocations, int[] customSlots,
            boolean[] customBools, List<String> unprovided, int[] textureUnits, int[] textures) {
        this.gl = gl;
        this.entityIdLocation = objectLocations[0];
        this.blockEntityIdLocation = objectLocations[1];
        this.entityColorLocation = objectLocations[2];
        this.textureUnits = textureUnits;
        this.textures = textures;
        this.locations = locations;
        this.frameLocations = frameLocations;
        this.cameraLocations = cameraLocations;
        this.environmentLocations = environmentLocations;
        this.customLocations = customLocations;
        this.customSlots = customSlots;
        this.customBools = customBools;
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

    static WorldProgramInputs connect(ShaderGl gl, ShaderProgram program) throws ProgramBuildException {
        return connect(gl, program, CustomUniforms.NONE);
    }

    static WorldProgramInputs connect(ShaderGl gl, ShaderProgram program, CustomUniforms customs)
            throws ProgramBuildException {
        return connect(gl, program, customs, ProgramTextures.NONE);
    }

    static WorldProgramInputs connect(ShaderGl gl, ShaderProgram program, CustomUniforms customs,
            ProgramTextures declared) throws ProgramBuildException {
        int id = program.id();
        int[] locations = new int[WorldSampler.values().length];
        Arrays.fill(locations, -1);
        int[] frameLocations = new int[FrameUniform.values().length];
        Arrays.fill(frameLocations, -1);
        int[] cameraLocations = new int[CAMERA_UNIFORMS.length];
        Arrays.fill(cameraLocations, -1);
        int[] environmentLocations = new int[ENVIRONMENT_UNIFORMS.length];
        Arrays.fill(environmentLocations, -1);
        int[] objectLocations = {-1, -1, -1};
        List<String> unprovided = new ArrayList<>();
        List<CustomUniforms.Declaration> custom = new ArrayList<>();
        List<Integer> customLocations = new ArrayList<>();
        List<Integer> textureLocations = new ArrayList<>();
        List<Integer> textureLocationUnits = new ArrayList<>();
        for (ActiveUniform uniform : gl.activeUniforms(id)) {
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
            if (name.equals(ENTITY_ID) || name.equals(BLOCK_ENTITY_ID)) {
                check(program, uniform, name, array, GL_INT, "an int");
                objectLocations[name.equals(ENTITY_ID) ? 0 : 1] = gl.uniformLocation(id, name);
                continue;
            }
            if (name.equals(ENTITY_COLOR)) {
                check(program, uniform, name, array, GL_FLOAT_VEC4, "a vec4");
                objectLocations[2] = gl.uniformLocation(id, name);
                continue;
            }
            int unit = PackTextures.gbuffersUnit(name);
            if (unit >= 0 && declared.declared(unit)) {
                String failure = declared.failure(unit);
                if (failure != null) {
                    throw new ProgramBuildException(ProgramFailure.inputs(program.name(), program.name() + " samples "
                            + name + ", but the texture declared for it can't be used: " + failure));
                }
                check(program, uniform, name, array, GL_SAMPLER_2D, "a sampler2D");
                textureLocations.add(gl.uniformLocation(id, name));
                textureLocationUnits.add(unit);
                continue;
            }
            CustomUniforms.Declaration declaration = customs.uniforms().get(name);
            if (declaration != null) {
                int type = declaration.bool() ? GL_BOOL : GL_FLOAT;
                if (uniform.type != type || array) {
                    unprovided.add(name + " (declared as " + describe(uniform, array) + ", but its custom uniform is a "
                            + declaration.type() + ")");
                } else {
                    custom.add(declaration);
                    customLocations.add(gl.uniformLocation(id, name));
                }
                continue;
            }
            unprovided.add(name);
        }
        int[] textureLocationArray = new int[textureLocations.size()];
        int[] textureLocationUnitArray = new int[textureLocations.size()];
        List<Integer> units = new ArrayList<>();
        for (int i = 0; i < textureLocationArray.length; i++) {
            textureLocationArray[i] = textureLocations.get(i);
            textureLocationUnitArray[i] = textureLocationUnits.get(i);
            if (!units.contains(textureLocationUnits.get(i))) {
                units.add(textureLocationUnits.get(i));
            }
        }
        int[] textureUnits = new int[units.size()];
        int[] textures = new int[units.size()];
        for (int i = 0; i < textureUnits.length; i++) {
            textureUnits[i] = units.get(i);
            textures[i] = declared.texture(textureUnits[i]);
        }
        assign(gl, id, locations, textureLocationArray, textureLocationUnitArray);
        int[] customLocationArray = new int[custom.size()];
        int[] customSlots = new int[custom.size()];
        boolean[] customBools = new boolean[custom.size()];
        for (int i = 0; i < custom.size(); i++) {
            customLocationArray[i] = customLocations.get(i);
            customSlots[i] = custom.get(i).index();
            customBools[i] = custom.get(i).bool();
        }
        return new WorldProgramInputs(gl, locations, frameLocations, cameraLocations, environmentLocations,
                objectLocations, customLocationArray, customSlots, customBools, Collections.unmodifiableList(unprovided),
                textureUnits, textures);
    }

    private static void check(ShaderProgram program, ActiveUniform uniform, String name, boolean array, int type,
            String expected) throws ProgramBuildException {
        if (uniform.type != type || array) {
            throw new ProgramBuildException(ProgramFailure.inputs(program.name(), program.name() + " declares "
                    + name + " as " + describe(uniform, array) + ", but it has to be " + expected));
        }
    }

    boolean accepts(@Nullable CameraSnapshot camera, EnvironmentInputs environment, FogSource fog) {
        if ((anyWorldValue || anyPassValue || customLocations.length > 0) && !environment.readyFor(camera)) {
            return false;
        }
        if (customLocations.length > 0 && camera == null) {
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

    void update(FrameInputs frame, @Nullable CameraSnapshot camera, EnvironmentInputs environment, FogSource fog) {
        update(frame, camera, environment, fog, null);
    }

    void update(FrameInputs frame, @Nullable CameraSnapshot camera, EnvironmentInputs environment, FogSource fog,
            @Nullable CustomUniformValues custom) {
        update(frame, camera, environment, fog, custom, false);
    }

    void update(FrameInputs frame, @Nullable CameraSnapshot camera, EnvironmentInputs environment, FogSource fog,
            @Nullable CustomUniformValues custom, boolean atlas) {
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
        int atlasLocation = environmentLocations[EnvironmentUniform.ATLAS_SIZE.ordinal()];
        if (atlasLocation >= 0) {
            int width = atlas ? environment.atlasWidth() : 0;
            int height = atlas ? environment.atlasHeight() : 0;
            if (!atlasSet || width != setAtlasWidth || height != setAtlasHeight) {
                gl.uniform2i(atlasLocation, width, height);
                setAtlasWidth = width;
                setAtlasHeight = height;
                atlasSet = true;
            }
        }
        if (customLocations.length > 0) {
            if (custom == null) {
                throw new IllegalStateException("A world program with custom uniforms was bound without their values");
            }
            custom.prepare(frame, camera, environment);
            if (custom != updatedCustomValues || custom.generation() != updatedCustom) {
                for (int i = 0; i < customLocations.length; i++) {
                    float value = custom.value(customSlots[i]);
                    if (customBools[i]) {
                        gl.uniform1i(customLocations[i], value != 0 ? 1 : 0);
                    } else {
                        gl.uniform1f(customLocations[i], value);
                    }
                }
                updatedCustomValues = custom;
                updatedCustom = custom.generation();
                customUpdates++;
            }
        }
    }

    void object(int entityId, int blockEntityId, float red, float green, float blue, float alpha) {
        boolean known = objectSet;
        if (entityIdLocation >= 0 && (!known || setEntityId != entityId)) {
            gl.uniform1i(entityIdLocation, entityId);
        }
        if (blockEntityIdLocation >= 0 && (!known || setBlockEntityId != blockEntityId)) {
            gl.uniform1i(blockEntityIdLocation, blockEntityId);
        }
        if (entityColorLocation >= 0 && (!known || setRed != red || setGreen != green || setBlue != blue
                || setAlpha != alpha)) {
            gl.uniform4f(entityColorLocation, red, green, blue, alpha);
        }
        setEntityId = entityId;
        setBlockEntityId = blockEntityId;
        setRed = red;
        setGreen = green;
        setBlue = blue;
        setAlpha = alpha;
        objectSet = true;
    }

    boolean objectDiffers(int entityId, int blockEntityId, float red, float green, float blue, float alpha) {
        if (!objectSet) {
            return entityIdLocation >= 0 || blockEntityIdLocation >= 0 || entityColorLocation >= 0;
        }
        return entityIdLocation >= 0 && setEntityId != entityId
                || blockEntityIdLocation >= 0 && setBlockEntityId != blockEntityId
                || entityColorLocation >= 0 && (setRed != red || setGreen != green || setBlue != blue
                        || setAlpha != alpha);
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
            case EYE_ALTITUDE:
                gl.uniform1f(location, environment.eyeAltitude());
                break;
            case NEAR:
                gl.uniform1f(location, EnvironmentInputs.NEAR);
                break;
            case FAR:
                gl.uniform1f(location, environment.far());
                break;
            case NIGHT_VISION:
                gl.uniform1f(location, environment.nightVision());
                break;
            case BLINDNESS:
                gl.uniform1f(location, environment.blindness());
                break;
            case SCREEN_BRIGHTNESS:
                gl.uniform1f(location, environment.screenBrightness());
                break;
            case FOG_MODE:
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

    private static void assign(ShaderGl gl, int program, int[] locations, int[] textureLocations,
            int[] textureUnits) {
        boolean any = textureLocations.length > 0;
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
            for (int i = 0; i < textureLocations.length; i++) {
                gl.uniform1i(textureLocations[i], textureUnits[i]);
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

    public int location(WorldSampler sampler) {
        return locations[sampler.ordinal()];
    }

    public int location(FrameUniform uniform) {
        return frameLocations[uniform.ordinal()];
    }

    public int location(CameraUniform uniform) {
        return cameraLocations[uniform.ordinal()];
    }

    public int objectLocation(String name) {
        switch (name) {
            case ENTITY_ID:
                return entityIdLocation;
            case BLOCK_ENTITY_ID:
                return blockEntityIdLocation;
            case ENTITY_COLOR:
                return entityColorLocation;
            default:
                return -1;
        }
    }

    public int location(EnvironmentUniform uniform) {
        return environmentLocations[uniform.ordinal()];
    }

    public long updatedWorldValues() {
        return updatedWorldValues;
    }

    public long updatedPassValues() {
        return updatedPassValues;
    }

    public long fogUpdates() {
        return fogUpdates;
    }

    long updatedFrame() {
        return updatedFrame;
    }

    public long updatedCamera() {
        return updatedCamera;
    }

    public long cameraUpdates() {
        return cameraUpdates;
    }

    public int customUniformCount() {
        return customLocations.length;
    }

    public long customUpdates() {
        return customUpdates;
    }

    public List<String> unprovided() {
        return unprovided;
    }

    public int textureCount() {
        return textureUnits.length;
    }

    public int textureUnit(int index) {
        return textureUnits[index];
    }

    public int texture(int index) {
        return textures[index];
    }

    int[] textureUnits() {
        return textureUnits;
    }

    int[] textures() {
        return textures;
    }
}
