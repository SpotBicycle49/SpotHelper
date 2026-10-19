package com.spothelper.mixin;

import com.spothelper.armorview.ArmorDurabilityText;
import com.spothelper.config.SpotHelperConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.PlayerLikeEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerEntityRenderer.class)
public abstract class PlayerEntityRendererMixin {

    @Inject(method = "updateRenderState(Lnet/minecraft/entity/PlayerLikeEntity;"
            + "Lnet/minecraft/client/render/entity/state/PlayerEntityRenderState;F)V",
            at = @At("TAIL"))
    private void spothelper$durability(PlayerLikeEntity player,
                                       PlayerEntityRenderState state, float tickDelta, CallbackInfo ci) {
        if (!SpotHelperConfig.INSTANCE.isArmorDurabilityEnabled()) return;
        if (state.displayName == null) return;
        if (player == MinecraftClient.getInstance().player) return;
        if (!(player instanceof PlayerEntity pe)) return;
        state.displayName = Text.empty().append(state.displayName)
                .append(ArmorDurabilityText.build(pe));
    }
}
