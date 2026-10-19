package com.spothelper.mixin;

import com.spothelper.friends.FriendsManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.screen.slot.SlotActionType;
import com.spothelper.slotlock.SlotLockManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayerInteractionManager.class)
public class ClientPlayerInteractionManagerMixin {
    @Inject(method = "attackEntity", at = @At("HEAD"), cancellable = true)
    private void spothelper$attackEntity(PlayerEntity player, Entity target, CallbackInfo ci) {
        if (player == null || target == null) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != player) return;

        if (target instanceof PlayerEntity) {
            // Друзей из вкладки «Друзья» ударить нельзя.
            if (FriendsManager.isFriend((PlayerEntity) target)) {
                ci.cancel();
                return;
            }
        }

        // Удар состоялся (не отменён): запоминаем его, чтобы дождаться подтверждения крита от сервера.
        try {
            com.spothelper.critsounds.AttackSounds.onAttack(player, target);
        } catch (Throwable ignored) {
        }
    }

    @Inject(method = "clickSlot", at = @At("HEAD"), cancellable = true)
    private void spothelper$clickSlot(int syncId, int slotId, int button, SlotActionType actionType,
                                      PlayerEntity player, CallbackInfo ci) {
        if (SlotLockManager.blocksClick(player, slotId, button, actionType)) ci.cancel();
    }
}