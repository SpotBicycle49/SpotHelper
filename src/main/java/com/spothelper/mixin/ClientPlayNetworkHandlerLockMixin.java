package com.spothelper.mixin;

import com.spothelper.shulkerlock.ShulkerLock;

import net.minecraft.client.network.ClientPlayNetworkHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Замок шалкера: не отправляет команды из списка, пока в инвентаре есть шалкер. */
@Mixin(ClientPlayNetworkHandler.class)
public class ClientPlayNetworkHandlerLockMixin {

    @Inject(method = "sendChatCommand", at = @At("HEAD"), cancellable = true)
    private void spothelper$lockChatCommand(String command, CallbackInfo ci) {
        if (ShulkerLock.blockCommand(command)) ci.cancel();
    }

    // В 1.21.11 метода sendCommand в ClientPlayNetworkHandler нет — закомментировано.
    // Если найдёшь новый метод для команд без слэша — верни аналогичный @Inject.
}