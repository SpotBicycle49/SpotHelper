package com.spothelper.mixin;

import com.spothelper.config.SpotHelperConfig;
import net.minecraft.client.render.LightmapTextureManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(LightmapTextureManager.class)
public class LightmapTextureManagerMixin {

    /**
     * Перехватываем float m (brightness) в методе update(float tickProgress).
     * ordinal = 6 — потому что в методе много float-переменных (f, g, h, j, k, l, m, n, o),
     * и m идёт примерно шестой по счёту после STORE.
     */
    @ModifyVariable(
            method = "update",
            at = @At("STORE"),
            ordinal = 6
    )
    private float spothelper$modifyBrightness(float m) {
        SpotHelperConfig cfg = SpotHelperConfig.INSTANCE;
        if (!cfg.isGammaEnabled()) return m;
        return cfg.getGammaValue();
    }
}