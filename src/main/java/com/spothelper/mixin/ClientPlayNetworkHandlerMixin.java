package com.spothelper.mixin;

import com.spothelper.holyworld.EventFlow;
import com.spothelper.slotlock.SlotLockManager;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.GameMessageS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayNetworkHandler.class)
public class ClientPlayNetworkHandlerMixin {

    @Inject(method = "onGameMessage", at = @At("HEAD"))
    private void spothelper$onGameMessage(GameMessageS2CPacket packet, CallbackInfo ci) {
        if (!MinecraftClient.getInstance().isOnThread()) return;
        try {
            EventFlow.onChat(packet.content().getString());
        } catch (Throwable ignored) {
        }
    }

    @Inject(method = "sendChatCommand", at = @At("HEAD"), cancellable = true)
    private void spothelper$sendChatCommand(String command, CallbackInfo ci) {
        if (SlotLockManager.blocksSellCommand(command)) { ci.cancel(); return; }
        if (com.spothelper.sellprotect.SellGuard.intercept(command)) ci.cancel();
    }
}
