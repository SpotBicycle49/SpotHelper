package com.spothelper.mixin;

import com.spothelper.zoom.ZoomManager;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameRenderer.class)
public abstract class ZoomFovMixin {
    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
    private void spothelper$applySmoothZoom(Camera camera, float tickProgress, boolean changingFov,
                                            CallbackInfoReturnable<Float> cir) {
        cir.setReturnValue(ZoomManager.applyFov(cir.getReturnValueF()));
    }
}
