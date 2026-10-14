// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.shader.pack;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The custom textures one configuration declares with {@code texture.<stage>.<name>} and {@code texture.noise}. This
 * is only what the pack asks for. Reading and uploading the images happens elsewhere, so nothing here touches files
 * or GL. Immutable.
 *
 * <p>Only shaders-folder PNGs on the documented gbuffers units and the noise texture can be used for now. Everything
 * else is kept with its source line and a reason, so it's reported instead of quietly binding nothing.
 */
public final class PackTextures {

    public static final PackTextures NONE = new PackTextures(Collections.<Declaration>emptyList());

    static final String TEXTURE = "texture.";
    private static final String CUSTOM_TEXTURE = "customTexture.";
    private static final String NOISE = "noise";
    private static final String NOISE_SAMPLER = "noisetex";
    // Documented noisetex unit, shaders.txt gbuffers table.
    private static final int NOISE_UNIT = 15;
    private static final Pattern KEY = Pattern.compile("texture\\.([A-Za-z]+)\\.([A-Za-z]\\w*?)(?:\\.([0-9]))?");
    private static final String MINECRAFT = "minecraft:";

    // The gbuffers units custom textures may use, 1.12-era names and their colortex aliases.
    private static final Map<String, Integer> GBUFFERS_UNITS = new LinkedHashMap<>();

    static {
        GBUFFERS_UNITS.put("gaux1", 7);
        GBUFFERS_UNITS.put("gaux2", 8);
        GBUFFERS_UNITS.put("gaux3", 9);
        GBUFFERS_UNITS.put("gaux4", 10);
        GBUFFERS_UNITS.put("colortex4", 7);
        GBUFFERS_UNITS.put("colortex5", 8);
        GBUFFERS_UNITS.put("colortex6", 9);
        GBUFFERS_UNITS.put("colortex7", 10);
    }

    /** Which programs a declaration is for. */
    public enum Stage {
        /** The noise texture, which every program can sample. */
        NOISE,
        /** gbuffers and shadow programs. */
        GBUFFERS,
        DEFERRED,
        /** composite and final programs. */
        COMPOSITE
    }

    /** Whether Focalis can provide a declaration yet. */
    public enum Status {
        /** A shaders-folder PNG on a unit Focalis binds. */
        USABLE,
        /** The image doesn't exist in the pack. Programs that sample it can't get it. */
        MISSING,
        /** Valid, but it needs something Focalis doesn't have yet, like deferred programs. */
        NEEDS_LATER_CAPABILITY,
        /**
         * A source form or unit Focalis doesn't support. On a unit Focalis binds, programs that sample it can't get it,
         * the same as a missing image.
         */
        UNSUPPORTED,
        /** A later line declares the same unit, so this one is ignored. */
        REPLACED
    }

    /** One declared texture. */
    public static final class Declaration {

        private final String key;
        private final Stage stage;
        private final String sampler;
        private final int unit;
        private final String value;
        @Nullable
        private final ShaderPath file;
        private final Status status;
        private final String reason;
        private final SourceLocation location;

        Declaration(String key, Stage stage, String sampler, int unit, String value, @Nullable ShaderPath file,
                Status status, String reason, SourceLocation location) {
            this.key = key;
            this.stage = stage;
            this.sampler = sampler;
            this.unit = unit;
            this.value = value;
            this.file = file;
            this.status = status;
            this.reason = reason;
            this.location = location;
        }

        public String key() {
            return key;
        }

        public Stage stage() {
            return stage;
        }

        /** The sampler or unit name as written, like {@code gaux4}, or {@code noisetex} for the noise texture. */
        public String sampler() {
            return sampler;
        }

        /** The documented texture unit, or -1 when Focalis doesn't bind this stage yet. */
        public int unit() {
            return unit;
        }

        /** The value as written. */
        public String value() {
            return value;
        }

        /** The image in the shaders folder, or null for other source forms. */
        @Nullable
        public ShaderPath file() {
            return file;
        }

        public Status status() {
            return status;
        }

        /** Why it isn't usable, empty when it is. */
        public String reason() {
            return reason;
        }

        public SourceLocation location() {
            return location;
        }

        @Override
        public String toString() {
            return key + "=" + value + " (" + status + (reason.isEmpty() ? "" : ", " + reason) + ")";
        }
    }

    private final List<Declaration> declarations;

    private PackTextures(List<Declaration> declarations) {
        this.declarations = declarations;
    }

    /**
     * Reads the declarations from properties that already went through the preprocessor. Anything that can't be
     * used is reported in {@code issues}.
     */
    static PackTextures read(ShaderPath file, Map<String, ShaderProperties.Property> properties, ShaderPack pack,
            List<PackIssue> issues) {
        List<Declaration> declarations = new ArrayList<>();
        for (Map.Entry<String, ShaderProperties.Property> entry : properties.entrySet()) {
            String key = entry.getKey();
            ShaderProperties.Property property = entry.getValue();
            SourceLocation here = new SourceLocation(file, property.line);
            if (key.startsWith(CUSTOM_TEXTURE)) {
                issues.add(new PackIssue(here, key + " isn't supported, customTexture isn't part of the documented"
                        + " format"));
                continue;
            }
            if (!key.startsWith(TEXTURE)) {
                continue;
            }
            Declaration declaration = declaration(key, property.value, here, pack, issues);
            if (declaration == null) {
                continue;
            }
            declarations.add(declaration);
            if (declaration.status != Status.USABLE) {
                issues.add(new PackIssue(here, key + " can't be used, " + declaration.reason));
            }
        }
        conflicts(declarations, issues);
        return declarations.isEmpty() ? NONE : new PackTextures(Collections.unmodifiableList(declarations));
    }

    @Nullable
    private static Declaration declaration(String key, String value, SourceLocation here, ShaderPack pack,
            List<PackIssue> issues) {
        Stage stage;
        String sampler;
        int unit;
        if (key.equals(TEXTURE + NOISE)) {
            stage = Stage.NOISE;
            sampler = NOISE_SAMPLER;
            unit = NOISE_UNIT;
        } else {
            Matcher matcher = KEY.matcher(key);
            if (!matcher.matches()) {
                issues.add(new PackIssue(here, key + " is ignored, custom textures are texture.<stage>.<name>"));
                return null;
            }
            String stageName = matcher.group(1);
            sampler = matcher.group(2);
            if (stageName.equals("gbuffers")) {
                stage = Stage.GBUFFERS;
                Integer documented = GBUFFERS_UNITS.get(sampler);
                if (documented == null) {
                    return new Declaration(key, stage, sampler, -1, value, null, Status.UNSUPPORTED,
                            sampler + " isn't a gbuffers custom texture unit Focalis supports", here);
                }
                unit = documented;
            } else if (stageName.equals("deferred") || stageName.equals("composite")) {
                stage = stageName.equals("deferred") ? Stage.DEFERRED : Stage.COMPOSITE;
                return new Declaration(key, stage, sampler, -1, value, packFile(value), Status.NEEDS_LATER_CAPABILITY,
                        "Focalis doesn't run " + stageName + " programs yet", here);
            } else {
                issues.add(new PackIssue(here, key + " is ignored, '" + stageName + "' isn't a texture stage"));
                return null;
            }
        }
        if (value.isEmpty()) {
            return new Declaration(key, stage, sampler, unit, value, null, Status.UNSUPPORTED, "it names no image",
                    here);
        }
        if (value.trim().indexOf(' ') >= 0 || value.trim().indexOf('\t') >= 0) {
            return new Declaration(key, stage, sampler, unit, value, null, Status.UNSUPPORTED,
                    "raw texture dumps aren't supported", here);
        }
        if (value.startsWith(MINECRAFT)) {
            return new Declaration(key, stage, sampler, unit, value, null, Status.UNSUPPORTED,
                    "resource pack and dynamic textures aren't supported", here);
        }
        ShaderPath image = packFile(value);
        if (image == null) {
            return new Declaration(key, stage, sampler, unit, value, null, Status.UNSUPPORTED,
                    "'" + value + "' isn't a path inside the shaders folder", here);
        }
        if (!image.fileName().toLowerCase(Locale.ROOT).endsWith(".png")) {
            return new Declaration(key, stage, sampler, unit, value, image, Status.UNSUPPORTED,
                    "only PNG images are supported", here);
        }
        if (!pack.files().contains(image)) {
            return new Declaration(key, stage, sampler, unit, value, image, Status.MISSING,
                    image + " doesn't exist in the pack", here);
        }
        if (!pack.hasImage(image)) {
            return new Declaration(key, stage, sampler, unit, value, image, Status.MISSING,
                    image + " couldn't be read when the pack loaded", here);
        }
        return new Declaration(key, stage, sampler, unit, value, image, Status.USABLE, "", here);
    }

    /**
     * The PNG in the shaders folder a {@code texture.} value names, or null for anything else. The loader uses this
     * to read every image some configuration could bind.
     */
    @Nullable
    static ShaderPath packImage(String value) {
        ShaderPath file = packFile(value);
        return file != null && file.fileName().toLowerCase(Locale.ROOT).endsWith(".png") ? file : null;
    }

    @Nullable
    private static ShaderPath packFile(String value) {
        String trimmed = value.trim();
        if (trimmed.startsWith(MINECRAFT) || trimmed.isEmpty() || trimmed.indexOf(' ') >= 0
                || trimmed.indexOf('\t') >= 0) {
            return null;
        }
        try {
            return ShaderPath.of(value.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    // Two 2D images can't share a unit, so the later line wins and the earlier one is reported.
    private static void conflicts(List<Declaration> declarations, List<PackIssue> issues) {
        Map<String, Integer> byUnit = new HashMap<>();
        for (int i = 0; i < declarations.size(); i++) {
            Declaration declaration = declarations.get(i);
            if (declaration.unit < 0) {
                continue;
            }
            String slot = declaration.stage + "/" + declaration.unit;
            Integer earlier = byUnit.put(slot, i);
            if (earlier == null) {
                continue;
            }
            Declaration replaced = declarations.get(earlier);
            declarations.set(earlier, new Declaration(replaced.key, replaced.stage, replaced.sampler, replaced.unit,
                    replaced.value, replaced.file, Status.REPLACED, declaration.key + " uses the same unit",
                    replaced.location));
            issues.add(new PackIssue(replaced.location, replaced.key + " is ignored, " + declaration.key
                    + " uses the same unit"));
        }
    }

    /** Every declaration in file order. Read only. */
    public List<Declaration> all() {
        return declarations;
    }

    /**
     * The declaration a program bound for the gbuffers stage reads on {@code unit}, or null when nothing is
     * declared for it there. It can be one Focalis can't provide, so the programs that sample it don't build.
     */
    @Nullable
    public Declaration forGbuffersUnit(int unit) {
        for (Declaration declaration : declarations) {
            if ((declaration.stage == Stage.GBUFFERS || declaration.stage == Stage.NOISE) && declaration.unit == unit
                    && declaration.status != Status.REPLACED) {
                return declaration;
            }
        }
        return null;
    }

    /** The gbuffers unit a sampler name reads, or -1 when it isn't a custom texture sampler. */
    public static int gbuffersUnit(String sampler) {
        if (sampler.equals(NOISE_SAMPLER)) {
            return NOISE_UNIT;
        }
        Integer unit = GBUFFERS_UNITS.get(sampler);
        return unit == null ? -1 : unit;
    }
}
