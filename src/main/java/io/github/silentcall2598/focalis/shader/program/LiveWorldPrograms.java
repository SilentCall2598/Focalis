// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.shader.program;

import io.github.silentcall2598.focalis.render.lifecycle.RenderDrawKind;
import io.github.silentcall2598.focalis.render.lifecycle.RenderStage;

import javax.annotation.Nullable;

/**
 * The world programs of one session and their binding, driven by the world pass. Owns the programs, which are built
 * once and kept through world reloads and dimension changes. Client thread only, with the GL context current.
 */
public final class LiveWorldPrograms {

    private final BuiltWorldPrograms programs;
    private final WorldProgramBinding binding;
    private boolean worldOpen;

    private LiveWorldPrograms(BuiltWorldPrograms programs, WorldProgramBinding binding) {
        this.programs = programs;
        this.binding = binding;
    }

    /** Builds every program {@code prepared} selected. Programs the pack or driver can't build stay unbound. */
    public static LiveWorldPrograms build(PreparedWorldPrograms prepared, ShaderCapabilities capabilities) {
        BuiltWorldPrograms programs = BuiltWorldPrograms.build(prepared, capabilities);
        return new LiveWorldPrograms(programs, new WorldProgramBinding(programs));
    }

    static LiveWorldPrograms build(PreparedWorldPrograms prepared, ProgramBuilder builder,
            ScopedProgramBinding scopes) {
        BuiltWorldPrograms programs = BuiltWorldPrograms.build(prepared, builder);
        return new LiveWorldPrograms(programs, new WorldProgramBinding(programs, scopes));
    }

    public BuiltWorldPrograms programs() {
        return programs;
    }

    /** Whether at least one program was built, so something can be bound. */
    public boolean usable() {
        for (BuiltWorldPrograms.Build build : programs.builds()) {
            if (build.succeeded()) {
                return true;
            }
        }
        return false;
    }

    public void worldStart() {
        checkClosed("when a world pass started");
        worldOpen = true;
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

    /**
     * Closes any open scope first, so no program is deleted while bound, then deletes every program. Both are
     * attempted even if the first throws, and the first failure is rethrown. Calling it again does nothing.
     */
    public void delete() {
        worldOpen = false;
        Throwable failure = null;
        try {
            binding.abort();
        } catch (RuntimeException | LinkageError e) {
            failure = e;
        }
        try {
            programs.delete();
        } catch (RuntimeException | LinkageError e) {
            failure = collect(failure, e);
        }
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
