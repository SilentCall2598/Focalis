// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.silentcall2598.focalis.render.lifecycle.RenderDrawKind;
import io.github.silentcall2598.focalis.render.lifecycle.RenderHooks;
import io.github.silentcall2598.focalis.render.lifecycle.RenderStage;
import net.minecraft.client.particle.ParticleManager;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.culling.ICamera;
import net.minecraft.entity.Entity;
import net.minecraft.util.BlockRenderLayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

// Wraps vanilla calls inside the world pass so RenderHooks can report each stage. Each wrapper calls the original once
// and changes nothing. require = 0 so a call another mod changed can't stop the game, and expect keeps the counts
// checkable with Mixin's debug options.
@Mixin(EntityRenderer.class)
public abstract class EntityRendererStageMixin {

    @WrapOperation(method = "renderWorldPass(IFJ)V", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/RenderGlobal;renderSky(FI)V"), require = 0, expect = 1)
    private void focalis$sky(RenderGlobal renderGlobal, float partialTicks, int pass, Operation<Void> original) {
        RenderHooks.stageStart(RenderStage.SKY, partialTicks);
        try {
            original.call(renderGlobal, partialTicks, pass);
        } finally {
            RenderHooks.stageEnd(RenderStage.SKY, partialTicks);
        }
    }

    // All four block layers go through this one call, and the layer argument says which one it is.
    @WrapOperation(method = "renderWorldPass(IFJ)V", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/RenderGlobal;renderBlockLayer("
                    + "Lnet/minecraft/util/BlockRenderLayer;DILnet/minecraft/entity/Entity;)I"),
            require = 0, expect = 4)
    private int focalis$blockLayer(RenderGlobal renderGlobal, BlockRenderLayer layer, double partialTicks, int pass,
            Entity entity, Operation<Integer> original) {
        RenderStage stage = layer == BlockRenderLayer.TRANSLUCENT ? RenderStage.TRANSLUCENT : RenderStage.TERRAIN;
        RenderDrawKind kind = focalis$terrainKind(layer);
        float ticks = (float) partialTicks;
        RenderHooks.stageStart(stage, kind, ticks);
        try {
            return original.call(renderGlobal, layer, partialTicks, pass, entity);
        } finally {
            RenderHooks.stageEnd(stage, kind, ticks);
        }
    }

    private static RenderDrawKind focalis$terrainKind(BlockRenderLayer layer) {
        if (layer == BlockRenderLayer.SOLID) {
            return RenderDrawKind.TERRAIN_SOLID;
        }
        if (layer == BlockRenderLayer.CUTOUT_MIPPED) {
            return RenderDrawKind.TERRAIN_CUTOUT_MIPPED;
        }
        if (layer == BlockRenderLayer.CUTOUT) {
            return RenderDrawKind.TERRAIN_CUTOUT;
        }
        if (layer == BlockRenderLayer.TRANSLUCENT) {
            return RenderDrawKind.TERRAIN_TRANSLUCENT;
        }
        // A layer some mod added isn't classified.
        return RenderDrawKind.DEFAULT;
    }

    // Forge sets entity render pass 0 right before the first call and pass 1 right before the second, which comes
    // after translucent terrain. Nothing else in renderWorldPass calls renderEntities.
    @WrapOperation(method = "renderWorldPass(IFJ)V", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/RenderGlobal;renderEntities("
                    + "Lnet/minecraft/entity/Entity;Lnet/minecraft/client/renderer/culling/ICamera;F)V", ordinal = 0),
            require = 0, expect = 1)
    private void focalis$entitiesPass0(RenderGlobal renderGlobal, Entity entity, ICamera camera, float partialTicks,
            Operation<Void> original) {
        RenderHooks.stageStart(RenderStage.ENTITIES, RenderDrawKind.ENTITY_PASS_0, partialTicks);
        try {
            original.call(renderGlobal, entity, camera, partialTicks);
        } finally {
            RenderHooks.stageEnd(RenderStage.ENTITIES, RenderDrawKind.ENTITY_PASS_0, partialTicks);
        }
    }

    @WrapOperation(method = "renderWorldPass(IFJ)V", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/RenderGlobal;renderEntities("
                    + "Lnet/minecraft/entity/Entity;Lnet/minecraft/client/renderer/culling/ICamera;F)V", ordinal = 1),
            require = 0, expect = 1)
    private void focalis$entitiesPass1(RenderGlobal renderGlobal, Entity entity, ICamera camera, float partialTicks,
            Operation<Void> original) {
        RenderHooks.stageStart(RenderStage.ENTITIES, RenderDrawKind.ENTITY_PASS_1, partialTicks);
        try {
            original.call(renderGlobal, entity, camera, partialTicks);
        } finally {
            RenderHooks.stageEnd(RenderStage.ENTITIES, RenderDrawKind.ENTITY_PASS_1, partialTicks);
        }
    }

    @WrapOperation(method = "renderWorldPass(IFJ)V", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/particle/ParticleManager;renderLitParticles("
                    + "Lnet/minecraft/entity/Entity;F)V"),
            require = 0, expect = 1)
    private void focalis$litParticles(ParticleManager particles, Entity entity, float partialTicks,
            Operation<Void> original) {
        RenderHooks.stageStart(RenderStage.PARTICLES, RenderDrawKind.PARTICLES_LIT, partialTicks);
        try {
            original.call(particles, entity, partialTicks);
        } finally {
            RenderHooks.stageEnd(RenderStage.PARTICLES, RenderDrawKind.PARTICLES_LIT, partialTicks);
        }
    }

    @WrapOperation(method = "renderWorldPass(IFJ)V", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/particle/ParticleManager;renderParticles("
                    + "Lnet/minecraft/entity/Entity;F)V"),
            require = 0, expect = 1)
    private void focalis$particles(ParticleManager particles, Entity entity, float partialTicks,
            Operation<Void> original) {
        RenderHooks.stageStart(RenderStage.PARTICLES, RenderDrawKind.PARTICLES_NORMAL, partialTicks);
        try {
            original.call(particles, entity, partialTicks);
        } finally {
            RenderHooks.stageEnd(RenderStage.PARTICLES, RenderDrawKind.PARTICLES_NORMAL, partialTicks);
        }
    }

    @WrapOperation(method = "renderWorldPass(IFJ)V", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/EntityRenderer;renderRainSnow(F)V"), require = 0, expect = 1)
    private void focalis$weather(EntityRenderer renderer, float partialTicks, Operation<Void> original) {
        RenderHooks.stageStart(RenderStage.WEATHER, partialTicks);
        try {
            original.call(renderer, partialTicks);
        } finally {
            RenderHooks.stageEnd(RenderStage.WEATHER, partialTicks);
        }
    }

    // renderCloudsCheck is called from two places in the world pass and only draws when clouds are on.
    @WrapOperation(method = "renderCloudsCheck(Lnet/minecraft/client/renderer/RenderGlobal;FIDDD)V", at = @At(
            value = "INVOKE", target = "Lnet/minecraft/client/renderer/RenderGlobal;renderClouds(FIDDD)V"),
            require = 0, expect = 1)
    private void focalis$clouds(RenderGlobal renderGlobal, float partialTicks, int pass, double x, double y,
            double z, Operation<Void> original) {
        RenderHooks.stageStart(RenderStage.CLOUDS, partialTicks);
        try {
            original.call(renderGlobal, partialTicks, pass, x, y, z);
        } finally {
            RenderHooks.stageEnd(RenderStage.CLOUDS, partialTicks);
        }
    }

    // After Forge's RenderWorldLastEvent, so HAND comes after WORLD END.
    @WrapOperation(method = "renderWorldPass(IFJ)V", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/EntityRenderer;renderHand(FI)V"), require = 0, expect = 1)
    private void focalis$hand(EntityRenderer renderer, float partialTicks, int pass, Operation<Void> original) {
        RenderHooks.stageStart(RenderStage.HAND, partialTicks);
        try {
            original.call(renderer, partialTicks, pass);
        } finally {
            RenderHooks.stageEnd(RenderStage.HAND, partialTicks);
        }
    }
}
