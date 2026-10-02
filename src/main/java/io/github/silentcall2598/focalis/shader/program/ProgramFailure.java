// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.shader.program;

import io.github.silentcall2598.focalis.shader.pack.ShaderPath;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Why a shader program from the selected pack couldn't be built. This is a problem with the pack or the driver's
 * view of it, never a Focalis bug.
 */
public final class ProgramFailure {

    public enum Kind {
        /** The program doesn't have a usable set of stages, like a fragment shader with no vertex shader. */
        INVALID_STAGES,
        /** A stage needs something this OpenGL context doesn't offer. */
        UNSUPPORTED_STAGE,
        COMPILE,
        LINK,
        /** The program declares an input Focalis provides in a way it can't provide it, like a sampler as a float. */
        INPUTS
    }

    private final Kind kind;
    private final String program;
    @Nullable
    private final ShaderPath file;
    private final String summary;
    private final String driverLog;
    private final List<DriverLogMessage> messages;

    private ProgramFailure(Kind kind, String program, @Nullable ShaderPath file, String summary, String driverLog,
            List<DriverLogMessage> messages) {
        this.kind = kind;
        this.program = program;
        this.file = file;
        this.summary = summary;
        this.driverLog = driverLog;
        this.messages = Collections.unmodifiableList(messages);
    }

    static ProgramFailure invalidStages(String program, String summary) {
        return new ProgramFailure(Kind.INVALID_STAGES, program, null, summary, "",
                Collections.<DriverLogMessage>emptyList());
    }

    static ProgramFailure unsupportedStage(String program, PreparedProgram.Stage stage) {
        String summary = stage.file().fileName() + " needs " + ShaderCapabilities.requirement(stage.stage())
                + ", which this OpenGL context doesn't provide";
        return new ProgramFailure(Kind.UNSUPPORTED_STAGE, program, stage.file(), summary, "",
                Collections.<DriverLogMessage>emptyList());
    }

    static ProgramFailure compile(String program, PreparedProgram.Stage stage, String driverLog,
            List<DriverLogMessage> messages) {
        return new ProgramFailure(Kind.COMPILE, program, stage.file(), stage.file().fileName() + " failed to compile",
                driverLog, messages);
    }

    static ProgramFailure link(String program, String driverLog, List<DriverLogMessage> messages) {
        return new ProgramFailure(Kind.LINK, program, null, program + " failed to link", driverLog, messages);
    }

    static ProgramFailure inputs(String program, String summary) {
        return new ProgramFailure(Kind.INPUTS, program, null, summary, "", Collections.<DriverLogMessage>emptyList());
    }

    public Kind kind() {
        return kind;
    }

    /** The program name as users see it, like {@code world-1/composite}. */
    public String program() {
        return program;
    }

    /** The stage file that failed, or null when the failure isn't about one file, like a link error. */
    @Nullable
    public ShaderPath file() {
        return file;
    }

    /** A short plain-English line such as "gbuffers_terrain.fsh failed to compile". */
    public String summary() {
        return summary;
    }

    /** The complete info log exactly as the driver returned it. This is the source of truth. */
    public String driverLog() {
        return driverLog;
    }

    /** The driver log split into lines, with pack locations where the log format is understood. */
    public List<DriverLogMessage> messages() {
        return messages;
    }

    /** The summary plus the driver's first error, with its pack location when that's known. */
    @Override
    public String toString() {
        DriverLogMessage headline = headline();
        if (headline == null) {
            return summary;
        }
        if (headline.location() == null || headline.location().file() == null) {
            return summary + "\n" + headline.message();
        }
        return summary + "\nshaders/" + headline.location().file() + " line " + headline.location().line() + "\n"
                + headline.message();
    }

    // Later lines are often warnings or follow-on errors, and NVIDIA link logs start with a header line, so the
    // first line mentioning an error is the most useful one to show. The raw log stays complete either way.
    @Nullable
    private DriverLogMessage headline() {
        for (DriverLogMessage message : messages) {
            if (message.text().toLowerCase(Locale.ROOT).contains("error")) {
                return message;
            }
        }
        return messages.isEmpty() ? null : messages.get(0);
    }
}
