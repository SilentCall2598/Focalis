// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.shader.session;

import io.github.silentcall2598.focalis.render.lifecycle.RenderStage;
import io.github.silentcall2598.focalis.render.target.MainFramebufferRedirect;
import io.github.silentcall2598.focalis.shader.material.MaterialIds;
import io.github.silentcall2598.focalis.shader.pack.MaterialMappings;
import io.github.silentcall2598.focalis.shader.program.EnvironmentInputs;
import net.minecraft.block.material.Material;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.ActiveRenderInfo;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.init.MobEffects;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.LoaderState;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GLContext;

import javax.annotation.Nullable;
import java.nio.FloatBuffer;
import java.nio.file.Path;

public final class MinecraftClientView implements ClientView {

    private final FloatBuffer matrixBuffer = BufferUtils.createFloatBuffer(16);

    @Override
    public Path shaderpacks() {
        return Minecraft.getMinecraft().gameDir.toPath().resolve("shaderpacks");
    }

    @Nullable
    @Override
    public Object world() {
        return Minecraft.getMinecraft().world;
    }

    @Override
    public int dimension() {
        return Minecraft.getMinecraft().world.provider.getDimension();
    }

    @Nullable
    @Override
    public Object viewEntity() {
        return Minecraft.getMinecraft().getRenderViewEntity();
    }

    @Override
    public void viewPosition(float partialTicks, double[] into) {
        Entity entity = Minecraft.getMinecraft().getRenderViewEntity();
        into[0] = entity.lastTickPosX + (entity.posX - entity.lastTickPosX) * partialTicks;
        into[1] = entity.lastTickPosY + (entity.posY - entity.lastTickPosY) * partialTicks;
        into[2] = entity.lastTickPosZ + (entity.posZ - entity.lastTickPosZ) * partialTicks;
    }

    @Override
    public int perspective() {
        return Minecraft.getMinecraft().gameSettings.thirdPersonView;
    }

    @Override
    public int displayWidth() {
        return Minecraft.getMinecraft().displayWidth;
    }

    @Override
    public int displayHeight() {
        return Minecraft.getMinecraft().displayHeight;
    }

    @Override
    public Framebuffer framebuffer() {
        return Minecraft.getMinecraft().getFramebuffer();
    }

    @Override
    public int framebufferWidth() {
        return Minecraft.getMinecraft().getFramebuffer().framebufferWidth;
    }

    @Override
    public int framebufferHeight() {
        return Minecraft.getMinecraft().getFramebuffer().framebufferHeight;
    }

    @Override
    public boolean framebuffersEnabled() {
        return OpenGlHelper.isFramebufferEnabled();
    }

    @Override
    public boolean anaglyph() {
        return Minecraft.getMinecraft().gameSettings.anaglyph;
    }

    @Override
    public boolean openGl30() {
        return GLContext.getCapabilities().OpenGL30;
    }

    @Override
    public boolean framebufferHookRan() {
        return MainFramebufferRedirect.hookCalled();
    }

    @Override
    public boolean registriesComplete() {
        return Loader.instance().hasReachedState(LoaderState.AVAILABLE);
    }

    @Override
    public MaterialIds resolveMaterials(MaterialMappings mappings) {
        return MaterialIds.resolve(mappings);
    }

    @Override
    public boolean vanillaDraws(RenderStage stage) {
        if (stage != RenderStage.WEATHER && stage != RenderStage.CLOUDS) {
            return true;
        }
        World world = Minecraft.getMinecraft().world;
        if (world == null) {
            return false;
        }
        return stage == RenderStage.WEATHER ? world.provider.getWeatherRenderer() == null
                : world.provider.getCloudRenderer() == null;
    }

    @Override
    public void readMatrix(int matrix, float[] into) {
        FloatBuffer buffer = matrixBuffer;
        buffer.clear();
        GL11.glGetFloat(matrix, buffer);
        buffer.get(into, 0, 16);
    }

    @Override
    public boolean captureFrameValues(EnvironmentInputs environment, long frame, float partialTicks) {
        Minecraft mc = Minecraft.getMinecraft();
        WorldClient world = mc.world;
        Entity entity = mc.getRenderViewEntity();
        if (world == null || entity == null) {
            return false;
        }
        Vec3d sky = world.getSkyColor(entity, partialTicks);
        environment.captureFrame(frame, world.getWorldTime(), world.getMoonPhase(),
                world.getCelestialAngle(partialTicks), world.getRainStrength(partialTicks), (float) sky.x,
                (float) sky.y, (float) sky.z, entity.getBrightnessForRender());
        int nightVision = effect(entity, MobEffects.NIGHT_VISION);
        int blindness = effect(entity, MobEffects.BLINDNESS);
        environment.captureView(mc.gameSettings.renderDistanceChunks,
                nightVision < 0 ? 0 : EnvironmentInputs.nightVision(nightVision, partialTicks),
                blindness < 0 ? 0 : EnvironmentInputs.blindness(blindness), mc.gameSettings.gammaSetting);
        captureAtlas(mc, environment);
        return true;
    }

    private static int effect(Entity entity, Potion potion) {
        if (!(entity instanceof EntityLivingBase)) {
            return -1;
        }
        PotionEffect effect = ((EntityLivingBase) entity).getActivePotionEffect(potion);
        return effect == null ? -1 : effect.getDuration();
    }

    // TextureMap keeps no size of its own, but every sprite's UVs are its texel bounds divided by the atlas size, so
    // the missing sprite's width over its U span gives the width back. Rounding takes out the float error.
    private static void captureAtlas(Minecraft mc, EnvironmentInputs environment) {
        TextureAtlasSprite missing = mc.getTextureMapBlocks().getMissingSprite();
        float spanU = missing.getMaxU() - missing.getMinU();
        float spanV = missing.getMaxV() - missing.getMinV();
        environment.captureAtlas(spanU > 0 ? Math.round(missing.getIconWidth() / spanU) : 0,
                spanV > 0 ? Math.round(missing.getIconHeight() / spanV) : 0);
    }

    // The block vanilla's setupFog checks for water and lava fog, at the camera and not at the player's eyes, so this
    // always agrees with the fog.
    @Override
    public int medium(float partialTicks) {
        Minecraft mc = Minecraft.getMinecraft();
        Material material = ActiveRenderInfo.getBlockStateAtEntityViewpoint(mc.world, mc.getRenderViewEntity(),
                partialTicks).getMaterial();
        if (material == Material.WATER) {
            return EnvironmentInputs.MEDIUM_WATER;
        }
        if (material == Material.LAVA) {
            return EnvironmentInputs.MEDIUM_LAVA;
        }
        return EnvironmentInputs.MEDIUM_AIR;
    }

    @Override
    public int precipitation(double x, double y, double z) {
        Biome biome = Minecraft.getMinecraft().world.getBiome(new BlockPos(x, y, z));
        if (biome.getEnableSnow()) {
            return EnvironmentInputs.PRECIPITATION_SNOW;
        }
        return biome.canRain() ? EnvironmentInputs.PRECIPITATION_RAIN : EnvironmentInputs.PRECIPITATION_NONE;
    }
}
