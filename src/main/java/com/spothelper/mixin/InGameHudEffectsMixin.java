package com.spothelper.mixin;

import com.spothelper.negeffects.NegativeEffects;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Виньетка по краям экрана. */
@Mixin(InGameHud.class)
public abstract class InGameHudEffectsMixin {
    @Inject(method = "renderVignetteOverlay", at = @At("HEAD"), cancellable = true)
    private void spothelper$noVignette(DrawContext context, Entity entity, CallbackInfo ci) {
        if (NegativeEffects.hides(NegativeEffects.VIGNETTE)) ci.cancel();
    }
}
