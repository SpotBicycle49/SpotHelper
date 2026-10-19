package com.spothelper.waypoints;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.spothelper.config.SpotHelperConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.Tessellator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.util.List;

/**
 * Метки в мире СКВОЗЬ СТЕНЫ: рамка-куб вокруг точки.
 *
 * Рисуется через WaypointLinesPipeline.draw со своим пайплайном без depth-теста.
 * Вызывать каждый кадр из события 3D-рендера, с матрицей, ещё не сдвинутой на камеру.
 */
public final class WaypointWorldRenderer {

    private static final float HALF = 0.3f;
    private static final float LINE_WIDTH = 3.0f;
    private static final int ALPHA = 0xC0;

    private static RenderPipeline pipeline;

    private WaypointWorldRenderer() {}

    /** Регистрирует пайплайн заранее (из onInitializeClient). Если шейдеры ещё не готовы — создастся лениво при первом кадре. */
    public static void init() {
        try {
            pipeline();
        } catch (Throwable ignored) {
            pipeline = null;
        }
    }

    private static synchronized RenderPipeline pipeline() {
        if (pipeline == null) {
            pipeline = RenderPipelines.register(
                    RenderPipeline.builder(RenderPipelines.RENDERTYPE_LINES_SNIPPET)
                            .withLocation(Identifier.of("spothelper", "pipeline/waypoint_through_walls"))
                            .withBlend(BlendFunction.ADDITIVE)
                            .withCull(false)
                            .withDepthWrite(false)
                            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                            .build());
        }
        return pipeline;
    }

    public static void render(MatrixStack matrices, Camera camera) {
        if (SpotHelperConfig.INSTANCE.showWaypointsInWorld != 1) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) return;

        List<Waypoint> waypoints = WaypointManager.current();
        if (waypoints.isEmpty()) return;

        Vec3d cam = camera.getCameraPos();
        BufferBuilder buffer = Tessellator.getInstance().begin(
                VertexFormat.DrawMode.LINES, VertexFormats.POSITION_COLOR_NORMAL_LINE_WIDTH);

        Matrix4f mat = matrices.peek().getPositionMatrix();

        // начало луча: чуть впереди камеры, чтобы линия «выходила» из центра экрана
        double yaw = Math.toRadians(camera.getYaw());
        double pitch = Math.toRadians(camera.getPitch());
        float sx = (float) (-Math.sin(yaw) * Math.cos(pitch) * 1.2);
        float sy = (float) (-Math.sin(pitch) * 1.2);
        float sz = (float) (Math.cos(yaw) * Math.cos(pitch) * 1.2);

        boolean any = false;
        for (Waypoint w : waypoints) {
            if (!w.visible) continue;
            float cx = (float) (w.x + 0.5 - cam.x);
            float cy = (float) (w.y + 1.0 - cam.y);
            float cz = (float) (w.z + 0.5 - cam.z);
            int argb = (ALPHA << 24) | (w.color & 0xFFFFFF);

            // рамка растёт с расстоянием, чтобы далёкую метку было видно
            float dist = (float) Math.sqrt(cx * cx + cy * cy + cz * cz);
            float half = HALF * Math.max(1f, dist / 25f);
            box(buffer, mat, cx - half, cy - half, cz - half, cx + half, cy + half, cz + half, argb);

            // луч от прицела до метки
            float dx = cx - sx;
            float dy = cy - sy;
            float dz = cz - sz;
            float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (len > 0.001f) {
                int rayColor = (0x80 << 24) | (w.color & 0xFFFFFF);
                line(buffer, mat, sx, sy, sz, cx, cy, cz, rayColor, dx / len, dy / len, dz / len);
            }
            any = true;
        }

        BuiltBuffer built = buffer.endNullable();
        if (built == null || !any) {
            if (built != null) built.close();
            return;
        }
        WaypointLinesPipeline.draw(built, pipeline());
    }

    private static void box(BufferBuilder b, Matrix4f m,
                            float x1, float y1, float z1, float x2, float y2, float z2, int c) {
        // низ
        line(b, m, x1, y1, z1, x2, y1, z1, c, 1, 0, 0);
        line(b, m, x2, y1, z1, x2, y1, z2, c, 0, 0, 1);
        line(b, m, x2, y1, z2, x1, y1, z2, c, -1, 0, 0);
        line(b, m, x1, y1, z2, x1, y1, z1, c, 0, 0, -1);
        // верх
        line(b, m, x1, y2, z1, x2, y2, z1, c, 1, 0, 0);
        line(b, m, x2, y2, z1, x2, y2, z2, c, 0, 0, 1);
        line(b, m, x2, y2, z2, x1, y2, z2, c, -1, 0, 0);
        line(b, m, x1, y2, z2, x1, y2, z1, c, 0, 0, -1);
        // стойки
        line(b, m, x1, y1, z1, x1, y2, z1, c, 0, 1, 0);
        line(b, m, x2, y1, z1, x2, y2, z1, c, 0, 1, 0);
        line(b, m, x2, y1, z2, x2, y2, z2, c, 0, 1, 0);
        line(b, m, x1, y1, z2, x1, y2, z2, c, 0, 1, 0);
    }

    private static void line(BufferBuilder b, Matrix4f m,
                             float x1, float y1, float z1, float x2, float y2, float z2,
                             int c, float nx, float ny, float nz) {
        b.vertex(m, x1, y1, z1).color(c).normal(nx, ny, nz).lineWidth(LINE_WIDTH);
        b.vertex(m, x2, y2, z2).color(c).normal(nx, ny, nz).lineWidth(LINE_WIDTH);
    }
}