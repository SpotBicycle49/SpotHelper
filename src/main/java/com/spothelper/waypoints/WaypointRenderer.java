package com.spothelper.waypoints;

import com.spothelper.config.SpotHelperConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import org.joml.Matrix3x2fStack;

import java.util.List;

/** Рисует метки на миникарте. Вызывается из Minimap.render() внутри области карты. */
public final class WaypointRenderer {

    private WaypointRenderer() {}

    public static void render(DrawContext ctx, MinecraftClient mc,
                              int x, int y, int size, int radius,
                              double playerX, double playerZ,
                              float yaw, boolean rotate, boolean circle) {
        SpotHelperConfig cfg = SpotHelperConfig.INSTANCE;
        if (!cfg.isMinimapWaypoints()) return;

        List<Waypoint> list = WaypointManager.current();
        if (list.isEmpty()) return;

        int half = size / 2;
        int cx = x + half;
        int cy = y + half;
        double pxPerBlock = (double) size / (radius * 2.0);

        double rc = 1.0, rs = 0.0;
        if (rotate) {
            double a = Math.toRadians(180f - yaw);
            rc = Math.cos(a);
            rs = Math.sin(a);
        }
        double edge = half - 8;

        for (Waypoint w : list) {
            if (!w.visible) continue;

            double worldDx = w.x + 0.5 - playerX;
            double worldDz = w.z + 0.5 - playerZ;

            double rawDx = worldDx * pxPerBlock;
            double rawDz = worldDz * pxPerBlock;
            double dx = rawDx * rc - rawDz * rs;
            double dz = rawDx * rs + rawDz * rc;

            boolean clamped = false;
            double m = circle ? Math.hypot(dx, dz) : Math.max(Math.abs(dx), Math.abs(dz));
            if (m > edge) {
                double k = edge / m;
                dx *= k;
                dz *= k;
                clamped = true;
            }

            int sx = cx + (int) Math.round(dx);
            int sy = cy + (int) Math.round(dz);
            int color = 0xFF000000 | (w.color & 0xFFFFFF);
            int r = clamped ? 3 : 4;

            drawDiamond(ctx, sx, sy, r + 1, 0xFF000000);
            drawDiamond(ctx, sx, sy, r, color);

            double dist = Math.hypot(worldDx, worldDz);
            String distText = formatDist(dist);

            if (!clamped) {
                drawText(ctx, mc, w.name, sx, sy - r - 10, color);
                drawText(ctx, mc, distText, sx, sy + r + 3, 0xFFCCCCCC);
            } else {
                int ty = dz > 0 ? sy - r - 10 : sy + r + 3;   // подпись внутрь карты
                drawText(ctx, mc, distText, sx, ty, 0xFFCCCCCC);
            }
        }
    }

    private static void drawDiamond(DrawContext ctx, int cx, int cy, int r, int color) {
        for (int i = -r; i <= r; i++) {
            int hw = r - Math.abs(i);
            ctx.fill(cx - hw, cy + i, cx + hw + 1, cy + i + 1, color);
        }
    }

    private static void drawText(DrawContext ctx, MinecraftClient mc, String s, int cx, int y, int color) {
        float sc = 0.75f;
        Matrix3x2fStack m = ctx.getMatrices();
        m.pushMatrix();
        m.translate(cx, y);
        m.scale(sc, sc);
        ctx.drawTextWithShadow(mc.textRenderer, s, -mc.textRenderer.getWidth(s) / 2, 0, color);
        m.popMatrix();
    }

    public static String formatDist(double d) {
        if (d < 1000) return Math.round(d) + "м";
        return String.format("%.1fкм", d / 1000.0);
    }
}
