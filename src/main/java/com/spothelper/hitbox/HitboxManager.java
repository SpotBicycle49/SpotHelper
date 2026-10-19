package com.spothelper.hitbox;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.spothelper.config.SpotHelperConfig;
import com.spothelper.waypoints.WaypointLinesPipeline;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

/**
 * «Хитбоксы всегда»: в 1.21.11 у EntityRenderManager нет setRenderHitboxes(boolean), поэтому
 * хитбоксы рисуем сами теми же линиями, что метки и траектории (WaypointLinesPipeline):
 * только у игроков: белая рамка по боксу, красная рамка на уровне глаз, синий вектор взгляда.
 * С depth-тестом, как у ванильных, — стены хитбоксы закрывают.
 */
public final class HitboxManager {
    private HitboxManager() {}

    private static final double MAX_DIST = 64.0;
    private static final float WIDTH = 2.0f;
    private static final int BOX = 0xFFFFFFFF;
    private static final int EYE = 0xFFFF3030;
    private static final int LOOK = 0xFF3030FF;

    private static RenderPipeline pipeline;

    public static void tick(MinecraftClient mc) {
        // рисуется в render(); тик не нужен
    }

    private static synchronized RenderPipeline pipeline() {
        if (pipeline == null) {
            pipeline = RenderPipelines.register(
                    RenderPipeline.builder(RenderPipelines.RENDERTYPE_LINES_SNIPPET)
                            .withLocation(Identifier.of("spothelper", "pipeline/hitboxes"))
                            .withCull(false)
                            .withDepthWrite(false)
                            .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
                            .build());
        }
        return pipeline;
    }

    /** Вызывать из WorldRenderEvents.AFTER_ENTITIES. */
    public static void render(MatrixStack matrices, Camera camera) {
        if (SpotHelperConfig.INSTANCE.alwaysHitboxes != 1) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) return;

        float tp = mc.getRenderTickCounter().getTickProgress(true);
        Vec3d cam = camera.getCameraPos();
        Matrix4f mat = matrices.peek().getPositionMatrix();
        boolean firstPerson = mc.options.getPerspective().isFirstPerson();

        BufferBuilder buf = Tessellator.getInstance().begin(
                VertexFormat.DrawMode.LINES, VertexFormats.POSITION_COLOR_NORMAL_LINE_WIDTH);
        boolean any = false;

        for (Entity e : mc.world.getEntities()) {
            if (!(e instanceof PlayerEntity) || e.isRemoved() || e.isInvisible()) continue; // только видимые игроки
            if (e == mc.player && firstPerson) continue; // как в ванили: свой хитбокс только в виде от 3-го лица
            Vec3d lerped = e.getLerpedPos(tp);
            if (lerped.squaredDistanceTo(cam) > MAX_DIST * MAX_DIST) continue;

            // бокс лежит в позиции текущего тика — сдвигаем к интерполированной
            Box b = e.getBoundingBox().offset(lerped.x - e.getX(), lerped.y - e.getY(), lerped.z - e.getZ());
            box(buf, mat, b, cam, BOX);
            any = true;

            double eyeY = lerped.y + (e.getEyeY() - e.getY());
            if (eyeY > b.minY && eyeY < b.maxY) {
                Box eye = new Box(b.minX, eyeY, b.minZ, b.maxX, eyeY, b.maxZ);
                ring(buf, mat, eye, cam, EYE);
            }
            Vec3d eyePos = new Vec3d(lerped.x, eyeY, lerped.z);
            line(buf, mat, eyePos, eyePos.add(e.getRotationVec(tp).multiply(2.0)), cam, LOOK);
        }

        BuiltBuffer built = buf.endNullable();
        if (built == null || !any) {
            if (built != null) built.close();
            return;
        }
        WaypointLinesPipeline.draw(built, pipeline());
    }

    private static void box(BufferBuilder buf, Matrix4f m, Box b, Vec3d cam, int argb) {
        Box bottom = new Box(b.minX, b.minY, b.minZ, b.maxX, b.minY, b.maxZ);
        Box top = new Box(b.minX, b.maxY, b.minZ, b.maxX, b.maxY, b.maxZ);
        ring(buf, m, bottom, cam, argb);
        ring(buf, m, top, cam, argb);
        line(buf, m, new Vec3d(b.minX, b.minY, b.minZ), new Vec3d(b.minX, b.maxY, b.minZ), cam, argb);
        line(buf, m, new Vec3d(b.maxX, b.minY, b.minZ), new Vec3d(b.maxX, b.maxY, b.minZ), cam, argb);
        line(buf, m, new Vec3d(b.maxX, b.minY, b.maxZ), new Vec3d(b.maxX, b.maxY, b.maxZ), cam, argb);
        line(buf, m, new Vec3d(b.minX, b.minY, b.maxZ), new Vec3d(b.minX, b.maxY, b.maxZ), cam, argb);
    }

    /** Прямоугольник в плоскости XZ на высоте minY. */
    private static void ring(BufferBuilder buf, Matrix4f m, Box b, Vec3d cam, int argb) {
        Vec3d a = new Vec3d(b.minX, b.minY, b.minZ);
        Vec3d c = new Vec3d(b.maxX, b.minY, b.minZ);
        Vec3d d = new Vec3d(b.maxX, b.minY, b.maxZ);
        Vec3d e = new Vec3d(b.minX, b.minY, b.maxZ);
        line(buf, m, a, c, cam, argb);
        line(buf, m, c, d, cam, argb);
        line(buf, m, d, e, cam, argb);
        line(buf, m, e, a, cam, argb);
    }

    private static void line(BufferBuilder b, Matrix4f m, Vec3d a, Vec3d c, Vec3d cam, int argb) {
        float x1 = (float) (a.x - cam.x), y1 = (float) (a.y - cam.y), z1 = (float) (a.z - cam.z);
        float x2 = (float) (c.x - cam.x), y2 = (float) (c.y - cam.y), z2 = (float) (c.z - cam.z);
        float dx = x2 - x1, dy = y2 - y1, dz = z2 - z1;
        float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 1.0e-4f) return;
        dx /= len; dy /= len; dz /= len;
        b.vertex(m, x1, y1, z1).color(argb).normal(dx, dy, dz).lineWidth(WIDTH);
        b.vertex(m, x2, y2, z2).color(argb).normal(dx, dy, dz).lineWidth(WIDTH);
    }
}
