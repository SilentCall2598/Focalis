// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.render.lifecycle;

import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

// Only observes Forge events. It never cancels them or touches GL state, so it can't change what Minecraft draws.
public final class ForgeRenderEventBridge {

    private final RenderLifecycle lifecycle;

    private ForgeRenderEventBridge(RenderLifecycle lifecycle) {
        this.lifecycle = lifecycle;
    }

    public static void install(RenderLifecycle lifecycle) {
        lifecycle.markDispatched(RenderStage.FRAME);
        lifecycle.markDispatched(RenderStage.WORLD);
        MinecraftForge.EVENT_BUS.register(new ForgeRenderEventBridge(lifecycle));
    }

    // Minecraft doesn't fire this on frames it skips entirely.
    @SubscribeEvent
    public void onRenderTick(TickEvent.RenderTickEvent event) {
        RenderPhase phase = event.phase == TickEvent.Phase.START ? RenderPhase.START : RenderPhase.END;
        lifecycle.dispatch(RenderStage.FRAME, phase, event.renderTickTime);
    }

    // Fires at the end of the world pass, right before the first-person hand. Forge has no event for the start of
    // the world pass, so WORLD only gets END.
    @SubscribeEvent
    public void onRenderWorldLast(RenderWorldLastEvent event) {
        lifecycle.dispatch(RenderStage.WORLD, RenderPhase.END, event.getPartialTicks());
    }
}
