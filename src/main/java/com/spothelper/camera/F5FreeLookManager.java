package com.spothelper.camera;

import com.spothelper.config.SpotHelperConfig;
import com.spothelper.client.SpotHelperClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.Perspective;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.MathHelper;

/** Клиентское состояние свободной камеры в режиме третьего лица. */
public final class F5FreeLookManager {
    private static float cameraYaw;
    private static float cameraPitch;
    private static float anchorYaw;
    private static boolean initialized;
    private static boolean lookKeyWasHeld;

    private F5FreeLookManager() {}

    public static void tick(MinecraftClient mc) {
        SpotHelperConfig cfg = SpotHelperConfig.INSTANCE;
        if (mc == null || mc.player == null || mc.options == null || !cfg.isF5FreeLook()
                || mc.options.getPerspective() == Perspective.FIRST_PERSON || mc.currentScreen != null) {
            initialized = false;
            lookKeyWasHeld = false;
            return;
        }
        if (!initialized) {
            cameraYaw = mc.player.getYaw();
            cameraPitch = mc.player.getPitch();
            anchorYaw = cameraYaw;
            initialized = true;
        }

        // При каждом новом удержании начинаем обзор с текущего направления игрока.
        // Само переключение F5 не включает свободную камеру.
        boolean lookKeyHeld = SpotHelperClient.FREE_LOOK_KEY != null
                && SpotHelperClient.FREE_LOOK_KEY.isPressed();
        if (lookKeyHeld && !lookKeyWasHeld) {
            cameraYaw = mc.player.getYaw();
            cameraPitch = mc.player.getPitch();
            anchorYaw = cameraYaw;
        }
        lookKeyWasHeld = lookKeyHeld;
    }

    public static boolean isActive(MinecraftClient mc) {
        return mc != null && mc.player != null && mc.options != null
                && SpotHelperConfig.INSTANCE.isF5FreeLook()
                && mc.options.getPerspective() != Perspective.FIRST_PERSON
                && mc.currentScreen == null && initialized
                && SpotHelperClient.FREE_LOOK_KEY != null
                && SpotHelperClient.FREE_LOOK_KEY.isPressed();
    }

    /**
     * Применяет уже рассчитанные Mouse.updateMouse дельты только к свободной камере.
     * Ванильный Mouse.updateMouse передаёт их в Entity.changeLookDirection(), где применяется множитель 0.15.
     * Здесь повторяем это вычисление, не меняя поворот игрока и его головы.
     */
    public static void applyMouseInput(double deltaX, double deltaY) {
        SpotHelperConfig cfg = SpotHelperConfig.INSTANCE;
        float sensitivity = cfg.getF5FreeLookSensitivity();
        float yawDelta = (float) deltaX * 0.15f * sensitivity;
        float pitchDelta = (float) deltaY * 0.15f * sensitivity;
        float range = cfg.getF5FreeLookRange();
        cameraYaw += yawDelta;
        if (range < 360.0f) {
            float offset = MathHelper.wrapDegrees(cameraYaw - anchorYaw);
            offset = MathHelper.clamp(offset, -range * 0.5f, range * 0.5f);
            cameraYaw = anchorYaw + offset;
        }
        cameraPitch = MathHelper.clamp(cameraPitch + pitchDelta, -90.0f, 90.0f);
    }

    /** Сохраняет вращение мыши как поворот камеры и возвращает игроку прежний yaw/pitch. */
    public static void applyMouseDelta(float oldYaw, float oldPitch, float newYaw, float newPitch) {
        SpotHelperConfig cfg = SpotHelperConfig.INSTANCE;
        float sensitivity = cfg.getF5FreeLookSensitivity();
        float yawDelta = MathHelper.wrapDegrees(newYaw - oldYaw) * sensitivity;
        float pitchDelta = (newPitch - oldPitch) * sensitivity;
        float range = cfg.getF5FreeLookRange();
        cameraYaw += yawDelta;
        if (range < 360.0f) {
            float offset = MathHelper.wrapDegrees(cameraYaw - anchorYaw);
            offset = MathHelper.clamp(offset, -range * 0.5f, range * 0.5f);
            cameraYaw = anchorYaw + offset;
        }
        cameraPitch = MathHelper.clamp(cameraPitch + pitchDelta, -90.0f, 90.0f);
    }

    public static float getCameraYaw(float vanillaYaw) {
        return initialized ? cameraYaw : vanillaYaw;
    }

    public static float getCameraPitch(float vanillaPitch) {
        return initialized ? cameraPitch : vanillaPitch;
    }

    public static void reset() {
        initialized = false;
        lookKeyWasHeld = false;
    }

    public static void restorePlayerRotation(Entity entity, float yaw, float pitch) {
        entity.setYaw(yaw);
        entity.setPitch(pitch);
    }
}
