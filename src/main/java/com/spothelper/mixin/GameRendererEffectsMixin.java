package com.spothelper.mixin;

import com.spothelper.negeffects.NegativeEffects;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Рамка кадра для NegativeEffects + наклон камеры при уроне и покачивание камеры. */
@Mixin(GameRenderer.class)
public abstract class GameRendererEffectsMixin {

    @Inject(method = "render(Lnet/minecraft/client/render/RenderTickCounter;Z)V", at = @At("HEAD"))
    private void spothelper$beginFrame(RenderTickCounter counter, boolean tick, CallbackInfo ci) {
        MinecraftClient mc = MinecraftClient.getInstance();
        NegativeEffects.beginFrame(mc, mc.player instanceof NegativeEffects.NauseaAccess a ? a : null);
    }

    @Inject(method = "render(Lnet/minecraft/client/render/RenderTickCounter;Z)V", at = @At("RETURN"))
    private void spothelper$endFrame(RenderTickCounter counter, boolean tick, CallbackInfo ci) {
        MinecraftClient mc = MinecraftClient.getInstance();
        NegativeEffects.endFrame(mc.player instanceof NegativeEffects.NauseaAccess a ? a : null);
    }

    @Inject(method = "tiltViewWhenHurt", at = @At("HEAD"), cancellable = true)
    private void spothelper$noDamageTilt(MatrixStack matrices, float tickProgress, CallbackInfo ci) {
        if (NegativeEffects.hides(NegativeEffects.DAMAGE_TILT)) ci.cancel();
    }

    @Inject(method = "bobView", at = @At("HEAD"), cancellable = true)
    private void spothelper$noBob(MatrixStack matrices, float tickProgress, CallbackInfo ci) {
        if (NegativeEffects.hides(NegativeEffects.VIEW_BOB)) ci.cancel();
    }
}
