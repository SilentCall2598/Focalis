// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.shader.program;

import io.github.silentcall2598.focalis.shader.pack.ProgramStage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Collections;

import static io.github.silentcall2598.focalis.shader.program.TestPrograms.FRAGMENT;
import static io.github.silentcall2598.focalis.shader.program.TestPrograms.VERTEX;
import static io.github.silentcall2598.focalis.shader.program.TestPrograms.prepareFinal;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProgramBuilderTest {

    private static final ShaderCapabilities EVERYTHING = new ShaderCapabilities(true, true, true);

    @TempDir
    Path temp;

    private final RecordingShaderGl gl = new RecordingShaderGl();

    @Test
    void successfulBuildLeavesOnlyTheProgramAlive() throws Exception {
        ShaderProgram program = new ProgramBuilder(EVERYTHING, gl).build(prepareFinal(temp,
                "final.vsh", VERTEX, "final.fsh", FRAGMENT));

        assertEquals(Collections.singleton(program.id()), gl.livePrograms);
        assertTrue(gl.liveShaders.isEmpty());
        assertTrue(gl.attached.get(program.id()).isEmpty());
        assertFalse(gl.deletedWhileAttached);

        program.delete();
        int callsAfterDelete = gl.calls;
        program.delete();
        assertTrue(gl.livePrograms.isEmpty());
        assertEquals(callsAfterDelete, gl.calls);
        assertThrows(IllegalStateException.class, program::id);
    }

    @Test
    void compileFailureDeletesEarlierStagesAndKeepsTheRawLog() throws Exception {
        gl.failCompileWhenSourceContains = "BROKEN";
        gl.compileLog = "0(3) : error C0000: syntax error\n0(4) : warning C7532: later warning\n";

        ProgramBuildException error = assertThrows(ProgramBuildException.class, () ->
                new ProgramBuilder(EVERYTHING, gl).build(prepareFinal(temp,
                        "final.vsh", VERTEX, "final.fsh", "#version 120\nBROKEN\nvoid main() {}\n")));

        ProgramFailure failure = error.failure();
        assertEquals(ProgramFailure.Kind.COMPILE, failure.kind());
        assertEquals("final.fsh", String.valueOf(failure.file()));
        assertEquals(gl.compileLog, failure.driverLog());
        assertEquals("final.fsh failed to compile\nshaders/final.fsh line 2\nerror C0000: syntax error",
                failure.toString());
        assertTrue(gl.liveShaders.isEmpty());
        assertTrue(gl.livePrograms.isEmpty());
    }

    @Test
    void linkFailureDeletesTheProgramAndItsStages() throws Exception {
        gl.failLink = true;
        gl.linkLog = "Fragment info\n-------------\n(0) : error C3001: no program defined\n";

        ProgramBuildException error = assertThrows(ProgramBuildException.class, () ->
                new ProgramBuilder(EVERYTHING, gl).build(prepareFinal(temp,
                        "final.vsh", VERTEX, "final.fsh", FRAGMENT)));

        ProgramFailure failure = error.failure();
        assertEquals(ProgramFailure.Kind.LINK, failure.kind());
        assertNull(failure.file());
        assertEquals(gl.linkLog, failure.driverLog());
        assertEquals("final failed to link\n(0) : error C3001: no program defined", failure.toString());
        assertTrue(gl.liveShaders.isEmpty());
        assertTrue(gl.livePrograms.isEmpty());
        assertFalse(gl.deletedWhileAttached);
    }

    @Test
    void unexpectedGlFailureIsNotReportedAsAPackProblemButStillCleansUp() throws Exception {
        gl.linkThrows = new IllegalStateException("context lost");
        PreparedProgram prepared = prepareFinal(temp, "final.vsh", VERTEX, "final.fsh", FRAGMENT);

        assertThrows(IllegalStateException.class, () -> new ProgramBuilder(EVERYTHING, gl).build(prepared));

        assertTrue(gl.liveShaders.isEmpty());
        assertTrue(gl.livePrograms.isEmpty());
    }

    @Test
    void programsThatCanNeverWorkFailBeforeAnyGlCall() throws Exception {
        PreparedProgram fragmentOnly = prepareFinal(temp, "final.fsh", FRAGMENT);
        PreparedProgram withGeometry = prepareFinal(temp, "final.vsh", VERTEX, "final.fsh", FRAGMENT,
                "final.gsh", "#version 150\nvoid main() {}\n");

        ProgramBuildException missing = assertThrows(ProgramBuildException.class, () ->
                new ProgramBuilder(EVERYTHING, gl).build(fragmentOnly));
        ProgramBuildException unsupported = assertThrows(ProgramBuildException.class, () ->
                new ProgramBuilder(new ShaderCapabilities(true, false, false), gl).build(withGeometry));

        assertEquals(ProgramFailure.Kind.INVALID_STAGES, missing.failure().kind());
        assertEquals(ProgramFailure.Kind.UNSUPPORTED_STAGE, unsupported.failure().kind());
        assertTrue(unsupported.failure().summary().contains("OpenGL 3.2"), unsupported.failure().summary());
        assertEquals(0, gl.calls);
    }

    @Test
    void stagesMapToTheirGlShaderTypesAndCapabilities() {
        ShaderCapabilities basic = new ShaderCapabilities(true, false, false);

        assertEquals(0x8B31, ShaderCompiler.glShaderType(ProgramStage.VERTEX));
        assertEquals(0x8B30, ShaderCompiler.glShaderType(ProgramStage.FRAGMENT));
        assertEquals(0x8DD9, ShaderCompiler.glShaderType(ProgramStage.GEOMETRY));
        assertEquals(0x91B9, ShaderCompiler.glShaderType(ProgramStage.COMPUTE));
        assertTrue(basic.supports(ProgramStage.VERTEX) && basic.supports(ProgramStage.FRAGMENT));
        assertFalse(basic.supports(ProgramStage.GEOMETRY) || basic.supports(ProgramStage.COMPUTE));
    }
}
