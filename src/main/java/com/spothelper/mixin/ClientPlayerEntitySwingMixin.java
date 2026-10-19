package com.spothelper.mixin;

import com.spothelper.critsounds.AttackSounds;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Фактический взмах основной рукой локального игрока. Вне клика ЛКМ (использование предмета и т. п.) игнорируется. */
@Mixin(ClientPlayerEntity.class)
public class ClientPlayerEntitySwingMixin {

    @Inject(method = "swingHand", at = @At("HEAD"))
    private void spothelper$onSwing(Hand hand, CallbackInfo ci) {
        if (hand != Hand.MAIN_HAND) return;
        try {
            AttackSounds.onSwing();
        } catch (Throwable ignored) {
        }
    }
}
