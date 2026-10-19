package com.spothelper.mixin;

import com.spothelper.damagetint.DamageTintManager;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Длительность красного тика урона (цвет — в OverlayTextureMixin). */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin {
    @Inject(method = "updateRenderState(Lnet/minecraft/entity/LivingEntity;"
            + "Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;F)V",
            at = @At("TAIL"))
    private void spothelper$damageTint(LivingEntity entity, LivingEntityRenderState state, float tickDelta, CallbackInfo ci) {
        state.hurt = DamageTintManager.isTinted(entity, state.hurt);
    }
}
