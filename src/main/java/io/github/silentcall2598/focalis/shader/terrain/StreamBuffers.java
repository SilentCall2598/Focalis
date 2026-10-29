// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.shader.terrain;

import javax.annotation.Nullable;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Objects;
import java.util.Set;

final class StreamBuffers {

    static final String NO_NAME = "the driver gave no buffer name";
    static final String NOT_KEPT = "the driver didn't keep all of the stream's bytes";

    private final AttributeGl gl;
    private final Set<BufferStream> owners = Collections.newSetFromMap(new IdentityHashMap<BufferStream, Boolean>());

    StreamBuffers(AttributeGl gl) {
        this.gl = Objects.requireNonNull(gl, "gl");
    }

    int live() {
        return owners.size();
    }

    @Nullable
    String upload(BufferStream stream, ByteBuffer data, int vertices, int generation) {
        return upload(stream, data, vertices, generation, StreamLayout.BUFFER);
    }

    @Nullable
    String upload(BufferStream stream, ByteBuffer data, int vertices, int generation, StreamLayout layout) {
        int bytes = vertices * layout.stride();
        int previous = gl.arrayBuffer();
        // A data store that a failed upload left behind would read back as the size asked for, so a buffer that held a
        // stream of the same size is emptied first.
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

    void release(BufferStream stream) {
        stream.valid = false;
        int name = stream.buffer;
        if (name != 0) {
            stream.buffer = 0;
            owners.remove(stream);
            gl.deleteBuffer(name);
        }
    }

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
