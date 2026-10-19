package com.spothelper.mixin;

import com.spothelper.camera.F5FreeLookManager;
import com.spothelper.zoom.ZoomManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.render.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Свободная камера и зум дистанции F5.
 * Хуки привязаны к именам методов Camera (update, setRotation, clipToSpace), а не к
 * конкретным вызовам внутри них, поэтому не ломаются при внутренних правках 1.21.x.
 */
@Mixin(Camera.class)
public abstract class CameraFreeLookMixin {
    @Unique private int spothelper$yawCalls;
    @Unique private int spothelper$pitchCalls;

    @Inject(method = "update", at = @At("HEAD"))
    private void spothelper$beginUpdate(CallbackInfo ci) {
        spothelper$yawCalls = 0;
        spothelper$pitchCalls = 0;
    }

    /** Первый вызов setRotation в update ставит углы сущности — подменяем их углами камеры. */
    @ModifyVariable(method = "setRotation", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private float spothelper$yaw(float yaw) {
        if (spothelper$yawCalls++ != 0) return yaw;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.getCameraEntity() != mc.player) return yaw;
        return F5FreeLookManager.isActive(mc) ? F5FreeLookManager.getCameraYaw(yaw) : yaw;
    }

    @ModifyVariable(method = "setRotation", at = @At("HEAD"), argsOnly = true, ordinal = 1)
    private float spothelper$pitch(float pitch) {
        if (spothelper$pitchCalls++ != 0) return pitch;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.getCameraEntity() != mc.player) return pitch;
        return F5FreeLookManager.isActive(mc) ? F5FreeLookManager.getCameraPitch(pitch) : pitch;
    }

    /** Дистанция камеры в третьем лице. */
    @ModifyVariable(method = "clipToSpace", at = @At("HEAD"), argsOnly = true)
    private float spothelper$f5Distance(float distance) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.options != null && mc.options.getPerspective() != Perspective.FIRST_PERSON) {
            return ZoomManager.getF5CameraDistance(distance);
        }
        return distance;
    }
}
