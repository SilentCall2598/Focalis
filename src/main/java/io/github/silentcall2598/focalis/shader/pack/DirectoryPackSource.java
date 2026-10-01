// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.shader.pack;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Stream;

final class DirectoryPackSource implements ShaderPackSource {

    private final String name;
    private final Map<ShaderPath, Path> files;
    private final Set<String> folders;

    private DirectoryPackSource(String name, Map<ShaderPath, Path> files, Set<String> folders) {
        this.name = name;
        this.files = files;
        this.folders = folders;
    }

    static DirectoryPackSource open(Path packDirectory) throws ShaderPackException, IOException {
        String name = packDirectory.getFileName().toString();
        Path packRoot = packDirectory.toRealPath();
        Path shaders = packRoot.resolve("shaders");
        if (!Files.isDirectory(shaders)) {
            throw new ShaderPackException("'" + name + "' has no shaders folder");
        }
        Path shadersRoot = shaders.toRealPath();
        if (!shadersRoot.startsWith(packRoot)) {
            throw new ShaderPackException("The shaders folder of '" + name + "' links outside the pack");
        }

        // Any link pointing outside the shaders folder is rejected. Lookups go through this index, which keeps
        // name matching case-sensitive like it is in ZIP packs. The walk doesn't go into linked folders, so a linked
        // folder counts as missing just like the files in it.
        Map<ShaderPath, Path> files = new TreeMap<>();
        Set<String> folders = new TreeSet<>();
        try (Stream<Path> walk = Files.walk(shadersRoot)) {
            for (Iterator<Path> it = walk.iterator(); it.hasNext(); ) {
                Path file = it.next();
                String relative = shadersRoot.relativize(file).toString();
                if (Files.isSymbolicLink(file) && !file.toRealPath().startsWith(shadersRoot)) {
                    throw new ShaderPackException("'" + relative + "' in '" + name
                            + "' links outside the shaders folder");
                }
                if (Files.isRegularFile(file)) {
                    try {
                        files.put(ShaderPath.of(relative), file);
                    } catch (IllegalArgumentException e) {
                        throw new ShaderPackException("'" + name + "' has an unsupported file name: "
                                + e.getMessage());
                    }
                } else if (Files.isDirectory(file, LinkOption.NOFOLLOW_LINKS) && shadersRoot.equals(file.getParent())) {
                    folders.add(file.getFileName().toString());
                }
            }
        } catch (UncheckedIOException e) {
            throw e.getCause();
        }
        return new DirectoryPackSource(name, Collections.unmodifiableMap(files), Collections.unmodifiableSet(folders));
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public Set<ShaderPath> files() {
        return files.keySet();
    }

    @Override
    public Set<String> folders() {
        return folders;
    }

    @Override
    public String readText(ShaderPath path) throws IOException {
        Path file = files.get(path);
        if (file == null) {
            throw new NoSuchFileException(path.toString());
        }
        try (InputStream input = Files.newInputStream(file)) {
            return PackFileReader.readText(input, path.toString());
        }
    }

    @Override
    public void close() {
    }
}
