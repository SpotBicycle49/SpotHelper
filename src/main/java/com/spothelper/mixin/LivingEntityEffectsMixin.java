package com.spothelper.mixin;

import com.spothelper.negeffects.NegativeEffects;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.registry.entry.RegistryEntry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Слепота/тьма: во время отрисовки кадра клиентский игрок «не видит» эти эффекты (туман, лайтмап). */
@Mixin(LivingEntity.class)
public abstract class LivingEntityEffectsMixin {

    @Inject(method = "hasStatusEffect", at = @At("HEAD"), cancellable = true)
    private void spothelper$hideHas(RegistryEntry<StatusEffect> effect, CallbackInfoReturnable<Boolean> cir) {
        if (NegativeEffects.hidesStatusEffect((LivingEntity) (Object) this, effect)) cir.setReturnValue(false);
    }

    @Inject(method = "getStatusEffect", at = @At("HEAD"), cancellable = true)
    private void spothelper$hideGet(RegistryEntry<StatusEffect> effect, CallbackInfoReturnable<StatusEffectInstance> cir) {
        if (NegativeEffects.hidesStatusEffect((LivingEntity) (Object) this, effect)) cir.setReturnValue(null);
    }
}
