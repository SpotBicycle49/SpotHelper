package com.spothelper.mixin;

import com.spothelper.shulkerlock.ShulkerLock;

import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Замок шалкера: не даёт закрыть окно игры (крестик, Alt+F4), пока в инвентаре есть шалкер. */
@Mixin(MinecraftClient.class)
public class MinecraftClientLockMixin {

    @Inject(method = "scheduleStop", at = @At("HEAD"), cancellable = true)
    private void spothelper$lockWindowClose(CallbackInfo ci) {
        if (ShulkerLock.blockWindowClose((MinecraftClient) (Object) this)) ci.cancel();
    }
}
