// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.shader.pack;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Turns a shaderpack folder or ZIP into a {@link ShaderPack}. Problems that make the whole pack unusable throw
 * {@link ShaderPackException}. Problems limited to one program are recorded as {@link PackIssue}s instead.
 */
public final class ShaderPackLoader {

    // Kept as text for later work. Nothing in them is interpreted yet.
    private static final List<String> UNINTERPRETED_METADATA = Arrays.asList(
            "block.properties", "item.properties", "entity.properties");

    private ShaderPackLoader() {
    }

    public static ShaderPack load(Path pack) throws ShaderPackException {
        try (ShaderPackSource source = ShaderPackSource.open(pack)) {
            return load(source);
        } catch (IOException e) {
            throw new ShaderPackException("Could not read shaderpack '" + pack.getFileName() + "': " + e.getMessage(),
                    e);
        }
    }

    static ShaderPack load(ShaderPackSource source) throws ShaderPackException, IOException {
        List<PackIssue> issues = new ArrayList<>();
        Map<ShaderPath, String> texts = new HashMap<>();
        // Only files that are actually needed get read, so textures and unused files stay on disk.
        IncludeResolver.FileText reader = path -> {
            if (!source.files().contains(path)) {
                return null;
            }
            String text = texts.get(path);
            if (text == null) {
                text = source.readText(path);
                texts.put(path, text);
            }
            return text;
        };

        DimensionProperties dimensions = DimensionProperties.NONE;
        ShaderPath dimensionFile = ShaderPath.of(DimensionProperties.FILE_NAME);
        if (source.files().contains(dimensionFile)) {
            dimensions = DimensionProperties.parse(dimensionFile, reader.read(dimensionFile), issues);
        }

        Map<String, Map<String, Map<ProgramStage, ShaderPath>>> found = discoverPrograms(source.files(), dimensions);
        if (found.isEmpty()) {
            throw new ShaderPackException("'" + source.name()
                    + "' has no shader programs (.vsh, .fsh, .gsh or .csh files)");
        }

        IncludeResolver resolver = new IncludeResolver(reader);
        OptionScanner options = new OptionScanner(reader);
        ProgramDirectory root = new ProgramDirectory("", Collections.<String, ProgramSource>emptyMap());
        Map<String, ProgramDirectory> dimensionDirectories = new TreeMap<>();
        for (Map.Entry<String, Map<String, Map<ProgramStage, ShaderPath>>> folder : found.entrySet()) {
            Map<String, ProgramSource> programs = new TreeMap<>();
            for (Map.Entry<String, Map<ProgramStage, ShaderPath>> program : folder.getValue().entrySet()) {
                String name = program.getKey();
                String label = folder.getKey().isEmpty() ? name : folder.getKey() + "/" + name;
                reportMissingStages(label, program.getValue(), issues);
                String problem = checkIncludes(label, program.getValue(), resolver, options, issues);
                programs.put(name, new ProgramSource(name, folder.getKey(), program.getValue(), problem));
            }
            ProgramDirectory directory = new ProgramDirectory(folder.getKey(), programs);
            if (folder.getKey().isEmpty()) {
                root = directory;
            } else {
                dimensionDirectories.put(folder.getKey(), directory);
            }
        }

        // A world folder without programs still turns shaders off for its dimension, so it's kept.
        for (String folder : source.folders()) {
            Integer worldId = ProgramDirectory.worldId(folder);
            if (worldId != null && folder.equals(ProgramDirectory.worldFolder(worldId))
                    && !dimensionDirectories.containsKey(folder)) {
                dimensionDirectories.put(folder,
                        new ProgramDirectory(folder, Collections.<String, ProgramSource>emptyMap()));
            }
        }

        for (String folder : dimensions.dimensionsByFolder().keySet()) {
            ProgramDirectory listed = dimensionDirectories.get(folder);
            if (listed == null || listed.programs().isEmpty()) {
                issues.add(new PackIssue(null, DimensionProperties.FILE_NAME + " lists folder " + folder
                        + ", which has no programs"));
            }
        }

        // Read now so configurations can use it later without touching the pack again.
        reader.read(ShaderPath.of(ShaderProperties.FILE_NAME));
        Set<ShaderPath> uninterpreted = new TreeSet<>();
        for (String name : UNINTERPRETED_METADATA) {
            ShaderPath path = ShaderPath.of(name);
            if (reader.read(path) != null) {
                uninterpreted.add(path);
            }
        }

        return new ShaderPack(source.name(), Collections.unmodifiableSet(new TreeSet<>(source.files())),
                Collections.unmodifiableMap(new HashMap<>(texts)), root,
                Collections.unmodifiableMap(dimensionDirectories), dimensions, options.finish(issues),
                Collections.unmodifiableSet(uninterpreted), Collections.unmodifiableList(issues));
    }

    // Programs live directly in the shaders folder or in a dimension folder, never deeper.
    private static Map<String, Map<String, Map<ProgramStage, ShaderPath>>> discoverPrograms(Set<ShaderPath> files,
            DimensionProperties dimensions) {
        Map<String, Map<String, Map<ProgramStage, ShaderPath>>> found = new TreeMap<>();
        for (ShaderPath file : files) {
            ProgramStage stage = ProgramStage.forFileName(file.fileName());
            String folder = file.directory();
            boolean programFolder = folder.isEmpty() || ProgramDirectory.worldId(folder) != null
                    || dimensions.dimensionsByFolder().containsKey(folder);
            if (stage == null || !programFolder) {
                continue;
            }
            String fileName = file.fileName();
            String programName = fileName.substring(0, fileName.length() - stage.extension().length() - 1);
            found.computeIfAbsent(folder, key -> new TreeMap<>())
                    .computeIfAbsent(programName, key -> new EnumMap<>(ProgramStage.class))
                    .put(stage, file);
        }
        return found;
    }

    // Nothing gets a default vertex or fragment shader later, so a half-written program is worth pointing out.
    private static void reportMissingStages(String label, Map<ProgramStage, ShaderPath> stages,
            List<PackIssue> issues) {
        boolean vertex = stages.containsKey(ProgramStage.VERTEX);
        boolean fragment = stages.containsKey(ProgramStage.FRAGMENT);
        boolean rendering = vertex || fragment || stages.containsKey(ProgramStage.GEOMETRY);
        if (rendering && !(vertex && fragment)) {
            issues.add(new PackIssue(null, label + " needs both a vertex and a fragment shader but has only "
                    + stages.keySet()));
        }
    }

    // Options only come from .vsh and .fsh sources, like the format documents.
    @Nullable
    private static String checkIncludes(String label, Map<ProgramStage, ShaderPath> stages, IncludeResolver resolver,
            OptionScanner options, List<PackIssue> issues) throws IOException {
        List<ResolvedSource> scanned = new ArrayList<>();
        for (Map.Entry<ProgramStage, ShaderPath> stage : stages.entrySet()) {
            try {
                ResolvedSource source = resolver.resolve(stage.getValue());
                if (stage.getKey() == ProgramStage.VERTEX || stage.getKey() == ProgramStage.FRAGMENT) {
                    scanned.add(source);
                }
            } catch (IncludeException e) {
                issues.add(new PackIssue(e.location(), label + " can't be used: " + e.reason()));
                return e.getMessage();
            }
        }
        for (ResolvedSource source : scanned) {
            options.addProgram(source);
        }
        return null;
    }
}
