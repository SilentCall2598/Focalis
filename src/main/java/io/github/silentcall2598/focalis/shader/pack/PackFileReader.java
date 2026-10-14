// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.shader.pack;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/** Reads pack files with a size limit, since packs are untrusted input. */
final class PackFileReader {

    /** Far above any real shader file, but keeps a bad ZIP entry from eating the heap. */
    static final int MAX_FILE_BYTES = 8 * 1024 * 1024;
    /** Images can be bigger than any shader file, like a large lookup table. */
    static final int MAX_IMAGE_BYTES = 32 * 1024 * 1024;

    private static final char BYTE_ORDER_MARK = 0xFEFF;

    private PackFileReader() {
    }

    static String readText(InputStream input, String name) throws IOException {
        String text = new String(readBytes(input, name, MAX_FILE_BYTES), StandardCharsets.UTF_8);
        // Some editors on Windows save UTF-8 with a byte order mark, which GLSL compilers reject.
        return !text.isEmpty() && text.charAt(0) == BYTE_ORDER_MARK ? text.substring(1) : text;
    }

    static byte[] readBytes(InputStream input, String name, int limit) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = input.read(buffer)) != -1) {
            if (bytes.size() + read > limit) {
                throw new IOException(name + " is larger than " + limit + " bytes");
            }
            bytes.write(buffer, 0, read);
        }
        return bytes.toByteArray();
    }
}
