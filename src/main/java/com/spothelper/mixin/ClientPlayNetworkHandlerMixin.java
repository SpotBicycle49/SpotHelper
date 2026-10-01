package com.spothelper.mixin;

import com.spothelper.client.EventFlow;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.GameMessageS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Пересылает входящие сообщения чата в EventFlow: так мод читает ответ команды /event.
 * Само сообщение не меняется и не скрывается.
 */
@Mixin(ClientPlayNetworkHandler.class)
public class ClientPlayNetworkHandlerMixin {

    @Inject(method = "onGameMessage", at = @At("HEAD"))
    private void spothelper$onGameMessage(GameMessageS2CPacket packet, CallbackInfo ci) {
        // обработчик пакета вызывается дважды (сетевой поток, затем главный) - берём только главный
        if (!MinecraftClient.getInstance().isOnThread()) return;
        try {
            EventFlow.onChat(packet.getMessage().getString());
        } catch (Throwable ignored) {
        }
    }
}
