package com.spothelper.mixin;

import com.spothelper.zoom.ZoomManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.Mouse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Колёсико мыши: зум FOV и дистанция камеры F5. */
@Mixin(Mouse.class)
public abstract class MouseFreeLookMixin {
    @Inject(method = "onMouseScroll", at = @At("HEAD"), cancellable = true)
    private void spothelper$handleZoomScroll(long window, double horizontal, double vertical, CallbackInfo ci) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (ZoomManager.handleScroll(mc, vertical)) {
            ci.cancel();
        }
    }
}
