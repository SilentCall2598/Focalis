// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.shader.program;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Tracks which shader and program objects are alive, so tests can prove nothing leaks. */
final class RecordingShaderGl implements ShaderGl {

    final Set<Integer> liveShaders = new HashSet<>();
    final Set<Integer> livePrograms = new HashSet<>();
    final Map<Integer, Set<Integer>> attached = new HashMap<>();
    int calls;
    int createdPrograms;
    int deletedPrograms;
    int linkCalls;
    boolean deletedWhileAttached;

    @Nullable
    String failCompileWhenSourceContains;
    String compileLog = "";
    boolean failLink;
    String linkLog = "";
    // A RuntimeException or an Error, thrown from this link call on.
    @Nullable
    Throwable linkThrows;
    int linkThrowsFromCall = 1;
    // Programs in the order deleteProgram was called for them, including calls that threw.
    final List<Integer> deleteOrder = new ArrayList<>();
    // Thrown by the deleteProgram call with this number, counting from 1. That program stays alive.
    final Map<Integer, Throwable> deleteThrowsOnCall = new HashMap<>();

    private final Map<Integer, String> sources = new HashMap<>();
    private int nextId = 1;

    @Override
    public int createShader(int type) {
        calls++;
        int id = nextId++;
        liveShaders.add(id);
        return id;
    }

    @Override
    public void shaderSource(int shader, String source) {
        calls++;
        sources.put(shader, source);
    }

    @Override
    public void compileShader(int shader) {
        calls++;
    }

    @Override
    public boolean compileSucceeded(int shader) {
        calls++;
        return !failsToCompile(shader);
    }

    @Override
    public String shaderLog(int shader) {
        calls++;
        return failsToCompile(shader) ? compileLog : "";
    }

    @Override
    public void deleteShader(int shader) {
        calls++;
        for (Set<Integer> shaders : attached.values()) {
            deletedWhileAttached |= shaders.contains(shader);
        }
        liveShaders.remove(shader);
    }

    @Override
    public int createProgram() {
        calls++;
        createdPrograms++;
        int id = nextId++;
        livePrograms.add(id);
        attached.put(id, new HashSet<Integer>());
        return id;
    }

    @Override
    public void attachShader(int program, int shader) {
        calls++;
        attached.get(program).add(shader);
    }

    @Override
    public void detachShader(int program, int shader) {
        calls++;
        attached.get(program).remove(shader);
    }

    @Override
    public void linkProgram(int program) {
        calls++;
        linkCalls++;
        if (linkThrows != null && linkCalls >= linkThrowsFromCall) {
            throwUnchecked(linkThrows);
        }
    }

    @Override
    public boolean linkSucceeded(int program) {
        calls++;
        return !failLink;
    }

    @Override
    public String programLog(int program) {
        calls++;
        return linkLog;
    }

    @Override
    public void deleteProgram(int program) {
        calls++;
        deletedPrograms++;
        deleteOrder.add(program);
        Throwable failure = deleteThrowsOnCall.get(deletedPrograms);
        if (failure != null) {
            throwUnchecked(failure);
        }
        livePrograms.remove(program);
        attached.remove(program);
    }

    private static void throwUnchecked(Throwable failure) {
        if (failure instanceof Error) {
            throw (Error) failure;
        }
        throw (RuntimeException) failure;
    }

    private boolean failsToCompile(int shader) {
        return failCompileWhenSourceContains != null && sources.get(shader).contains(failCompileWhenSourceContains);
    }
}
