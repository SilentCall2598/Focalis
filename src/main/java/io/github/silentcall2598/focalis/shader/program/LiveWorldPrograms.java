// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.shader.program;

import io.github.silentcall2598.focalis.render.lifecycle.RenderDrawKind;
import io.github.silentcall2598.focalis.render.lifecycle.RenderStage;
import io.github.silentcall2598.focalis.shader.pack.ProgramDirectory;
import io.github.silentcall2598.focalis.shader.pack.ShaderMacros;
import io.github.silentcall2598.focalis.shader.pack.ShaderPack;
import io.github.silentcall2598.focalis.shader.pack.StandardMacros;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * The world programs of one session and their binding, driven by the world pass. Each world pass binds the programs
 * of the folder its dimension selects. A folder's programs are prepared and built the first time a dimension needs
 * them and kept for the rest of the session, failures included, so going back to a dimension or rejoining never
 * builds again. Owns every program it built. Client thread only, with the GL context current.
 */
public final class LiveWorldPrograms {

    /** Hears about each folder right after its programs were built and before anything binds them. */
    public interface BuildListener {

        void built(DirectoryPrograms programs);
    }

    private final ShaderPack pack;
    private final Function<PreparedWorldPrograms, BuiltWorldPrograms> builder;
    private final BuildListener listener;
    private final WorldProgramBinding binding;
    // ProgramDirectory has no equals, so this is keyed by the folder object. Dimensions without their own folder
    // all share the entry of the shaders folder.
    private final Map<ProgramDirectory, DirectoryPrograms> folders = new LinkedHashMap<>();
    @Nullable
    private DirectoryPrograms selected;
    private int selectedDimension;
    private boolean worldOpen;
    private boolean deleted;

    private LiveWorldPrograms(ShaderPack pack, Function<PreparedWorldPrograms, BuiltWorldPrograms> builder,
            BuildListener listener, ScopedProgramBinding scopes, FrameInputs frame, CameraInputs camera,
            EnvironmentInputs environment, FogSource fog) {
        this.pack = Objects.requireNonNull(pack, "pack");
        this.builder = builder;
        this.listener = Objects.requireNonNull(listener, "listener");
        this.binding = new WorldProgramBinding(scopes, frame, camera, environment, fog);
    }

    /**
     * Nothing is built until a world pass needs it.
     *
     * @param frame the frame values every program of the session gets. The caller captures it at the start of each
     *     frame, and before the first world pass.
     * @param camera the camera values every program of the session gets. The caller starts every world pass on it
     *     and captures the pass's camera before its first world stage binds anything.
     * @param environment the world and environment values every program of the session gets. The caller takes them
     *     right after the camera of each world pass.
     * @param fog read whenever a stage binds a program that uses fog values
     */
    public static LiveWorldPrograms create(ShaderPack pack, ShaderCapabilities capabilities, BuildListener listener,
            FrameInputs frame, CameraInputs camera, EnvironmentInputs environment, FogSource fog) {
        Objects.requireNonNull(capabilities, "capabilities");
        return new LiveWorldPrograms(pack, prepared -> BuiltWorldPrograms.build(prepared, capabilities), listener,
                new ScopedProgramBinding(), frame, camera, environment, fog);
    }

    static LiveWorldPrograms create(ShaderPack pack, ProgramBuilder builder, BuildListener listener,
            ScopedProgramBinding scopes, FrameInputs frame, CameraInputs camera, EnvironmentInputs environment,
            FogSource fog) {
        return new LiveWorldPrograms(pack, prepared -> BuiltWorldPrograms.build(prepared, builder), listener,
                scopes, frame, camera, environment, fog);
    }

    /**
     * Starts a world pass in {@code dimension} and selects the programs of the folder it uses, building them first if
     * no world pass needed that folder before. Programs the pack or driver can't build stay unbound.
     *
     * @return the selected folder's programs
     */
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

    /** Starts a world pass without a client world. It has no dimension, so nothing is bound in it. */
    public void worldStartWithoutWorld() {
        checkClosed("when a world pass started");
        checkNotDeleted();
        binding.select(null);
        selected = null;
        worldOpen = true;
    }

    // A folder only goes in once its build returned, so a build that throws never leaves half a folder behind.
    private DirectoryPrograms programsFor(ProgramDirectory directory) {
        DirectoryPrograms programs = folders.get(directory);
        if (programs != null) {
            return programs;
        }
        PreparedWorldPrograms prepared = PreparedWorldPrograms.prepare(pack, directory, StandardMacros.environment(),
                ShaderMacros.empty());
        programs = new DirectoryPrograms(directory, prepared, builder.apply(prepared));
        folders.put(directory, programs);
        listener.built(programs);
        return programs;
    }

    /** The programs the current or last world pass selected, or null when it had no world. */
    @Nullable
    public DirectoryPrograms selected() {
        return selected;
    }

    /** Every folder built so far, in the order they were first needed. Read only. */
    public Collection<DirectoryPrograms> built() {
        return Collections.unmodifiableCollection(folders.values());
    }

    public void worldEnd() {
        worldOpen = false;
        checkClosed("at the end of a world pass");
    }

    /** Catches a world pass whose end never came, since that end is a Forge event something could skip. */
    public void frameEnd() {
        worldOpen = false;
        checkClosed("at the end of a frame");
    }

    /**
     * Opens the program scope of a stage inside the world pass. Other mods can draw things like sky or entities
     * outside it, and those are left alone along with their END.
     *
     * @return whether a scope was opened
     */
    public boolean stageStart(RenderStage stage, RenderDrawKind drawKind) {
        if (!worldOpen) {
            return false;
        }
        binding.start(stage, drawKind);
        return true;
    }

    // A START outside the world pass always has its END outside too, since a scope left open fails the next check.
    public void stageEnd(RenderStage stage, RenderDrawKind drawKind) {
        if (worldOpen) {
            binding.end(stage, drawKind);
        }
    }

    /** Steps aside while vanilla binds its own programs inside the world pass, like for entity outlines. */
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

    /**
     * Right after one entity or block entity renderer returned. It may have bound its own programs and left another
     * one bound, so the stage's program is bound again if needed.
     *
     * @return whether the program had to be bound again
     */
    public boolean rendererReturned() {
        return worldOpen && binding.reassert();
    }

    // A scope still open here never got its END, or a suspension its resume. Both are closed so no program stays
    // bound, and this throws because Focalis lost track of the stages.
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

    /**
     * Closes any open scope first, so no program is deleted while bound, then deletes the programs of every folder
     * once. Everything is attempted even if something throws, and the first failure is rethrown. Calling it again
     * does nothing.
     */
    public void delete() {
        worldOpen = false;
        deleted = true;
        Throwable failure = null;
        try {
            binding.abort();
        } catch (RuntimeException | LinkageError e) {
            failure = e;
        }
        // Aborting always empties the scopes, so nothing can refuse this.
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
