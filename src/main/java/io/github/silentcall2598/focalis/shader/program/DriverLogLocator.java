// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.shader.program;

import io.github.silentcall2598.focalis.shader.pack.ResolvedSource;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Splits a driver log into lines and points them back at pack files. Vendors format logs differently, so only
 * formats seen from real drivers are matched. Everything else is kept as plain text.
 */
final class DriverLogLocator {

    // Each pattern captures the source string index, the line number and the message. Only NVIDIA has been checked,
    // on driver 616.92, where each line starts with something like 0(5) for string 0, line 5.
    private static final List<Pattern> KNOWN_FORMATS = Collections.singletonList(
            Pattern.compile("(\\d+)\\((\\d+)\\) : (.+)"));

    private DriverLogLocator() {
    }

    /** @param source the source the log is about, or null for logs like link errors that span several stages */
    static List<DriverLogMessage> locate(String driverLog, @Nullable ResolvedSource source) {
        List<DriverLogMessage> messages = new ArrayList<>();
        for (String line : driverLog.split("\r?\n")) {
            if (!line.trim().isEmpty()) {
                messages.add(locateLine(line, source));
            }
        }
        return messages;
    }

    private static DriverLogMessage locateLine(String line, @Nullable ResolvedSource source) {
        for (Pattern format : KNOWN_FORMATS) {
            Matcher matcher = format.matcher(line);
            if (!matcher.matches()) {
                continue;
            }
            int sourceString = Integer.parseInt(matcher.group(1));
            int lineNumber = Integer.parseInt(matcher.group(2));
            // Focalis always hands the driver a single source string, so anything else can't be mapped.
            if (source != null && sourceString == 0 && lineNumber >= 1 && lineNumber <= source.lineCount()) {
                return new DriverLogMessage(line, matcher.group(3), source.origin(lineNumber));
            }
            return new DriverLogMessage(line, matcher.group(3), null);
        }
        return new DriverLogMessage(line, line, null);
    }
}
