package com.spothelper.mixin;

import com.spothelper.critsounds.AttackSounds;
import com.spothelper.critsounds.AttackTracker;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.hit.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Обрамляет обработку клика ЛКМ (MinecraftClient.doAttack): запоминает, куда смотрит прицел.
 * Сам клик не блокируется, ничего не отменяется и не задерживается. Взмах ловит ClientPlayerEntitySwingMixin
 * только внутри этого окна, поэтому клик, который игра отбросила, звука не даёт.
 */
@Mixin(MinecraftClient.class)
public class MinecraftClientAttackMixin {

    @Inject(method = "doAttack", at = @At("HEAD"))
    private void spothelper$attackBegin(CallbackInfoReturnable<Boolean> cir) {
        try {
            HitResult hit = ((MinecraftClient) (Object) this).crosshairTarget;
            HitResult.Type t = hit == null ? HitResult.Type.MISS : hit.getType();
            AttackSounds.beginClick(t == HitResult.Type.ENTITY ? AttackTracker.Swing.ENTITY
                    : t == HitResult.Type.BLOCK ? AttackTracker.Swing.BLOCK : AttackTracker.Swing.AIR);
        } catch (Throwable ignored) {
        }
    }

    @Inject(method = "doAttack", at = @At("RETURN"))
    private void spothelper$attackEnd(CallbackInfoReturnable<Boolean> cir) {
        try {
            AttackSounds.endClick();
        } catch (Throwable ignored) {
        }
    }
}
