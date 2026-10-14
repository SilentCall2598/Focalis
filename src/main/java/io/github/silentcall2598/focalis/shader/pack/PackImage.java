// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.shader.pack;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

import javax.annotation.Nullable;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.MemoryCacheImageInputStream;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.ComponentColorModel;
import java.awt.image.Raster;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Iterator;

/**
 * A custom texture decoded from a pack PNG, with the blur and clamp settings of its {@code .mcmeta}. Pixels are RGBA
 * with one byte each, rows from the top of the image down, the order Minecraft uploads its own textures in. Plain
 * data, nothing here touches GL.
 */
public final class PackImage {

    /** Bigger images are refused before decoding, since packs are untrusted input. */
    public static final int MAX_SIZE = 8192;

    private static final byte[] PNG_SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};

    private final int width;
    private final int height;
    private final byte[] pixels;
    private final boolean blur;
    private final boolean clamp;

    private PackImage(int width, int height, byte[] pixels, boolean blur, boolean clamp) {
        this.width = width;
        this.height = height;
        this.pixels = pixels;
        this.blur = blur;
        this.clamp = clamp;
    }

    /** Decodes an image the loader kept, with the {@code .mcmeta} next to it if there is one. */
    public static PackImage read(ShaderPack pack, ShaderPath file) throws IOException {
        byte[] bytes = pack.image(file);
        if (bytes == null) {
            throw new IOException(file + " wasn't read when the pack loaded");
        }
        return decode(file, bytes, pack.text(ShaderPath.of(file + ".mcmeta")));
    }

    static PackImage decode(ShaderPath file, byte[] png, @Nullable String mcmeta) throws IOException {
        boolean[] settings = settings(file, mcmeta);
        if (!isPng(png)) {
            throw new IOException(file + " isn't a PNG file");
        }
        Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName("png");
        if (!readers.hasNext()) {
            throw new IOException("No PNG decoder is available for " + file);
        }
        ImageReader reader = readers.next();
        // A memory cache, so ImageIO never writes temporary files for this.
        try (ImageInputStream input = new MemoryCacheImageInputStream(new ByteArrayInputStream(png))) {
            reader.setInput(input, true, true);
            int width = reader.getWidth(0);
            int height = reader.getHeight(0);
            if (width < 1 || height < 1 || width > MAX_SIZE || height > MAX_SIZE) {
                throw new IOException(file + " is " + width + "x" + height + ", and images can be at most " + MAX_SIZE
                        + "x" + MAX_SIZE);
            }
            BufferedImage image = reader.read(0);
            return new PackImage(width, height, rgba(image), settings[0], settings[1]);
        } catch (RuntimeException e) {
            // Some damaged files make the decoder throw unchecked exceptions.
            throw new IOException(file + " can't be decoded: " + e, e);
        } catch (IOException e) {
            throw new IOException(file + " can't be decoded: " + e.getMessage(), e);
        } finally {
            reader.dispose();
        }
    }

    private static boolean isPng(byte[] bytes) {
        if (bytes.length < PNG_SIGNATURE.length) {
            return false;
        }
        for (int i = 0; i < PNG_SIGNATURE.length; i++) {
            if (bytes[i] != PNG_SIGNATURE[i]) {
                return false;
            }
        }
        return true;
    }

    // Read like Minecraft 1.12 reads its own texture metadata. A missing section or key is false, any JSON primitive
    // counts as a boolean the way Gson reads it, and anything else is an error.
    private static boolean[] settings(ShaderPath file, @Nullable String mcmeta) throws IOException {
        boolean[] settings = new boolean[2];
        if (mcmeta == null) {
            return settings;
        }
        try {
            JsonElement root = new Gson().fromJson(mcmeta, JsonElement.class);
            if (root == null || !root.isJsonObject()) {
                throw new IOException(file + ".mcmeta isn't a JSON object");
            }
            JsonElement section = root.getAsJsonObject().get("texture");
            if (section == null) {
                return settings;
            }
            if (!section.isJsonObject()) {
                throw new IOException(file + ".mcmeta has a texture section that isn't an object");
            }
            settings[0] = flag(file, section.getAsJsonObject(), "blur");
            settings[1] = flag(file, section.getAsJsonObject(), "clamp");
            return settings;
        } catch (JsonParseException | IllegalStateException | UnsupportedOperationException e) {
            throw new IOException(file + ".mcmeta isn't valid: " + e.getMessage(), e);
        }
    }

    private static boolean flag(ShaderPath file, JsonObject section, String name) throws IOException {
        JsonElement value = section.get(name);
        if (value == null) {
            return false;
        }
        if (!value.isJsonPrimitive()) {
            throw new IOException(file + ".mcmeta has a " + name + " that isn't a boolean");
        }
        return value.getAsBoolean();
    }

    // The samples of color model images are used as they are, scaled to 8 bits. Converting them through getRGB would
    // pass gray images through a color space conversion and change their values. Palette and packed images are
    // already sRGB values, so getRGB is exact for them.
    private static byte[] rgba(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        byte[] pixels = new byte[width * height * 4];
        ColorModel model = image.getColorModel();
        if (model instanceof ComponentColorModel) {
            Raster raster = image.getRaster();
            int bands = raster.getNumBands();
            int[] maximum = new int[bands];
            for (int band = 0; band < bands; band++) {
                maximum[band] = (1 << model.getComponentSize(band)) - 1;
            }
            int[] row = new int[width * bands];
            for (int y = 0; y < height; y++) {
                raster.getPixels(0, y, width, 1, row);
                for (int x = 0; x < width; x++) {
                    int at = (y * width + x) * 4;
                    int sample = x * bands;
                    int first = scale(row[sample], maximum[0]);
                    if (bands < 3) {
                        pixels[at] = (byte) first;
                        pixels[at + 1] = (byte) first;
                        pixels[at + 2] = (byte) first;
                        pixels[at + 3] = (byte) (bands == 2 ? scale(row[sample + 1], maximum[1]) : 255);
                    } else {
                        pixels[at] = (byte) first;
                        pixels[at + 1] = (byte) scale(row[sample + 1], maximum[1]);
                        pixels[at + 2] = (byte) scale(row[sample + 2], maximum[2]);
                        pixels[at + 3] = (byte) (bands > 3 ? scale(row[sample + 3], maximum[3]) : 255);
                    }
                }
            }
            return pixels;
        }
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int argb = image.getRGB(x, y);
                int at = (y * width + x) * 4;
                pixels[at] = (byte) (argb >> 16);
                pixels[at + 1] = (byte) (argb >> 8);
                pixels[at + 2] = (byte) argb;
                pixels[at + 3] = (byte) (argb >>> 24);
            }
        }
        return pixels;
    }

    private static int scale(int sample, int maximum) {
        return maximum == 255 ? sample : (sample * 255 + maximum / 2) / maximum;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    /** A copy of the RGBA pixels, rows from the top down. */
    public byte[] pixels() {
        return pixels.clone();
    }

    /** Linear filtering instead of nearest, from the {@code .mcmeta}. */
    public boolean blur() {
        return blur;
    }

    /** Clamping to the edge instead of repeating, from the {@code .mcmeta}. */
    public boolean clamp() {
        return clamp;
    }
}
