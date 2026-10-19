package com.spothelper.waypoints;

import com.spothelper.config.SpotHelperConfig;
import com.spothelper.util.WorldToScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.Camera;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.List;

/**
 * Метки в мире — 2D-часть (рисуется в HUD, работает всегда, независимо от 3D-рендера):
 *  - метка в поле зрения: ромб + имя + расстояние над точкой;
 *  - метка за спиной или вне экрана: маркер на краю экрана в сторону метки + имя и расстояние.
 * 3D-часть (рамка и луч сквозь стены) рисует WaypointWorldRenderer из GameRendererMixin.
 */
public final class WaypointLabels {
    private static final int EDGE = 30;   // отступ маркера от края экрана

    private WaypointLabels() {}

    public static void render(DrawContext ctx) {
        SpotHelperConfig cfg = SpotHelperConfig.INSTANCE;
        if (cfg.showWaypointsInWorld != 1) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null || mc.options.hudHidden) return;

        List<Waypoint> list = WaypointManager.current();
        if (list.isEmpty()) return;

        TextRenderer tr = mc.textRenderer;
        int sw = mc.getWindow().getScaledWidth();
        int sh = mc.getWindow().getScaledHeight();
        Vec3d eye = mc.player.getEyePos();
        Camera camera = mc.gameRenderer == null ? null : mc.gameRenderer.getCamera();

        for (Waypoint w : list) {
            if (!w.visible) continue;

            double dist = eye.distanceTo(new Vec3d(w.x + 0.5, w.y + 0.5, w.z + 0.5));
            String name = (w.name == null || w.name.isEmpty()) ? "Метка" : w.name;
            String dist1 = WaypointRenderer.formatDist(dist);
            int color = 0xFF000000 | (w.color & 0xFFFFFF);

            float[] p = WorldToScreen.project(mc, new Vec3d(w.x + 0.5, w.y + 1.9, w.z + 0.5));
            boolean onScreen = p != null && p[0] >= 0 && p[0] <= sw && p[1] >= 0 && p[1] <= sh;

            if (onScreen) {
                int x = Math.round(p[0]);
                int y = Math.round(p[1]);
                // маленький ромб-маркер и две строки текста
                ctx.fill(x - 2, y - 2, x + 2, y + 2, color);
                ctx.drawCenteredTextWithShadow(tr, name, x, y - 22, color);
                ctx.drawCenteredTextWithShadow(tr, dist1, x, y - 12, 0xFFDDDDDD);
                continue;
            }

            // ---- метка вне экрана: маркер на краю в сторону метки ----
            if (camera == null) continue;
            Vec3d cam = camera.getCameraPos();
            double dx = (w.x + 0.5) - cam.x;
            double dz = (w.z + 0.5) - cam.z;
            // yaw в ванили: 0 = +Z, растёт к -X; delta > 0 — метка правее взгляда
            double yawTo = Math.toDegrees(Math.atan2(-dx, dz));
            double delta = MathHelper.wrapDegrees(yawTo - camera.getYaw());
            double rad = Math.toRadians(delta);

            double ex = sw / 2.0 + Math.sin(rad) * (sw / 2.0 - EDGE);
            double ey = sh / 2.0 - Math.cos(rad) * (sh / 2.0 - EDGE);
            int mx = (int) Math.round(ex);
            int my = (int) Math.round(ey);

            // стрелка-ромб, повёрнутый «острием» в сторону метки
            ctx.fill(mx - 4, my - 1, mx + 5, my + 2, color);
            ctx.fill(mx - 1, my - 4, mx + 2, my + 5, color);
            ctx.fill(mx - 2, my - 2, mx + 3, my + 3, 0xFF000000 | (color & 0x00FFFFFF));

            int labelW = Math.max(tr.getWidth(name), tr.getWidth(dist1));
            int tx = MathHelper.clamp(mx, labelW / 2 + 2, sw - labelW / 2 - 2);
            int ty = my < sh / 2 ? my + 8 : my - 26;
            ctx.drawCenteredTextWithShadow(tr, name, tx, ty, color);
            ctx.drawCenteredTextWithShadow(tr, dist1, tx, ty + 10, 0xFFDDDDDD);
        }
    }
}
