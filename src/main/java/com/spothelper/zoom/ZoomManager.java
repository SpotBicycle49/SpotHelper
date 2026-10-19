package com.spothelper.zoom;

import com.spothelper.config.SpotHelperConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.util.InputUtil;

/** Состояние обычного FOV-зумa и независимого зума дистанции F5. */
public final class ZoomManager {
    private static float currentZoom = 1.0f;
    private static float targetZoom = 1.0f;

    private ZoomManager() {}

    public static void tick(MinecraftClient mc) {
        if (mc == null || mc.options == null || mc.getWindow() == null) return;
        SpotHelperConfig cfg = SpotHelperConfig.INSTANCE;
        boolean canZoom = cfg.isZoomEnabled() && mc.player != null && mc.currentScreen == null;
        boolean pressed = canZoom && InputUtil.isKeyPressed(mc.getWindow(), cfg.getZoomKey());
        targetZoom = pressed ? cfg.getZoomDefault() : 1.0f;
        currentZoom += (targetZoom - currentZoom) * 0.35f;
        if (Math.abs(targetZoom - currentZoom) < 0.001f) currentZoom = targetZoom;
    }

    public static boolean isNormalZoomActive(MinecraftClient mc) {
        return mc != null && mc.player != null && mc.currentScreen == null
                && SpotHelperConfig.INSTANCE.isZoomEnabled()
                && InputUtil.isKeyPressed(mc.getWindow(), SpotHelperConfig.INSTANCE.getZoomKey());
    }

    /** В Minecraft 1.21.11 GameRenderer#getFov возвращает float, поэтому используем float и здесь. */
    public static float applyFov(float originalFov) {
        if (!SpotHelperConfig.INSTANCE.isZoomEnabled()) return originalFov;
        double factor = Math.max(0.05, Math.min(1.0, currentZoom));
        double radians = Math.toRadians(originalFov);
        return (float) Math.toDegrees(2.0 * Math.atan(Math.tan(radians / 2.0) * factor));
    }

    public static boolean handleScroll(MinecraftClient mc, double vertical) {
        if (mc == null || mc.player == null || mc.currentScreen != null || vertical == 0.0) return false;
        SpotHelperConfig cfg = SpotHelperConfig.INSTANCE;
        if (mc.options.getPerspective() != Perspective.FIRST_PERSON && cfg.isF5ZoomEnabled()) {
            float next = cfg.getF5ZoomDistance() - (float) vertical * cfg.getF5ZoomScrollStep();
            cfg.f5ZoomDistance = Math.max(cfg.getF5ZoomMin(), Math.min(cfg.getF5ZoomMax(), next));
            cfg.save();
            return true;
        }
        if (isNormalZoomActive(mc)) {
            float next = currentZoom - (float) vertical * cfg.getZoomScrollStep();
            currentZoom = Math.max(cfg.getZoomMin(), Math.min(cfg.getZoomMax(), next));
            targetZoom = currentZoom;
            cfg.zoomDefault = currentZoom;
            cfg.save();
            return true;
        }
        return false;
    }

    public static float getF5CameraDistance(float vanillaDistance) {
        SpotHelperConfig cfg = SpotHelperConfig.INSTANCE;
        return cfg.isF5ZoomEnabled() ? cfg.getF5ZoomDistance() : vanillaDistance;
    }
}
