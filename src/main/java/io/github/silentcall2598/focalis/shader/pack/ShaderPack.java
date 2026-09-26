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
    private final ProgramDirectory root;
    private final Map<String, ProgramDirectory> dimensionDirectories;
    private final DimensionProperties dimensionProperties;
    private final Set<ShaderPath> uninterpretedMetadata;
    private final List<PackIssue> issues;

    ShaderPack(String name, Set<ShaderPath> files, Map<ShaderPath, String> texts, ProgramDirectory root,
            Map<String, ProgramDirectory> dimensionDirectories, DimensionProperties dimensionProperties,
            Set<ShaderPath> uninterpretedMetadata, List<PackIssue> issues) {
        this.name = name;
        this.files = files;
        this.texts = texts;
        this.root = root;
        this.dimensionDirectories = dimensionDirectories;
        this.dimensionProperties = dimensionProperties;
        this.uninterpretedMetadata = uninterpretedMetadata;
        this.issues = issues;
    }

    public String name() {
        return name;
    }

    /** Every file under {@code shaders}, including textures and anything else Focalis doesn't read. */
    public Set<ShaderPath> files() {
        return files;
    }

    /** Programs directly in the shaders folder. */
    public ProgramDirectory root() {
        return root;
    }

    /** Dimension folders with programs in them, keyed by folder name such as {@code world-1}. */
    public Map<String, ProgramDirectory> dimensionDirectories() {
        return dimensionDirectories;
    }

    public DimensionProperties dimensionProperties() {
        return dimensionProperties;
    }

    /**
     * Metadata files the pack has but Focalis doesn't interpret yet, such as {@code shaders.properties}. Their
     * text is kept and available from {@link #text}.
     */
    public Set<ShaderPath> uninterpretedMetadata() {
        return uninterpretedMetadata;
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

    /** Expands the includes of one stage of a program. */
    public ResolvedSource resolve(ProgramSource program, ProgramStage stage) throws IncludeException {
        ShaderPath file = program.file(stage);
        if (file == null) {
            throw new IllegalArgumentException(program.name() + " has no " + stage + " stage");
        }
        return new IncludeResolver(texts::get).resolve(file);
    }
}
