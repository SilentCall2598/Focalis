// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.mixin;

import net.minecraft.client.renderer.DestroyBlockProgress;
import net.minecraft.client.renderer.RenderGlobal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

// The blocks being broken. drawBlockDamageTexture draws nothing while this is empty.
@Mixin(RenderGlobal.class)
public interface RenderGlobalDamageAccessor {

    @Accessor("damagedBlocks")
    Map<Integer, DestroyBlockProgress> focalis$damagedBlocks();
}
