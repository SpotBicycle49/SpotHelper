package com.spothelper.mixin;

import com.spothelper.crosshair.CrosshairColorManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InGameHud.class)
public abstract class InGameHudCrosshairMixin {
    @Inject(method = "renderCrosshair", at = @At("HEAD"), cancellable = true)
    private void spothelper$customCrosshair(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        if (CrosshairColorManager.render(MinecraftClient.getInstance(), context)) {
            ci.cancel();
        }
    }
}
