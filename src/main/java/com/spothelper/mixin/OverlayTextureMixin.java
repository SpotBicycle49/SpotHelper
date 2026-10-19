package com.spothelper.mixin;

import com.spothelper.damagetint.DamageTintManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Красная половина текстуры наложения (строки 0–7; строки 8–15 — белая вспышка) — это и есть цвет тика урона. */
@Mixin(OverlayTexture.class)
public abstract class OverlayTextureMixin implements DamageTintManager.OverlayAccess {
    @Shadow @Final private NativeImageBackedTexture texture;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void spothelper$register(CallbackInfo ci) {
        DamageTintManager.register(this);
    }

    @Override
    public void spothelper$applyTint(int argb) {
        NativeImage img = this.texture.getImage();
        if (img == null) return;
        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 16; x++) img.setColorArgb(x, y, argb);
        }
        this.texture.upload();
    }
}
