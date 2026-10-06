// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.shader.program;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Tracks which shader and program objects are alive, so tests can prove nothing leaks. It can bind programs for
 * {@link ScopedProgramBinding} too, so uniforms set by the binding land on the program it made current.
 */
final class RecordingShaderGl implements ShaderGl, ProgramBindingGl {

    private static final int FLOAT = 0x1406;
    private static final Pattern UNIFORM = Pattern.compile("uniform\\s+(\\w+)\\s+(\\w+)\\s*(?:\\[(\\d+)\\])?\\s*;");

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

    // Uniforms come from "uniform <type> <name>;" lines in the sources attached when a program links. Names in here
    // count as optimized out, so the driver doesn't report them.
    final Set<String> optimizedOut = new HashSet<>();
    // Arrays whose code only reads the first element, so the driver reports a size of 1.
    final Set<String> trimmedArrays = new HashSet<>();
    // The current program, as glUseProgram leaves it.
    int current;
    final List<Integer> uses = new ArrayList<>();
    // Every uniform1i as "program:name=value", against whatever program was current.
    final List<String> uniformSets = new ArrayList<>();
    // Uniform1i calls GL would reject, like with no program current or a location of another program.
    int uniformErrors;
    int activeUniformQueries;
    int locationQueries;
    // Thrown by the useProgram call with this number, counting from 1. The current program doesn't change then.
    final Map<Integer, Throwable> useThrowsOnCall = new HashMap<>();
    // Thrown by the useProgram call with this number after it bound the program, like a wrapper that checks for
    // errors after the call and finds one left over from earlier.
    final Map<Integer, Throwable> useThrowsAfterBindOnCall = new HashMap<>();
    // Thrown by the currentProgram call with this number, counting from 1.
    final Map<Integer, Throwable> currentThrowsOnCall = new HashMap<>();
    private int currentQueries;
    // Thrown by every uniform call from the one with this number on, counting from 1.
    @Nullable
    Throwable uniformThrows;
    int uniformThrowsFromCall = 1;
    int uniformCalls;

    private final Map<Integer, List<ActiveUniform>> linkedUniforms = new HashMap<>();
    private final Map<Integer, Map<Integer, Number>> uniformValues = new HashMap<>();

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
        Map<String, ActiveUniform> uniforms = new LinkedHashMap<>();
        for (int shader : attached.get(program)) {
            Matcher matcher = UNIFORM.matcher(sources.get(shader));
            while (matcher.find()) {
                String name = matcher.group(2);
                if (!optimizedOut.contains(name)) {
                    int size = matcher.group(3) == null ? 1 : Integer.parseInt(matcher.group(3));
                    // Like a driver, an array is reported as its first element, with the size cut to what is used.
                    uniforms.put(name, new ActiveUniform(matcher.group(3) == null ? name : name + "[0]",
                            glType(matcher.group(1)), trimmedArrays.contains(name) ? 1 : size));
                }
            }
        }
        linkedUniforms.put(program, new ArrayList<>(uniforms.values()));
        uniformValues.put(program, new HashMap<Integer, Number>());
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

    @Override
    public List<ActiveUniform> activeUniforms(int program) {
        calls++;
        activeUniformQueries++;
        return new ArrayList<>(linkedUniforms.get(program));
    }

    @Override
    public int uniformLocation(int program, String name) {
        calls++;
        locationQueries++;
        return find(program, name);
    }

    private int find(int program, String name) {
        List<ActiveUniform> uniforms = linkedUniforms.get(program);
        for (int i = 0; i < uniforms.size(); i++) {
            String reported = uniforms.get(i).name;
            if (reported.equals(name) || reported.equals(name + "[0]")) {
                return location(program, i);
            }
        }
        return -1;
    }

    @Override
    public void uniform1i(int location, int value) {
        set(location, value, false);
    }

    @Override
    public void uniform1f(int location, float value) {
        set(location, value, true);
    }

    // Like GL, an int goes into an int or a sampler and a float only into a float.
    private void set(int location, Number value, boolean isFloat) {
        calls++;
        uniformCalls++;
        if (uniformThrows != null && uniformCalls >= uniformThrowsFromCall) {
            throwUnchecked(uniformThrows);
        }
        if (location == -1) {
            return;
        }
        List<ActiveUniform> uniforms = current == 0 ? null : linkedUniforms.get(current);
        int index = location - location(current, 0);
        if (uniforms == null || index < 0 || index >= uniforms.size()
                || (uniforms.get(index).type == FLOAT) != isFloat) {
            uniformErrors++;
            return;
        }
        uniformValues.get(current).put(location, value);
        uniformSets.add(current + ":" + uniforms.get(index).name + "=" + value);
    }

    @Override
    public int currentProgram() {
        calls++;
        currentQueries++;
        Throwable failure = currentThrowsOnCall.get(currentQueries);
        if (failure != null) {
            throwUnchecked(failure);
        }
        return current;
    }

    @Override
    public void useProgram(int program) {
        calls++;
        uses.add(program);
        Throwable failure = useThrowsOnCall.get(uses.size());
        if (failure != null) {
            throwUnchecked(failure);
        }
        current = program;
        Throwable late = useThrowsAfterBindOnCall.get(uses.size());
        if (late != null) {
            throwUnchecked(late);
        }
    }

    /** The value a uniform of a program holds, 0 until something set it, like after a real link. */
    int uniformValue(int program, String name) {
        Number value = uniformValues.get(program).get(find(program, name));
        return value == null ? 0 : value.intValue();
    }

    float floatValue(int program, String name) {
        Number value = uniformValues.get(program).get(find(program, name));
        return value == null ? 0 : value.floatValue();
    }

    // Locations are per program here, so one used with the wrong program is caught.
    private static int location(int program, int index) {
        return program * 100 + index;
    }

    private static int glType(String glsl) {
        switch (glsl) {
            case "sampler1D":
                return 0x8B5D;
            case "sampler2D":
                return 0x8B5E;
            case "sampler3D":
                return 0x8B5F;
            case "samplerCube":
                return 0x8B60;
            case "sampler2DShadow":
                return 0x8B62;
            case "float":
                return 0x1406;
            case "int":
                return 0x1404;
            case "vec4":
                return 0x8B52;
            default:
                return 0;
        }
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
