// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.shader.program;

import io.github.silentcall2598.focalis.render.lifecycle.RenderDrawKind;
import io.github.silentcall2598.focalis.render.lifecycle.RenderStage;
import io.github.silentcall2598.focalis.shader.pack.PackConfiguration;
import io.github.silentcall2598.focalis.shader.pack.ProgramDirectory;
import io.github.silentcall2598.focalis.shader.pack.ShaderPack;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;

public final class LiveWorldPrograms {

    public interface BuildListener {

        void built(DirectoryPrograms programs);

        default void texturesMade(ProgramTextures textures) {
        }
    }

    private final ShaderPack pack;
    private PackConfiguration configuration;
    private CustomUniformValues customValues;
    private final BiFunction<PreparedWorldPrograms, ProgramTextures, BuiltWorldPrograms> builder;
    private final Function<PackConfiguration, ProgramTextures> textureMaker;
    @Nullable
    private ProgramTextures textures;
    private final BuildListener listener;
    private final WorldProgramBinding binding;
    private final Map<ProgramDirectory, DirectoryPrograms> folders = new LinkedHashMap<>();
    @Nullable
    private DirectoryPrograms selected;
    private int selectedDimension;
    private boolean worldOpen;
    private boolean deleted;

    private LiveWorldPrograms(PackConfiguration configuration,
            BiFunction<PreparedWorldPrograms, ProgramTextures, BuiltWorldPrograms> builder,
            Function<PackConfiguration, ProgramTextures> textureMaker, BuildListener listener,
            ScopedProgramBinding scopes, ScopedProgramState state, FrameInputs frame, CameraInputs camera,
            EnvironmentInputs environment, FogSource fog) {
        this.configuration = Objects.requireNonNull(configuration, "configuration");
        this.pack = configuration.pack();
        this.builder = builder;
        this.textureMaker = textureMaker;
        this.listener = Objects.requireNonNull(listener, "listener");
        this.binding = new WorldProgramBinding(scopes, state, frame, camera, environment, fog);
        this.customValues = new CustomUniformValues(configuration.customUniforms());
        binding.customValues(customValues);
        binding.terrainColors(configuration.lighting().changesTerrainColors());
    }

    public static LiveWorldPrograms create(PackConfiguration configuration, ShaderCapabilities capabilities,
            BuildListener listener, FrameInputs frame, CameraInputs camera, EnvironmentInputs environment,
            FogSource fog) {
        Objects.requireNonNull(capabilities, "capabilities");
        return new LiveWorldPrograms(configuration,
                (prepared, textures) -> BuiltWorldPrograms.build(prepared, capabilities, textures),
                ProgramTextures::create, listener, new ScopedProgramBinding(), new ScopedProgramState(), frame, camera,
                environment, fog);
    }

    static LiveWorldPrograms create(PackConfiguration configuration, ProgramBuilder builder, BuildListener listener,
            ScopedProgramBinding scopes, FrameInputs frame, CameraInputs camera, EnvironmentInputs environment,
            FogSource fog) {
        return create(configuration, builder, listener, scopes, new ScopedProgramState(), LwjglTextureGl.INSTANCE,
                frame, camera, environment, fog);
    }

    static LiveWorldPrograms create(PackConfiguration configuration, ProgramBuilder builder, BuildListener listener,
            ScopedProgramBinding scopes, ScopedProgramState state, TextureGl textureGl, FrameInputs frame,
            CameraInputs camera, EnvironmentInputs environment, FogSource fog) {
        return new LiveWorldPrograms(configuration,
                (prepared, textures) -> BuiltWorldPrograms.build(prepared, builder, textures),
                next -> ProgramTextures.create(next, textureGl), listener, scopes, state, frame, camera, environment,
                fog);
    }

    public PackConfiguration configuration() {
        return configuration;
    }

    public CustomUniformValues customValues() {
        return customValues;
    }

    @Nullable
    public ProgramTextures textures() {
        return textures;
    }

    public ScopedProgramState state() {
        return binding.state();
    }

    public void terrainStreams(TerrainStreamGate gate) {
        checkNotDeleted();
        if (worldOpen) {
            throw new IllegalStateException("The terrain stream gate can't change during a world pass");
        }
        binding.terrainStreams(gate);
    }

    public boolean applyConfiguration(PackConfiguration next) {
        Objects.requireNonNull(next, "configuration");
        checkNotDeleted();
        if (next.pack() != pack) {
            throw new IllegalArgumentException("The configuration belongs to shaderpack '" + next.pack().name()
                    + "', not '" + pack.name() + "'");
        }
        if (worldOpen) {
            throw new IllegalStateException("The pack configuration can't change during a world pass");
        }
        if (next.equals(configuration)) {
            return false;
        }
        configuration = next;
        customValues = new CustomUniformValues(next.customUniforms());
        binding.customValues(customValues);
        binding.terrainColors(next.lighting().changesTerrainColors());
        rethrow(deleteFolders(null));
        return true;
    }

    public DirectoryPrograms worldStart(int dimension) {
        checkClosed("when a world pass started");
        checkNotDeleted();
        DirectoryPrograms programs = selected;
        if (programs == null || dimension != selectedDimension) {
            programs = programsFor(pack.programDirectoryFor(dimension));
        }
        binding.select(programs.programs());
        selected = programs;
        selectedDimension = dimension;
        worldOpen = true;
        return programs;
    }

    public void worldStartWithoutWorld() {
        checkClosed("when a world pass started");
        checkNotDeleted();
        binding.select(null);
        selected = null;
        worldOpen = true;
    }

    private DirectoryPrograms programsFor(ProgramDirectory directory) {
        DirectoryPrograms programs = folders.get(directory);
        if (programs != null) {
            return programs;
        }
        if (textures == null) {
            textures = textureMaker.apply(configuration);
            listener.texturesMade(textures);
        }
        PreparedWorldPrograms prepared = PreparedWorldPrograms.prepare(pack, directory, configuration);
        programs = new DirectoryPrograms(directory, prepared, builder.apply(prepared, textures));
        folders.put(directory, programs);
        listener.built(programs);
        return programs;
    }

    @Nullable
    public DirectoryPrograms selected() {
        return selected;
    }

    public Collection<DirectoryPrograms> built() {
        return Collections.unmodifiableCollection(folders.values());
    }

    public void worldEnd() {
        worldOpen = false;
        checkClosed("at the end of a world pass");
    }

    public void frameEnd() {
        worldOpen = false;
        checkClosed("at the end of a frame");
    }

    public boolean stageStart(RenderStage stage, RenderDrawKind drawKind) {
        return stageStart(stage, drawKind, false);
    }

    public boolean stageStart(RenderStage stage, RenderDrawKind drawKind, boolean vanillaDraws) {
        if (!worldOpen) {
            return false;
        }
        binding.start(stage, drawKind, vanillaDraws);
        return true;
    }

    public void stageEnd(RenderStage stage, RenderDrawKind drawKind) {
        if (worldOpen) {
            binding.end(stage, drawKind);
        }
    }

    public void vanillaProgramsStart(RenderStage stage, RenderDrawKind drawKind) {
        if (worldOpen) {
            binding.suspend(stage, drawKind);
        }
    }

    public void vanillaProgramsEnd(RenderStage stage, RenderDrawKind drawKind) {
        if (worldOpen) {
            binding.resume(stage, drawKind);
        }
    }

    public boolean rendererReturned() {
        return worldOpen && binding.rendererReturned();
    }

    public void objectStart(RenderDrawKind kind, int id) {
        if (worldOpen) {
            binding.objectStart(kind, id);
        }
    }

    public void objectEnd() {
        if (worldOpen) {
            binding.objectEnd();
        }
    }

    public void objectColor(float red, float green, float blue, float alpha) {
        if (worldOpen) {
            binding.objectColor(red, green, blue, alpha);
        }
    }

    public void specialStart(RenderDrawKind kind) {
        if (worldOpen) {
            binding.specialStart(kind);
        }
    }

    public void specialEnd(RenderDrawKind kind) {
        if (worldOpen) {
            binding.specialEnd(kind);
        }
    }

    public void specialsAbandoned() {
        if (worldOpen) {
            binding.specialsAbandoned();
        }
    }

    private void checkClosed(String when) {
        if (binding.isEmpty() && !binding.isSuspended()) {
            return;
        }
        binding.abort();
        throw new IllegalStateException("A world program scope was still open or suspended " + when + ". The"
                + " program it replaced was put back.");
    }

    private void checkNotDeleted() {
        if (deleted) {
            throw new IllegalStateException("The world programs of this session were already deleted");
        }
    }

    public void delete() {
        worldOpen = false;
        deleted = true;
        Throwable failure = null;
        try {
            binding.abort();
        } catch (RuntimeException | LinkageError e) {
            failure = e;
        }
        rethrow(deleteFolders(failure));
    }

    @Nullable
    private Throwable deleteFolders(@Nullable Throwable earlier) {
        Throwable failure = earlier;
        binding.select(null);
        selected = null;
        for (DirectoryPrograms programs : folders.values()) {
            try {
                programs.programs().delete();
            } catch (RuntimeException | LinkageError e) {
                failure = collect(failure, e);
            }
        }
        folders.clear();
        ProgramTextures made = textures;
        textures = null;
        if (made != null) {
            try {
                made.delete();
            } catch (RuntimeException | LinkageError e) {
                failure = collect(failure, e);
            }
        }
        return failure;
    }

    private static void rethrow(@Nullable Throwable failure) {
        if (failure instanceof RuntimeException) {
            throw (RuntimeException) failure;
        }
        if (failure instanceof LinkageError) {
            throw (LinkageError) failure;
        }
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
}
