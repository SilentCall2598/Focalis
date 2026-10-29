// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.shader.pack;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    public Set<ShaderPath> files() {
        return files;
    }

    public ProgramDirectory root() {
        return root;
    }

    public Map<String, ProgramDirectory> dimensionDirectories() {
        return dimensionDirectories;
    }

    public ProgramDirectory programDirectoryFor(int dimension) {
        ProgramDirectory override = dimensionDirectories.get(ProgramDirectory.worldFolder(dimension));
        return override != null ? override : root;
    }

    public DimensionProperties dimensionProperties() {
        return dimensionProperties;
    }

    public ShaderOptions options() {
        return options;
    }

    public List<PackIssue> issues() {
        return issues;
    }

    @Nullable
    public String text(ShaderPath file) {
        return texts.get(file);
    }

    public Set<String> mentions(String prefix) {
        Pattern identifier = Pattern.compile("\\b" + Pattern.quote(prefix) + "\\w*");
        Set<String> found = new TreeSet<>();
        for (String text : texts.values()) {
            Matcher matcher = identifier.matcher(text);
            while (matcher.find()) {
                found.add(matcher.group());
            }
        }
        return found;
    }

    public boolean hasImage(ShaderPath file) {
        return images.containsKey(file);
    }

    @Nullable
    public byte[] image(ShaderPath file) {
        byte[] bytes = images.get(file);
        return bytes == null ? null : bytes.clone();
    }

    public ResolvedSource resolve(ProgramSource program, ProgramStage stage) throws IncludeException {
        return new IncludeResolver(texts::get).resolve(stageFile(program, stage));
    }

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
