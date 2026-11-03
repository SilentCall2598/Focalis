// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.shader.terrain;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.ContextCapabilities;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL21;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GLContext;

import javax.annotation.Nullable;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.Locale;

final class StreamSelfTest {

    enum Outcome {
        PASSED,
        FAILED,
        UNSUPPORTED
    }

    static final class Result {

        final Outcome outcome;
        final String detail;

        Result(Outcome outcome, String detail) {
            this.outcome = outcome;
            this.detail = detail;
        }

        @Override
        public String toString() {
            return outcome + ", " + detail;
        }
    }

    private static final int VERTICES = 4;
    private static final int ROWS = 5;
    private static final float TOLERANCE = 1.0e-5f;
    private static final String[] ROW_NAMES = {"normal", "ID and midpoint", "tangent", "texture coordinates",
            "color"};

    private static final String VERTEX = "#version 120\n"
            + "attribute vec4 mc_Entity;\n"
            + "attribute vec4 mc_midTexCoord;\n"
            + "attribute vec4 at_tangent;\n"
            + "uniform float row;\n"
            + "varying vec4 value;\n"
            + "void main() {\n"
            + "    if (row < 0.5) value = vec4(gl_Normal, 0.0);\n"
            + "    else if (row < 1.5) value = vec4(mc_Entity.xy, mc_midTexCoord.xy);\n"
            + "    else if (row < 2.5) value = at_tangent;\n"
            + "    else if (row < 3.5) value = vec4(gl_MultiTexCoord0.xy, gl_MultiTexCoord1.xy);\n"
            + "    else value = gl_Color;\n"
            + "    gl_Position = vec4(gl_Vertex.x, (row + 0.5) / " + ROWS + ".0 * 2.0 - 1.0, 0.0, 1.0);\n"
            + "}\n";

    private static final String FRAGMENT = "#version 120\n"
            + "varying vec4 value;\n"
            + "void main() {\n"
            + "    gl_FragColor = value;\n"
            + "}\n";

    private static final byte[][] NORMAL_BYTES = {{127, 0, -128}, {64, -64, 1}, {0, 127, -1}, {-128, 100, 50}};
    private static final float[][] NORMAL_FLOATS = {{1f, 0f, -1f}, {0.5f, -0.5f, 0.25f}, {0f, 1f, -0.125f},
            {-1f, 0.75f, 0.375f}};
    private static final short[][] ENTITY = {{12345, 7}, {-1, 0}, {32767, -2}, {1, 3}};
    private static final float[][] MID = {{0.125f, 0.875f}, {0.333f, 0.667f}, {0.001f, 0.999f}, {0.5f, 0.25f}};
    private static final byte[][] TANGENT_BYTES = {{127, -128, 0, 127}, {-128, 127, 64, -128}, {1, -1, 127, 127},
            {0, 0, -128, -128}};
    private static final float[][] TANGENT_FLOATS = {{1f, -1f, 0f, 1f}, {-1f, 1f, 0.5f, -1f},
            {0.25f, -0.25f, 1f, 1f}, {0f, 0f, -1f, -1f}};
    private static final float[][] TEXTURE = {{0.0625f, 0.9375f}, {0.5f, 0.125f}, {0.75f, 0.0f}, {1.0f, 0.5f}};
    private static final short[][] LIGHTMAP = {{240, 15}, {0, 240}, {128, 64}, {8, 200}};
    private static final int[][] COLOR = {{255, 128, 0, 64}, {0, 255, 32, 255}, {16, 8, 255, 0}, {200, 100, 50, 25}};
    private static final int[][] STREAM_COLOR = {{1, 2, 200, 77}, {250, 3, 4, 5}, {99, 98, 97, 96},
            {128, 255, 129, 254}};

    private StreamSelfTest() {
    }

    static Result run(StreamLayout layout) {
        ContextCapabilities caps = GLContext.getCapabilities();
        if (!caps.OpenGL30) {
            return new Result(Outcome.UNSUPPORTED, "the stream needs OpenGL 3.0, since its check reads values back"
                    + " from a float framebuffer");
        }
        // An error that was already there can't be blamed on the check, and would hide whatever the check raises.
        int earlier = GL11.glGetError();
        Errors errors = new Errors();
        int program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        int drawFramebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int readFramebuffer = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int renderbuffer = GL11.glGetInteger(GL30.GL_RENDERBUFFER_BINDING);
        int packBuffer = GL11.glGetInteger(GL21.GL_PIXEL_PACK_BUFFER_BINDING);
        int arrayBuffer = GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glPushClientAttrib(GL11.GL_ALL_CLIENT_ATTRIB_BITS);
        Made made = new Made();
        Result result;
        try {
            result = draw(layout, made, errors);
        } finally {
            made.delete(errors);
            // The framebuffers first. Popping the attributes restores the draw buffer, which a different framebuffer
            // than the one bound when they were pushed turns down.
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, drawFramebuffer);
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readFramebuffer);
            errors.after("binding the framebuffers again");
            GL11.glPopClientAttrib();
            errors.after("restoring the client attributes");
            GL11.glPopAttrib();
            errors.after("restoring the attributes");
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, arrayBuffer);
            GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, packBuffer);
            GL30.glBindRenderbuffer(GL30.GL_RENDERBUFFER, renderbuffer);
            errors.after("binding the buffers again");
            GL20.glUseProgram(program);
            errors.after("binding the program again");
        }
        if (errors.first != null) {
            return new Result(Outcome.FAILED, errors.first + ", so the check can't be trusted");
        }
        if (earlier != 0 && result.outcome == Outcome.PASSED) {
            return new Result(Outcome.PASSED, result.detail + " (GL error 0x" + Integer.toHexString(earlier)
                    + " was already pending and is cleared)");
        }
        return result;
    }

    private static final class Errors {
        @Nullable
        String first;

        void after(String step) {
            int code = GL11.glGetError();
            if (code != 0 && first == null) {
                first = "GL error 0x" + Integer.toHexString(code) + " after " + step;
            }
        }
    }

    private static final class Made {
        int framebuffer;
        int renderbuffer;
        int vertexShader;
        int fragmentShader;
        int program;
        int vertexBuffer;
        int streamBuffer;
        int list;

        void delete(Errors errors) {
            if (list != 0) {
                GL11.glDeleteLists(list, 1);
            }
            errors.after("deleting the list");
            if (vertexBuffer != 0) {
                GL15.glDeleteBuffers(vertexBuffer);
            }
            if (streamBuffer != 0) {
                GL15.glDeleteBuffers(streamBuffer);
            }
            errors.after("deleting the buffers");
            if (program != 0) {
                GL20.glUseProgram(0);
                GL20.glDeleteProgram(program);
            }
            errors.after("deleting the program");
            if (vertexShader != 0) {
                GL20.glDeleteShader(vertexShader);
            }
            if (fragmentShader != 0) {
                GL20.glDeleteShader(fragmentShader);
            }
            if (framebuffer != 0) {
                GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
                GL30.glDeleteFramebuffers(framebuffer);
            }
            if (renderbuffer != 0) {
                GL30.glDeleteRenderbuffers(renderbuffer);
            }
            errors.after("deleting the render target");
        }
    }

    private static Result draw(StreamLayout layout, Made made, Errors errors) {
        made.framebuffer = GL30.glGenFramebuffers();
        made.renderbuffer = GL30.glGenRenderbuffers();
        GL30.glBindRenderbuffer(GL30.GL_RENDERBUFFER, made.renderbuffer);
        GL30.glRenderbufferStorage(GL30.GL_RENDERBUFFER, GL30.GL_RGBA32F, VERTICES, ROWS);
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, made.framebuffer);
        GL30.glFramebufferRenderbuffer(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL30.GL_RENDERBUFFER,
                made.renderbuffer);
        errors.after("creating the render target");
        int status = GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER);
        if (status != GL30.GL_FRAMEBUFFER_COMPLETE) {
            return new Result(Outcome.UNSUPPORTED, "its float render target is incomplete, status 0x"
                    + Integer.toHexString(status));
        }
        String linkProblem = link(made);
        if (linkProblem != null) {
            return new Result(Outcome.FAILED, linkProblem);
        }
        errors.after("linking the program");
        int row = GL20.glGetUniformLocation(made.program, "row");

        GL11.glViewport(0, 0, VERTICES, ROWS);
        for (int capability : new int[]{GL11.GL_DEPTH_TEST, GL11.GL_BLEND, GL11.GL_ALPHA_TEST, GL11.GL_SCISSOR_TEST,
                GL11.GL_STENCIL_TEST, GL11.GL_CULL_FACE, GL11.GL_POINT_SMOOTH, GL11.GL_DITHER,
                GL11.GL_COLOR_LOGIC_OP, GL13.GL_MULTISAMPLE, GL20.GL_VERTEX_PROGRAM_POINT_SIZE,
                GL20.GL_POINT_SPRITE}) {
            GL11.glDisable(capability);
        }
        GL11.glColorMask(true, true, true, true);
        GL11.glPointSize(1.0f);
        GL11.glClearColor(-9.0f, -9.0f, -9.0f, -9.0f);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
        GL20.glUseProgram(made.program);
        errors.after("setting up the draw state");

        ByteBuffer vertices = vanillaVertices();
        ByteBuffer stream = streamVertices(layout);
        AttributeGl gl = LwjglAttributeGl.INSTANCE;
        disableArrays();
        if (layout.forBuffers()) {
            made.vertexBuffer = GL15.glGenBuffers();
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, made.vertexBuffer);
            GL15.glBufferData(GL15.GL_ARRAY_BUFFER, vertices, GL15.GL_STATIC_DRAW);
            made.streamBuffer = GL15.glGenBuffers();
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, made.streamBuffer);
            GL15.glBufferData(GL15.GL_ARRAY_BUFFER, stream, GL15.GL_STATIC_DRAW);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, made.vertexBuffer);
            vanillaPointers(null);
            gl.bindArrayBuffer(made.streamBuffer);
            StreamArrays.pointAtBuffer(gl, layout);
            gl.bindArrayBuffer(made.vertexBuffer);
            StreamArrays.enable(gl, true);
            errors.after("pointing the arrays at the buffers");
            for (int r = 0; r < ROWS; r++) {
                GL20.glUniform1f(row, r);
                GL11.glDrawArrays(GL11.GL_POINTS, 0, VERTICES);
            }
            errors.after("drawing from buffers");
        } else {
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
            vanillaPointers(vertices);
            StreamArrays.pointAtClient(gl, layout, stream);
            StreamArrays.enable(gl, true);
            errors.after("pointing the arrays at client memory");
            made.list = GL11.glGenLists(1);
            GL11.glNewList(made.list, GL11.GL_COMPILE);
            GL11.glDrawArrays(GL11.GL_POINTS, 0, VERTICES);
            GL11.glEndList();
            errors.after("compiling the list");
            disableArrays();
            for (int r = 0; r < ROWS; r++) {
                GL20.glUniform1f(row, r);
                GL11.glCallList(made.list);
            }
            errors.after("calling the list");
        }
        GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, 0);
        GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 4);
        GL11.glPixelStorei(GL11.GL_PACK_ROW_LENGTH, 0);
        GL11.glPixelStorei(GL11.GL_PACK_SKIP_ROWS, 0);
        GL11.glPixelStorei(GL11.GL_PACK_SKIP_PIXELS, 0);
        FloatBuffer pixels = BufferUtils.createFloatBuffer(VERTICES * ROWS * 4);
        GL11.glReadPixels(0, 0, VERTICES, ROWS, GL11.GL_RGBA, GL11.GL_FLOAT, pixels);
        errors.after("reading the pixels back");
        return compare(layout, pixels);
    }

    @Nullable
    private static String link(Made made) {
        made.vertexShader = compile(GL20.GL_VERTEX_SHADER, VERTEX);
        made.fragmentShader = compile(GL20.GL_FRAGMENT_SHADER, FRAGMENT);
        if (made.vertexShader == 0 || made.fragmentShader == 0) {
            return "its shaders didn't compile";
        }
        made.program = GL20.glCreateProgram();
        GL20.glAttachShader(made.program, made.vertexShader);
        GL20.glAttachShader(made.program, made.fragmentShader);
        GL20.glBindAttribLocation(made.program, StreamLayout.ENTITY_LOCATION, StreamLayout.ENTITY);
        GL20.glBindAttribLocation(made.program, StreamLayout.MID_TEX_COORD_LOCATION, StreamLayout.MID_TEX_COORD);
        GL20.glBindAttribLocation(made.program, StreamLayout.TANGENT_LOCATION, StreamLayout.TANGENT);
        GL20.glLinkProgram(made.program);
        if (GL20.glGetProgrami(made.program, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) {
            String log = GL20.glGetProgramInfoLog(made.program, 2048).trim();
            return "the driver wouldn't link a program with the stream's attribute locations: " + firstLine(log);
        }
        String[] names = {StreamLayout.ENTITY, StreamLayout.MID_TEX_COORD, StreamLayout.TANGENT};
        int[] expected = {StreamLayout.ENTITY_LOCATION, StreamLayout.MID_TEX_COORD_LOCATION,
                StreamLayout.TANGENT_LOCATION};
        for (int i = 0; i < names.length; i++) {
            int location = GL20.glGetAttribLocation(made.program, names[i]);
            if (location != expected[i]) {
                return "the driver put " + names[i] + " at location " + location + " instead of " + expected[i];
            }
        }
        return null;
    }

    private static int compile(int type, String source) {
        int shader = GL20.glCreateShader(type);
        GL20.glShaderSource(shader, source);
        GL20.glCompileShader(shader);
        if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
            GL20.glDeleteShader(shader);
            return 0;
        }
        return shader;
    }

    private static String firstLine(String log) {
        int end = log.indexOf('\n');
        return end < 0 ? log : log.substring(0, end).trim();
    }

    private static ByteBuffer vanillaVertices() {
        ByteBuffer data = BufferUtils.createByteBuffer(VERTICES * StreamWriter.SOURCE_STRIDE);
        for (int i = 0; i < VERTICES; i++) {
            int at = i * StreamWriter.SOURCE_STRIDE;
            data.putFloat(at, (i + 0.5f) / VERTICES * 2.0f - 1.0f);
            data.putFloat(at + 4, 0.0f);
            data.putFloat(at + 8, 0.0f);
            for (int c = 0; c < 4; c++) {
                data.put(at + 12 + c, (byte) COLOR[i][c]);
            }
            data.putFloat(at + 16, TEXTURE[i][0]);
            data.putFloat(at + 20, TEXTURE[i][1]);
            data.putShort(at + 24, LIGHTMAP[i][0]);
            data.putShort(at + 26, LIGHTMAP[i][1]);
        }
        return data;
    }

    private static ByteBuffer streamVertices(StreamLayout layout) {
        int stride = layout.stride();
        ByteBuffer data = BufferUtils.createByteBuffer(VERTICES * stride);
        boolean compact = layout.normalType() == GL11.GL_BYTE;
        for (int i = 0; i < VERTICES; i++) {
            int at = i * stride;
            for (int c = 0; c < 3; c++) {
                if (compact) {
                    data.put(at + c, NORMAL_BYTES[i][c]);
                } else {
                    data.putFloat(at + c * 4, NORMAL_FLOATS[i][c]);
                }
            }
            data.putShort(at + layout.entityOffset(), ENTITY[i][0]);
            data.putShort(at + layout.entityOffset() + 2, ENTITY[i][1]);
            data.putFloat(at + layout.midTexCoordOffset(), MID[i][0]);
            data.putFloat(at + layout.midTexCoordOffset() + 4, MID[i][1]);
            for (int c = 0; c < 4; c++) {
                if (compact) {
                    data.put(at + layout.tangentOffset() + c, TANGENT_BYTES[i][c]);
                } else {
                    data.putFloat(at + layout.tangentOffset() + c * 4, TANGENT_FLOATS[i][c]);
                }
            }
            if (layout.hasColors()) {
                for (int c = 0; c < 4; c++) {
                    data.put(at + layout.colorOffset() + c, (byte) STREAM_COLOR[i][c]);
                }
            }
        }
        return data;
    }

    private static void vanillaPointers(@Nullable ByteBuffer client) {
        int stride = StreamWriter.SOURCE_STRIDE;
        if (client == null) {
            GL11.glVertexPointer(3, GL11.GL_FLOAT, stride, 0L);
            GL11.glColorPointer(4, GL11.GL_UNSIGNED_BYTE, stride, 12L);
            GL11.glTexCoordPointer(2, GL11.GL_FLOAT, stride, 16L);
            GL13.glClientActiveTexture(GL13.GL_TEXTURE1);
            GL11.glTexCoordPointer(2, GL11.GL_SHORT, stride, 24L);
        } else {
            client.position(0);
            GL11.glVertexPointer(3, GL11.GL_FLOAT, stride, client);
            client.position(12);
            GL11.glColorPointer(4, GL11.GL_UNSIGNED_BYTE, stride, client);
            client.position(16);
            GL11.glTexCoordPointer(2, GL11.GL_FLOAT, stride, client);
            GL13.glClientActiveTexture(GL13.GL_TEXTURE1);
            client.position(24);
            GL11.glTexCoordPointer(2, GL11.GL_SHORT, stride, client);
            client.position(0);
        }
        GL11.glEnableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
        GL13.glClientActiveTexture(GL13.GL_TEXTURE0);
        GL11.glEnableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
        GL11.glEnableClientState(GL11.GL_VERTEX_ARRAY);
        GL11.glEnableClientState(GL11.GL_COLOR_ARRAY);
    }

    private static void disableArrays() {
        GL11.glDisableClientState(GL11.GL_VERTEX_ARRAY);
        GL11.glDisableClientState(GL11.GL_COLOR_ARRAY);
        GL11.glDisableClientState(GL11.GL_NORMAL_ARRAY);
        GL11.glDisableClientState(GL11.GL_INDEX_ARRAY);
        GL11.glDisableClientState(GL11.GL_EDGE_FLAG_ARRAY);
        GL11.glDisableClientState(GL14.GL_SECONDARY_COLOR_ARRAY);
        GL11.glDisableClientState(GL14.GL_FOG_COORDINATE_ARRAY);
        int units = GL11.glGetInteger(GL20.GL_MAX_TEXTURE_COORDS);
        for (int unit = 0; unit < units; unit++) {
            GL13.glClientActiveTexture(GL13.GL_TEXTURE0 + unit);
            GL11.glDisableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
        }
        GL13.glClientActiveTexture(GL13.GL_TEXTURE0);
        int attributes = GL11.glGetInteger(GL20.GL_MAX_VERTEX_ATTRIBS);
        for (int index = 0; index < attributes; index++) {
            GL20.glDisableVertexAttribArray(index);
        }
    }

    private static Result compare(StreamLayout layout, FloatBuffer pixels) {
        boolean compact = layout.normalType() == GL11.GL_BYTE;
        int rules = BOTH;
        for (int r = 0; r < ROWS; r++) {
            for (int i = 0; i < VERTICES; i++) {
                int at = (r * VERTICES + i) * 4;
                for (int c = 0; c < 4; c++) {
                    float got = pixels.get(at + c);
                    boolean wrong;
                    if (compact && r == 0 && c < 3) {
                        rules &= byteRules(NORMAL_BYTES[i][c], got);
                        wrong = rules == 0;
                    } else if (compact && r == 2) {
                        rules &= byteRules(TANGENT_BYTES[i][c], got);
                        wrong = rules == 0;
                    } else {
                        float expected = expected(r, i, c, layout.hasColors());
                        wrong = !(Math.abs(got - expected) <= TOLERANCE * Math.max(1.0f, Math.abs(expected)));
                    }
                    if (wrong) {
                        return new Result(Outcome.FAILED, String.format(Locale.ROOT,
                                "the %s of vertex %d read %s as component %d", ROW_NAMES[r], i, got, c));
                    }
                }
            }
        }
        if (!compact) {
            return new Result(Outcome.PASSED, "every value read back exactly");
        }
        return new Result(Outcome.PASSED, rules == OLDER ? "normalized bytes follow the older (2c+1)/255 rule"
                : "normalized bytes follow the c/127 rule");
    }

    private static float expected(int r, int i, int c, boolean streamColors) {
        switch (r) {
            case 0:
                return c < 3 ? NORMAL_FLOATS[i][c] : 0.0f;
            case 1:
                return c < 2 ? ENTITY[i][c] : MID[i][c - 2];
            case 2:
                return TANGENT_FLOATS[i][c];
            case 3:
                return c < 2 ? TEXTURE[i][c] : LIGHTMAP[i][c - 2];
            default:
                return (streamColors ? STREAM_COLOR : COLOR)[i][c] / 255.0f;
        }
    }

    private static final int CURRENT = 1;
    private static final int OLDER = 2;
    private static final int BOTH = CURRENT | OLDER;

    private static int byteRules(byte stored, float got) {
        int rules = 0;
        if (Math.abs(got - Math.max(stored / 127.0f, -1.0f)) <= TOLERANCE) {
            rules |= CURRENT;
        }
        if (Math.abs(got - (2 * stored + 1) / 255.0f) <= TOLERANCE) {
            rules |= OLDER;
        }
        return rules;
    }
}
