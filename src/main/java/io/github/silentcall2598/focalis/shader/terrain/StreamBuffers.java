// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.shader.terrain;

import javax.annotation.Nullable;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Objects;
import java.util.Set;

/**
 * Makes, checks and deletes the GL buffers of the streams of vertex buffer chunk layers, and knows which of them exist.
 * A stream is only valid once the driver named its buffer and kept every byte of the upload. That is read back from the
 * buffer and not from the error flag, which may hold errors that are anyone's. Client thread only.
 */
final class StreamBuffers {

    static final String NO_NAME = "the driver gave no buffer name";
    static final String NOT_KEPT = "the driver didn't keep all of the stream's bytes";

    private final AttributeGl gl;
    private final Set<BufferStream> owners = Collections.newSetFromMap(new IdentityHashMap<BufferStream, Boolean>());

    StreamBuffers(AttributeGl gl) {
        this.gl = Objects.requireNonNull(gl, "gl");
    }

    /** How many stream buffers exist. */
    int live() {
        return owners.size();
    }

    /**
     * Puts the first {@code vertices} vertices of {@code data} into the stream's buffer, which is made if it has none,
     * and only then marks the stream valid. A buffer that held a stream of the same size is emptied first, since a data
     * store that a failed upload left behind would read back as the size asked for. The array buffer binding of the
     * caller is back whatever happens, and a failure deletes the buffer, so nothing it held is taken for the new data.
     *
     * @return null when the stream is valid now, otherwise why not, and it has no buffer then
     */
    @Nullable
    String upload(BufferStream stream, ByteBuffer data, int vertices, int generation) {
        int bytes = vertices * StreamLayout.BUFFER.stride();
        int previous = gl.arrayBuffer();
        boolean emptyFirst = stream.buffer != 0 && stream.vertices == vertices;
        stream.valid = false;
        if (stream.buffer == 0) {
            int name = gl.createBuffer();
            if (name == 0) {
                return NO_NAME;
            }
            stream.buffer = name;
            owners.add(stream);
        }
        boolean kept = false;
        try {
            gl.bindArrayBuffer(stream.buffer);
            if (emptyFirst) {
                gl.emptyArrayBuffer();
            }
            data.position(0).limit(bytes);
            gl.bufferData(data);
            kept = gl.arrayBufferSize() == bytes;
        } finally {
            try {
                data.clear();
                gl.bindArrayBuffer(previous);
            } finally {
                if (!kept) {
                    release(stream);
                }
            }
        }
        if (!kept) {
            return NOT_KEPT;
        }
        stream.vertices = vertices;
        stream.generation = generation;
        stream.valid = true;
        return null;
    }

    /** Deletes the stream's buffer, if it has one, and makes the stream invalid. Safe to repeat. */
    void release(BufferStream stream) {
        stream.valid = false;
        int name = stream.buffer;
        if (name != 0) {
            // Forgotten first, so a failing delete can't be tried twice.
            stream.buffer = 0;
            owners.remove(stream);
            gl.deleteBuffer(name);
        }
    }

    /** Deletes every stream buffer there is. All of them are tried, and the first failure is thrown after that. */
    void releaseAll() {
        Throwable failure = null;
        for (BufferStream stream : owners.toArray(new BufferStream[0])) {
            try {
                release(stream);
            } catch (RuntimeException | LinkageError e) {
                if (failure == null) {
                    failure = e;
                } else {
                    failure.addSuppressed(e);
                }
            }
        }
        if (failure instanceof RuntimeException) {
            throw (RuntimeException) failure;
        }
        if (failure instanceof LinkageError) {
            throw (LinkageError) failure;
        }
    }
}
