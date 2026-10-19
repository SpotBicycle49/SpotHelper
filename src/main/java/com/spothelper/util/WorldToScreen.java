package com.spothelper.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.util.math.Vec3d;

/**
 * Проекция точки мира на экран для подписей в HUD (имена меток, полоски здоровья).
 * Считается вручную по позиции, углам камеры и FOV, без матриц рендера, поэтому работает в HudRenderCallback.
 * Не учитывает покачивание камеры и динамический FOV (бег, зелья) — для подписей этого достаточно.
 */
public final class WorldToScreen {
    private WorldToScreen() {}

    /** Возвращает {x, y} в GUI-координатах или null, если точка за спиной камеры. */
    public static float[] project(MinecraftClient mc, Vec3d target) {
        if (mc.gameRenderer == null || mc.options == null) return null;
        Camera camera = mc.gameRenderer.getCamera();
        if (camera == null) return null;

        Vec3d cam = camera.getCameraPos();
        double yaw = Math.toRadians(camera.getYaw());
        double pitch = Math.toRadians(camera.getPitch());

        // базис камеры: вперёд, вправо, вверх
        double fx = -Math.sin(yaw) * Math.cos(pitch);
        double fy = -Math.sin(pitch);
        double fz = Math.cos(yaw) * Math.cos(pitch);
        double rx = -Math.cos(yaw);
        double ry = 0.0;
        double rz = -Math.sin(yaw);
        double ux = ry * fz - rz * fy;
        double uy = rz * fx - rx * fz;
        double uz = rx * fy - ry * fx;

        double dx = target.x - cam.x;
        double dy = target.y - cam.y;
        double dz = target.z - cam.z;

        double depth = dx * fx + dy * fy + dz * fz;
        if (depth <= 0.05) return null;
        double sx = dx * rx + dy * ry + dz * rz;
        double sy = dx * ux + dy * uy + dz * uz;

        double fov = mc.options.getFov().getValue();
        double tanHalf = Math.tan(Math.toRadians(fov) / 2.0);
        int w = mc.getWindow().getScaledWidth();
        int h = mc.getWindow().getScaledHeight();
        double aspect = (double) w / (double) h;

        double ndcX = sx / (depth * tanHalf * aspect);
        double ndcY = sy / (depth * tanHalf);
        float px = (float) (w / 2.0 + ndcX * w / 2.0);
        float py = (float) (h / 2.0 - ndcY * h / 2.0);
        return new float[]{px, py};
    }
}
