package com.spothelper.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.spothelper.SpotHelperConfig;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Matrix4f;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.WorldChunk;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class BlockEspRenderer {

    public static void render(MatrixStack matrices, float tickDelta) {
        SpotHelperConfig cfg = SpotHelperConfig.INSTANCE;
        if (!cfg.isEspEnabled()) return;

        Set<Block> targets = cfg.getEspBlocks();
        if (targets.isEmpty()) return;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null) return;

        Vec3d cam = client.gameRenderer.getCamera().getPos();
        int range = Math.max(8, Math.min(128, cfg.espRange));
        BlockPos playerPos = client.player.getBlockPos();

        List<BlockPos> found = new ArrayList<>();
        int r2 = range * range;

        int minX = (playerPos.getX() - range) >> 4;
        int maxX = (playerPos.getX() + range) >> 4;
        int minZ = (playerPos.getZ() - range) >> 4;
        int maxZ = (playerPos.getZ() + range) >> 4;

        for (int cx = minX; cx <= maxX; cx++) {
            for (int cz = minZ; cz <= maxZ; cz++) {
                WorldChunk chunk = client.world.getChunk(cx, cz);
                if (chunk == null) continue;

                int x0 = cx << 4;
                int z0 = cz << 4;
                int yMin = 0;
                int yMax = client.world.getHeight();

                for (int x = 0; x < 16; x++) {
                    for (int z = 0; z < 16; z++) {
                        for (int y = yMin; y < yMax; y++) {
                            BlockPos pos = new BlockPos(x0 + x, y, z0 + z);
                            if (playerPos.getSquaredDistance(pos) > r2) continue;

                            BlockState state = chunk.getBlockState(pos);
                            if (targets.contains(state.getBlock())) {
                                found.add(pos.toImmutable());
                            }
                        }
                    }
                }
            }
        }

        if (found.isEmpty()) return;

        matrices.push();
        matrices.translate(-cam.x, -cam.y, -cam.z);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableTexture();
        RenderSystem.disableLighting();

        if (cfg.isEspThroughWalls()) {
            RenderSystem.disableDepthTest();
        } else {
            RenderSystem.enableDepthTest();
        }

        RenderSystem.lineWidth(cfg.lineWidth);

        Matrix4f matrix = matrices.peek().getModel();
        BufferBuilder buffer = Tessellator.getInstance().getBuffer();

        // --- Заливка ---
        if (cfg.espMode == 1 || cfg.espMode == 2) {
            float r = cfg.fillR / 255f;
            float g = cfg.fillG / 255f;
            float b = cfg.fillB / 255f;
            float a = cfg.fillA / 255f;

            buffer.begin(GL11.GL_QUADS, VertexFormats.POSITION_COLOR);
            for (BlockPos pos : found) {
                drawBoxFill(buffer, matrix, pos, r, g, b, a);
            }
            Tessellator.getInstance().draw();
        }

        // --- Контур ---
        if (cfg.espMode == 0 || cfg.espMode == 2) {
            float r = cfg.outlineR / 255f;
            float g = cfg.outlineG / 255f;
            float b = cfg.outlineB / 255f;
            float a = cfg.outlineA / 255f;

            buffer.begin(GL11.GL_LINES, VertexFormats.POSITION_COLOR);
            for (BlockPos pos : found) {
                drawBoxOutline(buffer, matrix, pos, r, g, b, a);
            }
            Tessellator.getInstance().draw();
        }

        RenderSystem.lineWidth(1.0f);
        RenderSystem.enableTexture();
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
        matrices.pop();
    }

    private static void drawBoxOutline(BufferBuilder buf, Matrix4f mat,
                                       BlockPos pos, float r, float g, float b, float a) {
        float x1 = pos.getX();
        float y1 = pos.getY();
        float z1 = pos.getZ();
        float x2 = x1 + 1;
        float y2 = y1 + 1;
        float z2 = z1 + 1;

        // низ
        line(buf, mat, x1, y1, z1, x2, y1, z1, r, g, b, a);
        line(buf, mat, x2, y1, z1, x2, y1, z2, r, g, b, a);
        line(buf, mat, x2, y1, z2, x1, y1, z2, r, g, b, a);
        line(buf, mat, x1, y1, z2, x1, y1, z1, r, g, b, a);
        // верх
        line(buf, mat, x1, y2, z1, x2, y2, z1, r, g, b, a);
        line(buf, mat, x2, y2, z1, x2, y2, z2, r, g, b, a);
        line(buf, mat, x2, y2, z2, x1, y2, z2, r, g, b, a);
        line(buf, mat, x1, y2, z2, x1, y2, z1, r, g, b, a);
        // стойки
        line(buf, mat, x1, y1, z1, x1, y2, z1, r, g, b, a);
        line(buf, mat, x2, y1, z1, x2, y2, z1, r, g, b, a);
        line(buf, mat, x2, y1, z2, x2, y2, z2, r, g, b, a);
        line(buf, mat, x1, y1, z2, x1, y2, z2, r, g, b, a);
    }

    private static void drawBoxFill(BufferBuilder buf, Matrix4f mat,
                                    BlockPos pos, float r, float g, float b, float a) {
        float x1 = pos.getX();
        float y1 = pos.getY();
        float z1 = pos.getZ();
        float x2 = x1 + 1;
        float y2 = y1 + 1;
        float z2 = z1 + 1;

        // -Y
        quad(buf, mat, x1, y1, z1, x2, y1, z1, x2, y1, z2, x1, y1, z2, r, g, b, a);
        // +Y
        quad(buf, mat, x1, y2, z1, x1, y2, z2, x2, y2, z2, x2, y2, z1, r, g, b, a);
        // -Z
        quad(buf, mat, x1, y1, z1, x1, y2, z1, x2, y2, z1, x2, y1, z1, r, g, b, a);
        // +Z
        quad(buf, mat, x1, y1, z2, x2, y1, z2, x2, y2, z2, x1, y2, z2, r, g, b, a);
        // -X
        quad(buf, mat, x1, y1, z1, x1, y1, z2, x1, y2, z2, x1, y2, z1, r, g, b, a);
        // +X
        quad(buf, mat, x2, y1, z1, x2, y2, z1, x2, y2, z2, x2, y1, z2, r, g, b, a);
    }

    private static void line(BufferBuilder buf, Matrix4f mat,
                             float x1, float y1, float z1, float x2, float y2, float z2,
                             float r, float g, float b, float a) {
        buf.vertex(mat, x1, y1, z1).color(r, g, b, a).next();
        buf.vertex(mat, x2, y2, z2).color(r, g, b, a).next();
    }

    private static void quad(BufferBuilder buf, Matrix4f mat,
                             float x1, float y1, float z1, float x2, float y2, float z2,
                             float x3, float y3, float z3, float x4, float y4, float z4,
                             float r, float g, float b, float a) {
        buf.vertex(mat, x1, y1, z1).color(r, g, b, a).next();
        buf.vertex(mat, x2, y2, z2).color(r, g, b, a).next();
        buf.vertex(mat, x3, y3, z3).color(r, g, b, a).next();
        buf.vertex(mat, x4, y4, z4).color(r, g, b, a).next();
    }
}