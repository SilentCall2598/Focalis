// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.shader.post;

import io.github.silentcall2598.focalis.shader.program.ShaderProgram;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.shader.Framebuffer;
import org.apache.logging.log4j.Logger;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

import javax.annotation.Nullable;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * Copies the world image as it stands into Focalis textures and draws the pack's post program back over Minecraft's
 * framebuffer. It runs at the end of the world pass, so the hand, HUD and screens still draw on top as usual.
 * Client thread only, with the GL context current.
 */
public final class ScenePostPass {

    // Unit 0 holds the block atlas and unit 1 the lightmap. These stay clear of both.
    static final int SCENE_COLOR_UNIT = 2;
    static final int SCENE_DEPTH_UNIT = 3;

    /** The only uniforms the pass sets, with the type each has to be declared as. */
    enum Input {
        SCENE_COLOR("sceneColor", GL20.GL_SAMPLER_2D, "sampler2D"),
        SCENE_DEPTH("sceneDepth", GL20.GL_SAMPLER_2D, "sampler2D"),
        VIEW_WIDTH("viewWidth", GL11.GL_FLOAT, "float"),
        VIEW_HEIGHT("viewHeight", GL11.GL_FLOAT, "float");

        final String uniformName;
        final int glType;
        final String glslType;

        Input(String uniformName, int glType, String glslType) {
            this.uniformName = uniformName;
            this.glType = glType;
            this.glslType = glslType;
        }

        @Nullable
        static Input named(String uniformName) {
            for (Input input : values()) {
                if (input.uniformName.equals(uniformName)) {
                    return input;
                }
            }
            return null;
        }
    }

    private final ShaderProgram program;
    private final Logger logger;
    private final int[] locations = new int[Input.values().length];
    private final PassGlState state = new PassGlState(SCENE_COLOR_UNIT, SCENE_DEPTH_UNIT);
    @Nullable
    private SceneCapture capture;

    private ScenePostPass(ShaderProgram program, Logger logger) {
        this.program = program;
        this.logger = logger;
        for (Input input : Input.values()) {
            // -1 when the program doesn't use it, and setting -1 does nothing.
            locations[input.ordinal()] = GL20.glGetUniformLocation(program.id(), input.uniformName);
        }
    }

    /**
     * Checks that the program only asks for inputs the pass provides. The pass owns the program once this returns,
     * and the caller keeps it if this throws.
     */
    public static ScenePostPass create(ShaderProgram program, Logger logger) throws PostPassException {
        checkUniforms(program);
        return new ScenePostPass(program, logger);
    }

    // A uniform nothing sets would quietly read zero, and a sampler would read whatever unit 0 holds. Refusing the
    // program is clearer than drawing something that looks broken for no visible reason.
    private static void checkUniforms(ShaderProgram program) throws PostPassException {
        int id = program.id();
        int count = GL20.glGetProgrami(id, GL20.GL_ACTIVE_UNIFORMS);
        int maxLength = GL20.glGetProgrami(id, GL20.GL_ACTIVE_UNIFORM_MAX_LENGTH);
        IntBuffer sizeAndType = BufferUtils.createIntBuffer(2);
        List<String> unknown = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            String name = GL20.glGetActiveUniform(id, i, maxLength, sizeAndType);
            // Some drivers list built-in uniforms as well.
            if (name.startsWith("gl_")) {
                continue;
            }
            Input input = Input.named(name);
            if (input == null) {
                unknown.add(name);
            } else if (sizeAndType.get(0) != 1 || sizeAndType.get(1) != input.glType) {
                throw new PostPassException(program.name() + " declares " + name + " with the wrong type. It has"
                        + " to be a " + input.glslType + ".");
            }
        }
        if (!unknown.isEmpty()) {
            throw new PostPassException(program.name() + " uses uniforms Focalis doesn't provide: " + unknown
                    + ". So far only sceneColor, sceneDepth, viewWidth and viewHeight are set.");
        }
    }

    /**
     * Returns false without changing anything when Minecraft's framebuffer isn't the one being drawn to, which
     * happens when something else renders the world into its own target.
     *
     * @throws PostPassException if the scene can't be captured safely on this setup
     */
    public boolean render(Framebuffer target) throws PostPassException {
        state.record();
        if (state.drawFramebuffer() != target.framebufferObject) {
            return false;
        }
        try {
            SceneCapture current = capture;
            if (current == null || !current.matches(target)) {
                current = replaceCapture(target);
            }
            current.copyFrom(target);
            draw(target, current);
        } finally {
            state.restore();
        }
        return true;
    }

    private SceneCapture replaceCapture(Framebuffer target) throws PostPassException {
        deleteCapture();
        SceneCapture created = SceneCapture.create(target, state, SCENE_COLOR_UNIT, SCENE_DEPTH_UNIT);
        capture = created;
        logger.info("Scene capture is {}x{} with {} depth", created.width(), created.height(),
                created.depthFormat());
        return created;
    }

    private void draw(Framebuffer target, SceneCapture capture) {
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, target.framebufferObject);
        GlStateManager.viewport(0, 0, capture.width(), capture.height());
        state.disableFragmentTests();
        GL20.glUseProgram(program.id());
        state.bindTexture(SCENE_COLOR_UNIT, capture.colorTexture());
        state.bindTexture(SCENE_DEPTH_UNIT, capture.depthTexture());
        GL20.glUniform1i(locations[Input.SCENE_COLOR.ordinal()], SCENE_COLOR_UNIT);
        GL20.glUniform1i(locations[Input.SCENE_DEPTH.ordinal()], SCENE_DEPTH_UNIT);
        GL20.glUniform1f(locations[Input.VIEW_WIDTH.ordinal()], capture.width());
        GL20.glUniform1f(locations[Input.VIEW_HEIGHT.ordinal()], capture.height());

        // One triangle with its corners already in clip space covers the screen without touching Minecraft's
        // matrices. It winds counterclockwise, so the back-face culling vanilla leaves on keeps it.
        GL11.glBegin(GL11.GL_TRIANGLES);
        GL11.glVertex2f(-1.0F, -1.0F);
        GL11.glVertex2f(3.0F, -1.0F);
        GL11.glVertex2f(-1.0F, 3.0F);
        GL11.glEnd();
    }

    /** Frees the capture and the program. Calling it again does nothing. */
    public void delete() {
        deleteCapture();
        program.delete();
    }

    private void deleteCapture() {
        if (capture != null) {
            capture.delete();
            capture = null;
        }
    }
}
