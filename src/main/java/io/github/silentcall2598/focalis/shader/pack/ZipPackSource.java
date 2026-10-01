// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.shader.pack;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

final class ZipPackSource implements ShaderPackSource {

    private static final String SHADERS_PREFIX = "shaders/";

    private final String name;
    private final ZipFile zip;
    private final Map<ShaderPath, ZipEntry> files;
    private final Set<String> folders;

    private ZipPackSource(String name, ZipFile zip, Map<ShaderPath, ZipEntry> files, Set<String> folders) {
        this.name = name;
        this.zip = zip;
        this.files = files;
        this.folders = folders;
    }

    static ZipPackSource open(Path zipFile) throws ShaderPackException, IOException {
        String name = zipFile.getFileName().toString();
        ZipFile zip;
        try {
            zip = new ZipFile(zipFile.toFile());
        } catch (ZipException e) {
            throw new ShaderPackException("'" + name + "' is not a readable ZIP file", e);
        }
        try {
            Set<String> folders = new TreeSet<>();
            Map<ShaderPath, ZipEntry> files = index(name, zip, folders);
            return new ZipPackSource(name, zip, files, Collections.unmodifiableSet(folders));
        } catch (ShaderPackException e) {
            zip.close();
            throw e;
        } catch (RuntimeException e) {
            // ZipFile reports badly encoded entry names this way.
            zip.close();
            throw new ShaderPackException("'" + name + "' is not a readable ZIP file: " + e.getMessage(), e);
        }
    }

    // Every entry is checked, even outside shaders/, because a ZIP containing path tricks isn't worth trusting. A
    // folder is there when the ZIP has an entry for it or for anything inside it.
    private static Map<ShaderPath, ZipEntry> index(String name, ZipFile zip, Set<String> folders)
            throws ShaderPackException {
        Map<ShaderPath, ZipEntry> files = new TreeMap<>();
        String nestedShadersFolder = null;
        for (Enumeration<? extends ZipEntry> entries = zip.entries(); entries.hasMoreElements(); ) {
            ZipEntry entry = entries.nextElement();
            String path;
            try {
                path = String.join("/", ShaderPath.normalize(entry.getName()));
            } catch (IllegalArgumentException e) {
                throw new ShaderPackException("'" + name + "' contains an unsafe entry: " + e.getMessage());
            }
            // ZipEntry only knows folder entries ending in a slash, but Windows tools sometimes write backslashes.
            boolean folder = entry.isDirectory() || entry.getName().endsWith("\\");
            if (path.startsWith(SHADERS_PREFIX)) {
                String inShaders = path.substring(SHADERS_PREFIX.length());
                int slash = inShaders.indexOf('/');
                if (slash > 0) {
                    folders.add(inShaders.substring(0, slash));
                } else if (folder) {
                    folders.add(inShaders);
                }
            }
            if (folder || path.isEmpty()) {
                continue;
            }
            if (!path.startsWith(SHADERS_PREFIX)) {
                int shadersAt = path.indexOf("/" + SHADERS_PREFIX);
                if (shadersAt > 0 && nestedShadersFolder == null) {
                    nestedShadersFolder = path.substring(0, shadersAt + SHADERS_PREFIX.length());
                }
                continue;
            }
            ShaderPath inShaders = ShaderPath.of(path.substring(SHADERS_PREFIX.length()));
            if (files.put(inShaders, entry) != null) {
                throw new ShaderPackException("'" + name + "' has more than one entry for shaders/" + inShaders);
            }
        }
        if (files.isEmpty()) {
            throw new ShaderPackException(nestedShadersFolder == null
                    ? "'" + name + "' has no shaders folder"
                    : "'" + name + "' has its shaders folder at " + nestedShadersFolder
                            + " instead of the top of the ZIP");
        }
        return Collections.unmodifiableMap(files);
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
        ZipEntry entry = files.get(path);
        if (entry == null) {
            throw new NoSuchFileException(path.toString());
        }
        try (InputStream input = zip.getInputStream(entry)) {
            return PackFileReader.readText(input, path.toString());
        }
    }

    @Override
    public void close() throws IOException {
        zip.close();
    }
}
