// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.shader.program;

import io.github.silentcall2598.focalis.shader.pack.PackConfiguration;
import io.github.silentcall2598.focalis.shader.pack.PackImage;
import io.github.silentcall2598.focalis.shader.pack.PackTextures;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * The GL textures one pack configuration gives its gbuffers programs, made once the first time a world pass needs
 * programs and shared by every folder and dimension. Owns each texture it made and deletes each once. A declared
 * texture that can't be made is kept as a failure, so the programs that sample it can be left unbuilt while the
 * rest still work. Nothing is bound afterwards. Client thread only, with the GL context current.
 */
public final class ProgramTextures {

    /** The units a gbuffers custom texture can use go up to noisetex on 15. */
    public static final int UNITS = 16;

    /** No textures. Never touches GL. */
    public static final ProgramTextures NONE = new ProgramTextures(null, new Outcome[UNITS],
            Collections.<Outcome>emptyList());

    /** How one declared unit came out. */
    public static final class Outcome {

        private final PackTextures.Declaration declaration;
        private final int texture;
        @Nullable
        private final String failure;
        private final int width;
        private final int height;
        private final boolean linear;
        private final boolean clamp;

        private Outcome(PackTextures.Declaration declaration, int texture, @Nullable String failure, int width,
                int height, boolean linear, boolean clamp) {
            this.declaration = declaration;
            this.texture = texture;
            this.failure = failure;
            this.width = width;
            this.height = height;
            this.linear = linear;
            this.clamp = clamp;
        }

        static Outcome failed(PackTextures.Declaration declaration, String failure) {
            return new Outcome(declaration, 0, failure, 0, 0, false, false);
        }

        public PackTextures.Declaration declaration() {
            return declaration;
        }

        public int unit() {
            return declaration.unit();
        }

        /** The GL texture name, or 0 when it couldn't be made. */
        public int texture() {
            return texture;
        }

        /** Why it couldn't be made, or null when it was. */
        @Nullable
        public String failure() {
            return failure;
        }

        public int width() {
            return width;
        }

        public int height() {
            return height;
        }

        public boolean linear() {
            return linear;
        }

        public boolean clamp() {
            return clamp;
        }
    }

    @Nullable
    private final TextureGl gl;
    private final Outcome[] byUnit;
    private final List<Outcome> outcomes;
    private boolean deleted;

    private ProgramTextures(@Nullable TextureGl gl, Outcome[] byUnit, List<Outcome> outcomes) {
        this.gl = gl;
        this.byUnit = byUnit;
        this.outcomes = outcomes;
    }

    /**
     * Decodes and uploads every gbuffers texture the configuration declares. A configuration without any makes no GL
     * call. Images that can't be read or are too big for the driver become failures. Anything else that throws
     * deletes what was made so far and is rethrown.
     */
    public static ProgramTextures create(PackConfiguration configuration) {
        return create(configuration, LwjglTextureGl.INSTANCE);
    }

    static ProgramTextures create(PackConfiguration configuration, TextureGl gl) {
        Objects.requireNonNull(configuration, "configuration");
        Objects.requireNonNull(gl, "gl");
        List<PackTextures.Declaration> declared = new ArrayList<>();
        for (int unit = 0; unit < UNITS; unit++) {
            PackTextures.Declaration declaration = configuration.textures().forGbuffersUnit(unit);
            if (declaration != null) {
                declared.add(declaration);
            }
        }
        if (declared.isEmpty()) {
            return NONE;
        }
        Outcome[] byUnit = new Outcome[UNITS];
        List<Outcome> outcomes = new ArrayList<>();
        List<Integer> made = new ArrayList<>();
        try {
            int maxUnits = gl.maxTextureUnits();
            int maxSize = gl.maxTextureSize();
            for (PackTextures.Declaration declaration : declared) {
                Outcome outcome = make(configuration, declaration, gl, maxUnits, maxSize, made);
                byUnit[declaration.unit()] = outcome;
                outcomes.add(outcome);
            }
        } catch (RuntimeException | LinkageError e) {
            deleteNewestFirst(gl, made, e);
            throw e;
        }
        return new ProgramTextures(gl, byUnit, Collections.unmodifiableList(outcomes));
    }

    private static Outcome make(PackConfiguration configuration, PackTextures.Declaration declaration, TextureGl gl,
            int maxUnits, int maxSize, List<Integer> made) {
        if (declaration.status() != PackTextures.Status.USABLE) {
            return Outcome.failed(declaration, declaration.reason());
        }
        if (declaration.unit() >= maxUnits) {
            return Outcome.failed(declaration, "it needs texture unit " + declaration.unit()
                    + ", and the driver only has " + maxUnits);
        }
        PackImage image;
        try {
            image = PackImage.read(configuration.pack(), Objects.requireNonNull(declaration.file()));
        } catch (IOException e) {
            return Outcome.failed(declaration, e.getMessage());
        }
        if (image.width() > maxSize || image.height() > maxSize) {
            return Outcome.failed(declaration, declaration.file() + " is " + image.width() + "x" + image.height()
                    + ", and the driver allows at most " + maxSize + "x" + maxSize);
        }
        byte[] pixels = image.pixels();
        ByteBuffer buffer = ByteBuffer.allocateDirect(pixels.length);
        buffer.put(pixels).flip();
        int texture = gl.createTexture();
        // Owned from here, so a failure below still deletes it.
        made.add(texture);
        int previous = gl.boundTexture();
        try {
            gl.bindTexture(texture);
            gl.upload(image.width(), image.height(), buffer, image.blur(), image.clamp());
        } finally {
            gl.bindTexture(previous);
        }
        return new Outcome(declaration, texture, null, image.width(), image.height(), image.blur(), image.clamp());
    }

    @Nullable
    private static Throwable deleteNewestFirst(TextureGl gl, List<Integer> textures, @Nullable Throwable failure) {
        for (int i = textures.size() - 1; i >= 0; i--) {
            try {
                gl.deleteTexture(textures.get(i));
            } catch (RuntimeException | LinkageError e) {
                if (failure == null) {
                    failure = e;
                } else if (failure != e) {
                    failure.addSuppressed(e);
                }
            }
        }
        return failure;
    }

    /** Whether the configuration declares a gbuffers texture on this unit, made or not. */
    public boolean declared(int unit) {
        return unit >= 0 && unit < UNITS && byUnit[unit] != null;
    }

    /** The texture on a unit, or 0 when none is declared there, it failed or the set was deleted. */
    public int texture(int unit) {
        Outcome outcome = unit >= 0 && unit < UNITS ? byUnit[unit] : null;
        return outcome == null || deleted ? 0 : outcome.texture;
    }

    /** Why the texture declared on a unit couldn't be made, or null when it was or none is declared. */
    @Nullable
    public String failure(int unit) {
        Outcome outcome = unit >= 0 && unit < UNITS ? byUnit[unit] : null;
        return outcome == null ? null : outcome.failure;
    }

    /** Every declared unit in unit order, failures included. Read only. */
    public List<Outcome> outcomes() {
        return outcomes;
    }

    public boolean isDeleted() {
        return deleted;
    }

    /**
     * Deletes every texture this set made, each once, newest first. If a delete throws, the rest are still attempted
     * and the first failure is rethrown. Either way the set counts as deleted and calling it again does nothing.
     */
    public void delete() {
        // NONE is shared and owns nothing.
        if (deleted || gl == null) {
            return;
        }
        deleted = true;
        List<Integer> made = new ArrayList<>();
        for (Outcome outcome : outcomes) {
            if (outcome.texture != 0) {
                made.add(outcome.texture);
            }
        }
        Throwable failure = deleteNewestFirst(gl, made, null);
        if (failure instanceof RuntimeException) {
            throw (RuntimeException) failure;
        }
        if (failure instanceof LinkageError) {
            throw (LinkageError) failure;
        }
    }
}
