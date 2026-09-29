// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.shader.program;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Keeps a current program like the driver would and records every change, so tests can check binding order. */
final class RecordingProgramBindingGl implements ProgramBindingGl {

    int current;
    int queries;
    final List<Integer> uses = new ArrayList<>();
    // A RuntimeException or an Error thrown by currentProgram.
    @Nullable
    Throwable queryThrows;
    // Thrown by the useProgram call with this number, counting from 1. The current program doesn't change then.
    final Map<Integer, Throwable> useThrowsOnCall = new HashMap<>();

    RecordingProgramBindingGl(int current) {
        this.current = current;
    }

    @Override
    public int currentProgram() {
        queries++;
        if (queryThrows != null) {
            throwUnchecked(queryThrows);
        }
        return current;
    }

    @Override
    public void useProgram(int program) {
        uses.add(program);
        Throwable failure = useThrowsOnCall.get(uses.size());
        if (failure != null) {
            throwUnchecked(failure);
        }
        current = program;
    }

    private static void throwUnchecked(Throwable failure) {
        if (failure instanceof Error) {
            throw (Error) failure;
        }
        throw (RuntimeException) failure;
    }
}
