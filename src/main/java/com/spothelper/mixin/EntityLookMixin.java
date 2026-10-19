package com.spothelper.mixin;

import com.spothelper.camera.F5FreeLookManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Пока активна свободная камера, движение мыши уходит в камеру, а не в поворот игрока.
 * Перехват в самом Entity.changeLookDirection работает независимо от того,
 * как именно Mouse.updateMouse вызывает его в текущей версии.
 */
@Mixin(Entity.class)
public abstract class EntityLookMixin {
    @Inject(method = "changeLookDirection", at = @At("HEAD"), cancellable = true)
    private void spothelper$freeLook(double deltaX, double deltaY, CallbackInfo ci) {
        if (!((Object) this instanceof ClientPlayerEntity)) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if ((Object) this != mc.player) return;
        if (F5FreeLookManager.isActive(mc)) {
            F5FreeLookManager.applyMouseInput(deltaX, deltaY);
            ci.cancel();
        }
    }
}
