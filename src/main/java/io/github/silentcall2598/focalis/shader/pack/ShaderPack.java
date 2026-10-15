// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.shader.pack;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A loaded shaderpack as plain data. It owns no GL objects and never touches the pack files again, so it's
 * safe to keep around, inspect and share.
 */
public final class ShaderPack {

    private final String name;
    private final Set<ShaderPath> files;
    private final Map<ShaderPath, String> texts;
    private final Map<ShaderPath, byte[]> images;
    private final ProgramDirectory root;
    private final Map<String, ProgramDirectory> dimensionDirectories;
    private final DimensionProperties dimensionProperties;
    private final ShaderOptions options;
    private final List<PackIssue> issues;

    ShaderPack(String name, Set<ShaderPath> files, Map<ShaderPath, String> texts, Map<ShaderPath, byte[]> images,
            ProgramDirectory root, Map<String, ProgramDirectory> dimensionDirectories,
            DimensionProperties dimensionProperties, ShaderOptions options, List<PackIssue> issues) {
        this.name = name;
        this.files = files;
        this.texts = texts;
        this.images = images;
        this.root = root;
        this.dimensionDirectories = dimensionDirectories;
        this.dimensionProperties = dimensionProperties;
        this.options = options;
        this.issues = issues;
    }

    public String name() {
        return name;
    }

    /** Every file under {@code shaders}, including ones Focalis never reads. */
    public Set<ShaderPath> files() {
        return files;
    }

    /** Programs directly in the shaders folder. */
    public ProgramDirectory root() {
        return root;
    }

    /**
     * Dimension folders keyed by folder name such as {@code world-1}. A {@code world<id>} folder is included even
     * without programs, since its presence still decides what its dimension uses.
     */
    public Map<String, ProgramDirectory> dimensionDirectories() {
        return dimensionDirectories;
    }

    /**
     * The folder a dimension draws with. A {@code world<id>} folder named exactly after the dimension replaces the
     * shaders folder completely, even when its programs are missing or broken. Without one it's the shaders folder.
     */
    public ProgramDirectory programDirectoryFor(int dimension) {
        ProgramDirectory override = dimensionDirectories.get(ProgramDirectory.worldFolder(dimension));
        return override != null ? override : root;
    }

    public DimensionProperties dimensionProperties() {
        return dimensionProperties;
    }

    /** The options the pack's .vsh and .fsh sources define, including the files they include. */
    public ShaderOptions options() {
        return options;
    }

    /** Problems that affected part of the pack, such as a program with a broken include. */
    public List<PackIssue> issues() {
        return issues;
    }

    /** The text of a file the loader read, or null for files it skipped or that don't exist. */
    @Nullable
    public String text(ShaderPath file) {
        return texts.get(file);
    }

    /** Whether the loader kept the bytes of an image some configuration could bind. */
    public boolean hasImage(ShaderPath file) {
        return images.containsKey(file);
    }

    /** A copy of the bytes of an image the loader kept, or null for any other file. */
    @Nullable
    public byte[] image(ShaderPath file) {
        byte[] bytes = images.get(file);
        return bytes == null ? null : bytes.clone();
    }

    /** Expands the includes of one stage of a program, with the sources as written. */
    public ResolvedSource resolve(ProgramSource program, ProgramStage stage) throws IncludeException {
        return new IncludeResolver(texts::get).resolve(stageFile(program, stage));
    }

    /** Expands the includes of one stage of a program, with the configuration's option values written in. */
    public ResolvedSource resolve(ProgramSource program, ProgramStage stage, PackConfiguration configuration)
            throws IncludeException {
        if (configuration.pack() != this) {
            throw new IllegalArgumentException("The configuration belongs to shaderpack '"
                    + configuration.pack().name() + "', not '" + name + "'");
        }
        return new IncludeResolver(path -> {
            String text = configuration.text(path);
            return text != null ? text : texts.get(path);
        }).resolve(stageFile(program, stage));
    }

    private static ShaderPath stageFile(ProgramSource program, ProgramStage stage) {
        ShaderPath file = program.file(stage);
        if (file == null) {
            throw new IllegalArgumentException(program.name() + " has no " + stage + " stage");
        }
        return file;
    }
}
