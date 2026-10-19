package com.spothelper.mixin;

import com.spothelper.negeffects.NegativeEffects;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.SimpleOption;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Вспышки молний: подставляем опцию «скрывать вспышки» (сама настройка игры не меняется). */
@Mixin(GameOptions.class)
public abstract class GameOptionsEffectsMixin {
    private static SimpleOption<Boolean> spothelper$hideLightning;

    @Inject(method = "getHideLightningFlashes", at = @At("HEAD"), cancellable = true)
    private void spothelper$hideLightningFlashes(CallbackInfoReturnable<SimpleOption<Boolean>> cir) {
        if (!NegativeEffects.hides(NegativeEffects.LIGHTNING)) return;
        if (spothelper$hideLightning == null) spothelper$hideLightning = SimpleOption.ofBoolean("spothelper.hideLightning", true);
        cir.setReturnValue(spothelper$hideLightning);
    }
}
