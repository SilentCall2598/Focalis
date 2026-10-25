// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import io.github.silentcall2598.focalis.render.state.FragmentHold;
import net.minecraft.client.renderer.GlStateManager;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Lets a program's alpha test and blend directives stay in effect while vanilla code that sets that state itself
// draws with the program, see FragmentHold. A held call changes neither the cache nor GL. The overloads taking
// GlStateManager's factor enums call these methods, so they are held too.
//
// None of these hooks may allocate. A cancellable injection creates a CallbackInfo on every call, held or not, and
// Java 8u51's JIT didn't always remove it (24 bytes per call in DJ2). So the hold skips the writes instead of
// returning early. Each boolean method is a single call on its cached BooleanState, which is skipped while held. The
// alpha function and blend factor methods only write when the cache differs, so their request is recorded when the
// method starts, and their cache writes and GL call are skipped while held. The injection that records never uses its
// CallbackInfo, so Mixin doesn't create one.
@Mixin(GlStateManager.class)
public abstract class GlStateManagerFragmentHoldMixin {

    private static final String ENABLE = "Lnet/minecraft/client/renderer/GlStateManager$BooleanState;setEnabled()V";
    private static final String DISABLE = "Lnet/minecraft/client/renderer/GlStateManager$BooleanState;setDisabled()V";
    private static final String ALPHA_FUNCTION = "Lnet/minecraft/client/renderer/GlStateManager$AlphaState;func:I";
    private static final String ALPHA_REFERENCE = "Lnet/minecraft/client/renderer/GlStateManager$AlphaState;ref:F";
    private static final String SOURCE = "Lnet/minecraft/client/renderer/GlStateManager$BlendState;srcFactor:I";
    private static final String DESTINATION = "Lnet/minecraft/client/renderer/GlStateManager$BlendState;dstFactor:I";
    private static final String SOURCE_ALPHA =
            "Lnet/minecraft/client/renderer/GlStateManager$BlendState;srcFactorAlpha:I";
    private static final String DESTINATION_ALPHA =
            "Lnet/minecraft/client/renderer/GlStateManager$BlendState;dstFactorAlpha:I";

    @WrapWithCondition(method = "enableAlpha()V", at = @At(value = "INVOKE", target = ENABLE), require = 0,
            expect = 1)
    private static boolean focalis$enableAlpha(@Coerce Object state) {
        return !FragmentHold.alphaTest(true);
    }

    @WrapWithCondition(method = "disableAlpha()V", at = @At(value = "INVOKE", target = DISABLE), require = 0,
            expect = 1)
    private static boolean focalis$disableAlpha(@Coerce Object state) {
        return !FragmentHold.alphaTest(false);
    }

    @WrapWithCondition(method = "enableBlend()V", at = @At(value = "INVOKE", target = ENABLE), require = 0,
            expect = 1)
    private static boolean focalis$enableBlend(@Coerce Object state) {
        return !FragmentHold.blend(true);
    }

    @WrapWithCondition(method = "disableBlend()V", at = @At(value = "INVOKE", target = DISABLE), require = 0,
            expect = 1)
    private static boolean focalis$disableBlend(@Coerce Object state) {
        return !FragmentHold.blend(false);
    }

    @Inject(method = "alphaFunc(IF)V", at = @At("HEAD"), require = 0, expect = 1)
    private static void focalis$alphaFunc(int function, float reference, CallbackInfo info) {
        FragmentHold.alphaFunction(function, reference);
    }

    @WrapWithCondition(method = "alphaFunc(IF)V", at = @At(value = "FIELD", target = ALPHA_FUNCTION,
            opcode = Opcodes.PUTFIELD), require = 0, expect = 1)
    private static boolean focalis$alphaFunctionWrite(@Coerce Object state, int function) {
        return FragmentHold.passes(FragmentHold.ALPHA_FUNCTION);
    }

    @WrapWithCondition(method = "alphaFunc(IF)V", at = @At(value = "FIELD", target = ALPHA_REFERENCE,
            opcode = Opcodes.PUTFIELD), require = 0, expect = 1)
    private static boolean focalis$alphaReferenceWrite(@Coerce Object state, float reference) {
        return FragmentHold.passes(FragmentHold.ALPHA_FUNCTION);
    }

    @WrapWithCondition(method = "alphaFunc(IF)V", at = @At(value = "INVOKE",
            target = "Lorg/lwjgl/opengl/GL11;glAlphaFunc(IF)V", remap = false), require = 0, expect = 1)
    private static boolean focalis$alphaFuncGl(int function, float reference) {
        return FragmentHold.passes(FragmentHold.ALPHA_FUNCTION);
    }

    // The two factor glBlendFunc sets the alpha factors to the color ones, so that is what it asks for.
    @Inject(method = "blendFunc(II)V", at = @At("HEAD"), require = 0, expect = 1)
    private static void focalis$blendFunc(int source, int destination, CallbackInfo info) {
        FragmentHold.blendFactors(source, destination, source, destination);
    }

    @WrapWithCondition(method = "blendFunc(II)V", at = {
            @At(value = "FIELD", target = SOURCE, opcode = Opcodes.PUTFIELD),
            @At(value = "FIELD", target = DESTINATION, opcode = Opcodes.PUTFIELD)}, require = 0, expect = 2)
    private static boolean focalis$blendFuncWrite(@Coerce Object state, int factor) {
        return FragmentHold.passes(FragmentHold.BLEND_FACTORS);
    }

    @WrapWithCondition(method = "blendFunc(II)V", at = @At(value = "INVOKE",
            target = "Lorg/lwjgl/opengl/GL11;glBlendFunc(II)V", remap = false), require = 0, expect = 1)
    private static boolean focalis$blendFuncGl(int source, int destination) {
        return FragmentHold.passes(FragmentHold.BLEND_FACTORS);
    }

    @Inject(method = "tryBlendFuncSeparate(IIII)V", at = @At("HEAD"), require = 0, expect = 1)
    private static void focalis$blendFuncSeparate(int sourceColor, int destinationColor, int sourceAlpha,
            int destinationAlpha, CallbackInfo info) {
        FragmentHold.blendFactors(sourceColor, destinationColor, sourceAlpha, destinationAlpha);
    }

    @WrapWithCondition(method = "tryBlendFuncSeparate(IIII)V", at = {
            @At(value = "FIELD", target = SOURCE, opcode = Opcodes.PUTFIELD),
            @At(value = "FIELD", target = DESTINATION, opcode = Opcodes.PUTFIELD),
            @At(value = "FIELD", target = SOURCE_ALPHA, opcode = Opcodes.PUTFIELD),
            @At(value = "FIELD", target = DESTINATION_ALPHA, opcode = Opcodes.PUTFIELD)}, require = 0, expect = 4)
    private static boolean focalis$blendFuncSeparateWrite(@Coerce Object state, int factor) {
        return FragmentHold.passes(FragmentHold.BLEND_FACTORS);
    }

    @WrapWithCondition(method = "tryBlendFuncSeparate(IIII)V", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/OpenGlHelper;glBlendFunc(IIII)V"), require = 0, expect = 1)
    private static boolean focalis$blendFuncSeparateGl(int sourceColor, int destinationColor, int sourceAlpha,
            int destinationAlpha) {
        return FragmentHold.passes(FragmentHold.BLEND_FACTORS);
    }
}
