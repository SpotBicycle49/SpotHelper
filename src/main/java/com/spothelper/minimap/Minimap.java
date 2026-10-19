package com.spothelper.minimap;

import com.spothelper.config.SpotHelperConfig;
import com.spothelper.waypoints.WaypointRenderer;
import com.spothelper.minimap.MobSprites;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.passive.PassiveEntity;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;
import net.minecraft.client.color.world.BiomeColors;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.WorldChunk;
import org.joml.Matrix3x2fStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class Minimap {

    private static final float SQRT2 = 1.41421356f;

    // Сколько горизонтальных полос используем для круга.
    // Больше = точнее круг, но чуть дороже по GPU.
    private static final int ROUND_BANDS = 48;

    private static int texSize = 128;
    private static NativeImageBackedTexture texture;
    private static Identifier textureId;

    private static double texCenterX = 0;
    private static double texCenterZ = 0;
    private static boolean texInitialized = false;

    private static int builtRadius = -1;

    private static final Map<Block, Integer> blockColorCache = new HashMap<>();
    private static World lastWorld = null;

    private static final int CAT_PLAYER = 0, CAT_HOSTILE = 1, CAT_FRIENDLY = 2, CAT_ITEM = 3, CAT_OTHER = 4;

    private static final Set<String> BAD_ICON_TYPES = new HashSet<>();

    public static void render(DrawContext context, float tickDelta) {
        SpotHelperConfig cfg = SpotHelperConfig.INSTANCE;
        if (!cfg.isMinimapEnabled()) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) return;
        if (mc.options.hudHidden) return;

        int radius = cfg.realMinimapRadius();
        int sizeOnScreen = Math.max(40, cfg.realMinimapSize());
        int wantedTexSize = cfg.realMinimapTexSize();
        boolean circle = cfg.isMinimapCircle();

        boolean texResized = ensureTexture(mc, wantedTexSize);

        double playerX = mc.player.getX();
        double playerZ = mc.player.getZ();
        float yaw = mc.player.getYaw();

        // Ребилд текстуры только когда игрок отошёл далеко от центра
        double updateThreshold = Math.max(1.0, radius * 0.3);
        boolean needRebuild = texResized
                || !texInitialized
                || Math.abs(playerX - texCenterX) > updateThreshold
                || Math.abs(playerZ - texCenterZ) > updateThreshold
                || builtRadius != radius;

        if (needRebuild) {
            texCenterX = playerX;
            texCenterZ = playerZ;
            texInitialized = true;
            builtRadius = radius;
            updateTexture(mc, radius);
        }

        int margin = 6;
        int sw = mc.getWindow().getScaledWidth();
        int sh = mc.getWindow().getScaledHeight();

        int x, y;
        switch (cfg.minimapCorner) {
            case 0:  x = margin;                       y = margin;                     break;
            case 2:  x = margin;                       y = sh - sizeOnScreen - margin; break;
            case 3:  x = sw - sizeOnScreen - margin;   y = sh - sizeOnScreen - margin; break;
            default: x = sw - sizeOnScreen - margin;   y = margin;                     break;
        }

        // ---- Рамка ----
        int borderMode = cfg.minimapBorder;
        if (borderMode != 0) {
            // мягкая тень
            context.fill(x - 4, y - 4, x + sizeOnScreen + 4, y + sizeOnScreen + 4, 0x55000000);
            if (borderMode == 2) {
                context.fill(x - 3, y - 3, x + sizeOnScreen + 3, y + sizeOnScreen + 3, 0xFF1F5E99);
                context.fill(x - 2, y - 2, x + sizeOnScreen + 2, y + sizeOnScreen + 2, 0xFF3FA6FF);
                context.fill(x - 1, y - 1, x + sizeOnScreen + 1, y + sizeOnScreen + 1, 0xFF0B0B0B);
            } else {
                context.fill(x - 3, y - 3, x + sizeOnScreen + 3, y + sizeOnScreen + 3, 0xFF111111);
                context.fill(x - 2, y - 2, x + sizeOnScreen + 2, y + sizeOnScreen + 2, 0xFF6A6A6A);
                context.fill(x - 1, y - 1, x + sizeOnScreen + 1, y + sizeOnScreen + 1, 0xFF1E1E1E);
            }
        }

        int cx = x + sizeOnScreen / 2;
        int cy = y + sizeOnScreen / 2;

        double pixelsPerBlock = (double) sizeOnScreen / (radius * 2.0);
        double shiftX = (texCenterX - playerX) * pixelsPerBlock;
        double shiftZ = (texCenterZ - playerZ) * pixelsPerBlock;

        boolean rotate = cfg.isMinimapRotate();

        // ---- Содержимое ----
        Runnable drawContent = () -> {
            // Карта — с матрицей вращения
            if (textureId != null) {
                int alpha = MathHelper.clamp(cfg.minimapOpacity, 20, 255);
                int tint = (alpha << 24) | 0xFFFFFF;

                Matrix3x2fStack m = context.getMatrices();
                m.pushMatrix();
                if (rotate) {
                    m.translate(cx, cy);
                    m.rotate((float) Math.toRadians(180f - yaw));
                    m.translate(-cx, -cy);
                }

                // Текстура покрывает radius*2*SQRT2 блоков (с запасом на поворот),
                // поэтому на экране она в SQRT2 раз больше окна карты.
                int mapSize = (int) Math.round(sizeOnScreen * (double) SQRT2);
                int drawX = (int) Math.round(cx + shiftX - mapSize / 2.0);
                int drawY = (int) Math.round(cy + shiftZ - mapSize / 2.0);

                // Длинная перегрузка: берём ВСЮ текстуру (region = texSize x texSize)
                // и растягиваем на mapSize x mapSize пикселей.
                context.drawTexture(
                        RenderPipelines.GUI_TEXTURED,
                        textureId,
                        drawX, drawY,
                        0f, 0f,
                        mapSize, mapSize,
                        texSize, texSize,
                        texSize, texSize,
                        tint
                );
                m.popMatrix();
            }

            // Иконки — без вращающей матрицы, поворот координат вручную
            drawRadar(context, mc, cfg, x, y, sizeOnScreen, radius, playerX, playerZ, yaw, rotate);
            WaypointRenderer.render(context, mc, x, y, sizeOnScreen, radius, playerX, playerZ, yaw, rotate, circle);
        };

        if (circle) {
            renderRoundBands(context, x, y, sizeOnScreen, drawContent);
        } else {
            context.enableScissor(x, y, x + sizeOnScreen, y + sizeOnScreen);
            drawContent.run();
            context.disableScissor();
        }

        // ---- Внутренняя подсветка краёв и компас ----
        if (!circle) {
            context.fill(x, y, x + sizeOnScreen, y + 1, 0x40000000);
            context.fill(x, y, x + 1, y + sizeOnScreen, 0x40000000);
            context.fill(x, y + sizeOnScreen - 1, x + sizeOnScreen, y + sizeOnScreen, 0x25FFFFFF);
            context.fill(x + sizeOnScreen - 1, y, x + sizeOnScreen, y + sizeOnScreen, 0x25FFFFFF);
        }
        drawCompass(context, mc, cx, cy, sizeOnScreen, circle, rotate, yaw);

        // ---- Стрелка игрока (вне scissor) ----
        Matrix3x2fStack mm = context.getMatrices();
        mm.pushMatrix();
        if (!rotate) {
            mm.translate(cx, cy);
            mm.rotate((float) Math.toRadians(yaw + 180f));
            mm.translate(-cx, -cy);
        }
        drawArrow(context, cx, cy);
        mm.popMatrix();

        // ---- Текст под картой ----
        int textY = y + sizeOnScreen + 3;
        if (cfg.isMinimapCoords()) {
            BlockPos pp = mc.player.getBlockPos();
            String s = pp.getX() + " " + pp.getY() + " " + pp.getZ();
            context.drawTextWithShadow(mc.textRenderer, s,
                    x + (sizeOnScreen - mc.textRenderer.getWidth(s)) / 2, textY, 0xFFFFFFFF);
            textY += 10;
        }
        if (cfg.isMinimapBiome()) {
            String b = getBiomeName(mc);
            context.drawTextWithShadow(mc.textRenderer, b,
                    x + (sizeOnScreen - mc.textRenderer.getWidth(b)) / 2, textY, 0xFFAAFFAA);
            textY += 10;
        }
        if (cfg.isMinimapTime()) {
            long time = mc.world.getTimeOfDay() % 24000L;
            String t = (time / 1000L) + ":00";
            context.drawTextWithShadow(mc.textRenderer, t,
                    x + (sizeOnScreen - mc.textRenderer.getWidth(t)) / 2, textY, 0xFFFFFFAA);
        }
    }

    // ============================================================
    //   КРУГЛЫЙ РЕНДЕР ЧЕРЕЗ ПОЛОСЫ + SCISSOR (как в JustMap)
    // ============================================================

    private static void renderRoundBands(DrawContext context, int x, int y, int size,
                                         Runnable drawContent) {
        int cx = x + size / 2;
        int cy = y + size / 2;
        int bands = Math.min(size, ROUND_BANDS);
        double bandHeight = (double) size / bands;
        double radius = size / 2.0;

        for (int i = 0; i < bands; i++) {
            double top = y + i * bandHeight;
            double bottom = top + bandHeight;

            // Расстояние от центра по вертикали
            double dy;
            if ((top - cy) * (bottom - cy) <= 0.0) {
                dy = 0.0;   // полоса проходит через центр
            } else {
                dy = Math.min(Math.abs(top - cy), Math.abs(bottom - cy));
            }

            double t = 1.0 - (dy * dy) / (radius * radius);
            if (t <= 0.0) continue;
            double halfWidth = radius * Math.sqrt(t);

            int x1 = (int) Math.floor(cx - halfWidth);
            int y1 = (int) Math.floor(top);
            int x2 = (int) Math.ceil(cx + halfWidth);
            int y2 = (int) Math.ceil(bottom);

            context.enableScissor(x1, y1, x2, y2);
            drawContent.run();
            context.disableScissor();
        }
    }

    // ============================================================
    //   РАДАР — иконки прикреплены к мировым координатам
    // ============================================================

    private static void drawRadar(DrawContext context, MinecraftClient mc, SpotHelperConfig cfg,
                                  int x, int y, int size, int radius,
                                  double playerX, double playerZ,
                                  float yaw, boolean rotate) {
        int half = size / 2;
        int cx = x + half;
        int cy = y + half;

        double playerY = mc.player.getY();
        double pxPerBlock = (double) size / (radius * 2.0);

        // Матрица поворота координат (та же, что у карты)
        double rotCos = 1.0, rotSin = 0.0;
        if (rotate) {
            double angle = Math.toRadians(180f - yaw);
            rotCos = Math.cos(angle);
            rotSin = Math.sin(angle);
        }

        boolean tabHeld = mc.options != null && mc.options.playerListKey.isPressed();

        List<Entity> found = new ArrayList<>();
        double reachSq = (radius * SQRT2) * (radius * SQRT2);
        for (Entity e : mc.world.getEntities()) {
            if (e == mc.player) continue;

            double dx = e.getX() - playerX;
            double dy = e.getY() - playerY;
            double dz = e.getZ() - playerZ;
            if (dx * dx + dy * dy + dz * dz > reachSq) continue;

            int cat = radarCategory(e, cfg);
            if (cat < 0) continue;
            found.add(e);
        }

        found.sort((a, b) -> {
            double da = (a.getX() - playerX) * (a.getX() - playerX) + (a.getZ() - playerZ) * (a.getZ() - playerZ);
            double db = (b.getX() - playerX) * (b.getX() - playerX) + (b.getZ() - playerZ) * (b.getZ() - playerZ);
            return Double.compare(db, da);
        });

        for (Entity e : found) {
            int cat = radarCategory(e, cfg);
            if (cat < 0) continue;

            String typeId = (cat == CAT_ITEM) ? null : Registries.ENTITY_TYPE.getId(e.getType()).toString();
            int iconSize = Math.max(8, cfg.getRadarSizeFor(typeId, cat));

            boolean wantName = shown(cfg.getRadarNameMode(cat), tabHeld);
            int color = catColor(cfg, cat);

            // Мировые дельты → пиксели
            double rawDx = (e.getX() - playerX) * pxPerBlock;
            double rawDz = (e.getZ() - playerZ) * pxPerBlock;

            // Поворот координат иконки
            double dx = rawDx * rotCos - rawDz * rotSin;
            double dz = rawDx * rotSin + rawDz * rotCos;

            int sx = cx + (int) Math.round(dx);
            int sy = cy + (int) Math.round(dz);

            // Обрезка по границам квадрата
            int marginPx = iconSize / 2 + 12;
            if (sx < x + marginPx || sx > x + size - marginPx) continue;
            if (sy < y + marginPx || sy > y + size - marginPx) continue;

            boolean drew = false;

            if (e instanceof ItemEntity itemEntity) {
                ItemStack stack = itemEntity.getStack();
                if (!stack.isEmpty()) {
                    try {
                        context.drawItem(stack, sx - iconSize / 2, sy - iconSize / 2);
                        drew = true;
                    } catch (Throwable ignored) {}
                }
            }

            if (!drew && e instanceof LivingEntity living) {
                drew = drawMobFace(context, living, sx, sy, iconSize);
            }

            if (!drew && cat == CAT_OTHER && !(e instanceof ItemEntity)) {
                drew = drawCreativeIcon(context, e, sx, sy, iconSize);
            }

            if (!drew) {
                drawSquare(context, sx, sy, iconSize, color, cfg.radarOutline);
            }

            boolean heightOn = cfg.isShowEntityHeight() && heightWanted(cfg, cat, tabHeld);

            // Высота — сразу после имени: «Паук (у -64)»
            if (wantName) {
                drawName(context, mc, e, sx, sy, iconSize, cfg.getRadarNameScale(cat), color,
                        heightOn ? cfg : null, playerY);
            }

            // Если имя не показывается — высота остаётся под иконкой
            if (heightOn && !wantName) {
                drawHeight(context, mc, cfg, e, sx, sy, iconSize, cfg.getRadarNameScale(cat), playerY);
            }
        }
    }

    // ============================================================
    //   2D-МОРДА МОБА
    // ============================================================

    /**
     * Рисуем иконку сущности:
     *   • игрок  -> голова из скина (8x8 из 64x64, слои face + hat)
     *   • моб    -> наш 16x16 спрайт из assets/spothelper/textures/mob/
     */
    private static boolean drawMobFace(DrawContext ctx, LivingEntity living,
                                       int sx, int sy, int iconSize) {
        // ── Игрок: рисуем голову из скина ────────────────────
        if (living instanceof AbstractClientPlayerEntity player) {
            try {
                // getSkinTextures() на AbstractClientPlayerEntity может не быть в данном маппинге —
                // достаём текстуру через PlayerListEntry (надёжный путь в 1.21.1)
                Identifier skin = null;
                MinecraftClient _mc = MinecraftClient.getInstance();
                if (_mc.getNetworkHandler() != null) {
                    var entry = _mc.getNetworkHandler().getPlayerListEntry(player.getUuid());
                    if (entry != null) skin = entry.getSkinTextures().body().texturePath();
                }
                if (skin == null) return false;

                Matrix3x2fStack m = ctx.getMatrices();
                m.pushMatrix();
                m.translate(sx - iconSize / 2f, sy - iconSize / 2f);
                // Порядок аргументов: x, y, u, v, ширина и высота НА ЭКРАНЕ, ширина и высота области
                // в текстуре, размер всей текстуры, цвет. (Раньше u/v и размеры были перепутаны местами,
                // из-за чего рисовался кусочек скина 8x8 вместо лица.)
                // Лицо: область (8, 8) размером 8x8 в текстуре 64x64
                ctx.drawTexture(RenderPipelines.GUI_TEXTURED, skin,
                        0, 0,
                        8f, 8f,
                        iconSize, iconSize,
                        8, 8,
                        64, 64,
                        0xFFFFFFFF);
                // Шапка (второй слой): область (40, 8) размером 8x8
                ctx.drawTexture(RenderPipelines.GUI_TEXTURED, skin,
                        0, 0,
                        40f, 8f,
                        iconSize, iconSize,
                        8, 8,
                        64, 64,
                        0xFFFFFFFF);
                m.popMatrix();
                return true;
            } catch (Throwable ignored) {}
            return false;
        }

        // ── Моб: наш готовый 16x16 спрайт ────────────────────
        try {
            String id = Registries.ENTITY_TYPE.getId(living.getType()).toString();
            if (BAD_ICON_TYPES.contains(id)) return false;

            Identifier sprite = MobSprites.get(id);
            if (sprite == null) return false;

            Matrix3x2fStack m = ctx.getMatrices();
            m.pushMatrix();
            m.translate(sx - iconSize / 2f, sy - iconSize / 2f);
            float scale = iconSize / 16f;
            m.scale(scale, scale);

            ctx.drawTexture(RenderPipelines.GUI_TEXTURED, sprite,
                    0, 0,
                    0f, 0f,        // u, v (весь спрайт)
                    16, 16,        // регион
                    16, 16,        // размер текстуры
                    0xFFFFFFFF);

            m.popMatrix();
            return true;
        } catch (Throwable t) {
            BAD_ICON_TYPES.add(Registries.ENTITY_TYPE.getId(living.getType()).toString());
            return false;
        }
    }

    /** Иконка сущности без своего спрайта — как в креативном меню (стойка для брони, вагонетка, яйцо призыва…). */
    private static boolean drawCreativeIcon(DrawContext ctx, Entity e, int sx, int sy, int iconSize) {
        try {
            String id = Registries.ENTITY_TYPE.getId(e.getType()).toString();
            ItemStack stack = EntityIcons.stackFor(id);
            if (stack.isEmpty()) return false;
            Matrix3x2fStack m = ctx.getMatrices();
            m.pushMatrix();
            m.translate(sx - iconSize / 2f, sy - iconSize / 2f);
            float scale = iconSize / 16f;
            m.scale(scale, scale);
            ctx.drawItem(stack, 0, 0);
            m.popMatrix();
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    // ============================================================
    //   Утилиты
    // ============================================================

    private static boolean shown(int mode, boolean tabHeld) {
        return mode == 0 || (mode == 2 && tabHeld);
    }

    private static void drawSquare(DrawContext ctx, int sx, int sy, int s, int color, boolean outline) {
        int half = Math.max(2, s / 2);
        int x1 = sx - half;
        int y1 = sy - half;
        int x2 = sx + half;
        int y2 = sy + half;

        if (outline) {
            ctx.fill(x1 - 1, y1 - 1, x2 + 1, y1, 0xFF000000);
            ctx.fill(x1 - 1, y2, x2 + 1, y2 + 1, 0xFF000000);
            ctx.fill(x1 - 1, y1, x1, y2, 0xFF000000);
            ctx.fill(x2, y1, x2 + 1, y2, 0xFF000000);
        }
        ctx.fill(x1, y1, x2, y2, color);
    }

    /** Режим показа высоты: 0 — всегда, 1 — только у враждебных, 2 — пока зажат Tab. */
    private static boolean heightWanted(SpotHelperConfig cfg, int cat, boolean tabHeld) {
        switch (cfg.entityHeightMode) {
            case 1: return cat == CAT_HOSTILE;
            case 2: return tabHeld;
            default: return true;
        }
    }

    /**
     * Подпись высоты: «Y: 64» или «64».
     * Режим цвета 1: зелёный — выше игрока, красный — ниже, белый — та же высота (±2 блока).
     * Режим цвета 0: всегда белый.
     */
    private static void drawHeight(DrawContext ctx, MinecraftClient mc, SpotHelperConfig cfg, Entity e,
                                   int sx, int sy, int s, int scalePercent, double playerY) {
        int blockY = (int) Math.floor(e.getY());
        String text = cfg.entityHeightFormat == 1 ? "Y: " + blockY : String.valueOf(blockY);

        int color = 0xFFFFFFFF;
        if (cfg.entityHeightColorMode == 1) {
            double diff = e.getY() - playerY;
            if (diff > 2.0) color = 0xFF55FF55;
            else if (diff < -2.0) color = 0xFFFF5555;
        }

        float sc = 0.7f * scalePercent / 100f;
        int w = (int) (mc.textRenderer.getWidth(text) * sc);
        int drawX = sx - w / 2;
        int drawY = sy + s / 2 + 1;

        Matrix3x2fStack m = ctx.getMatrices();
        m.pushMatrix();
        m.translate(drawX, drawY);
        m.scale(sc, sc);
        ctx.drawText(mc.textRenderer, text, 1, 1, 0xAA000000, false);
        ctx.drawText(mc.textRenderer, text, 0, 0, color, false);
        m.popMatrix();
    }

    private static void drawName(DrawContext ctx, MinecraftClient mc, Entity e, int sx, int sy,
                                 int s, int scalePercent, int color,
                                 SpotHelperConfig heightCfg, double playerY) {
        String text;
        if (e instanceof ItemEntity itemEntity) {
            ItemStack st = itemEntity.getStack();
            text = st.getName().getString() + (st.getCount() > 1 ? " x" + st.getCount() : "");
        } else {
            text = e.getName().getString();
        }
        if (text == null || text.isEmpty()) return;

        // высота сразу после имени: « (у -64)»
        String hText = null;
        int hColor = 0xFFFFFFFF;
        if (heightCfg != null) {
            int blockY = (int) Math.floor(e.getY());
            hText = heightCfg.entityHeightFormat == 1 ? " (у " + blockY + ")" : " (" + blockY + ")";
            if (heightCfg.entityHeightColorMode == 1) {
                double diff = e.getY() - playerY;
                if (diff > 2.0) hColor = 0xFF55FF55;
                else if (diff < -2.0) hColor = 0xFFFF5555;
            }
        }

        float sc = 0.75f * scalePercent / 100f;
        int nameW = mc.textRenderer.getWidth(text);
        int hW = hText == null ? 0 : mc.textRenderer.getWidth(hText);
        int w = (int) ((nameW + hW) * sc);
        int drawX = sx - w / 2;
        int drawY = sy - s / 2 - 10;

        Matrix3x2fStack m = ctx.getMatrices();
        m.pushMatrix();
        m.translate(drawX, drawY);
        m.scale(sc, sc);
        ctx.drawText(mc.textRenderer, text, 1, 1, 0xAA000000, false);
        ctx.drawText(mc.textRenderer, text, 0, 0, color | 0xFF000000, false);
        if (hText != null) {
            ctx.drawText(mc.textRenderer, hText, nameW + 1, 1, 0xAA000000, false);
            ctx.drawText(mc.textRenderer, hText, nameW, 0, hColor, false);
        }
        m.popMatrix();
    }

    /**
     * Категория сущности для радара или -1, если её не нужно показывать.
     * Учитывает включённость категории и её фильтр (режим «Только» / «Скрывать»).
     */
    private static int radarCategory(Entity e, SpotHelperConfig cfg) {
        if (e instanceof PlayerEntity) return cfg.isMinimapPlayers() && !e.isInvisible() ? CAT_PLAYER : -1; // невидимых не показываем

        if (e instanceof ItemEntity itemEntity) {
            if (!cfg.isMinimapItems()) return -1;
            String itemId = Registries.ITEM.getId(itemEntity.getStack().getItem()).toString();
            return cfg.passesFilter(itemId, cfg.filterModeItems, cfg.filterItems) ? CAT_ITEM : -1;
        }

        String id = EntityCategories.idOf(e.getType());
        switch (EntityCategories.of(e.getType())) {
            case HOSTILE:
                if (!cfg.isMinimapHostile()) return -1;
                return cfg.passesFilter(id, cfg.filterModeHostile, cfg.filterHostile) ? CAT_HOSTILE : -1;
            case FRIENDLY:
                if (!cfg.isMinimapFriendly()) return -1;
                return cfg.passesFilter(id, cfg.filterModeFriendly, cfg.filterFriendly) ? CAT_FRIENDLY : -1;
            default:
                if (!cfg.isMinimapOther()) return -1;
                return cfg.passesFilter(id, cfg.filterModeOther, cfg.filterOther) ? CAT_OTHER : -1;
        }
    }

    private static int catColor(SpotHelperConfig cfg, int cat) {
        switch (cat) {
            case CAT_PLAYER: return cfg.mmColorPlayer;
            case CAT_HOSTILE: return cfg.mmColorHostile;
            case CAT_FRIENDLY: return cfg.mmColorFriendly;
            case CAT_ITEM: return cfg.mmColorItem;
            default: return cfg.mmColorOther;
        }
    }

    // ============================================================
    //   БИОМ
    // ============================================================

    private static String getBiomeName(MinecraftClient mc) {
        try {
            Biome biome = mc.world.getBiome(mc.player.getBlockPos()).value();
            Identifier id = mc.world.getRegistryManager()
                    .getOrThrow(RegistryKeys.BIOME)
                    .getId(biome);
            String path = id == null ? "?" : id.getPath();
            String ru = biomeNameRu(path);
            return ru != null ? ru : path;
        } catch (Throwable t) {
            return "?";
        }
    }

    private static String biomeNameRu(String id) {
        if (id == null) return null;
        switch (id) {
            case "plains": return "Равнины";
            case "forest": return "Лес";
            case "birch_forest": return "Берёзовый лес";
            case "dark_forest": return "Тёмный лес";
            case "taiga": return "Тайга";
            case "desert": return "Пустыня";
            case "savanna": return "Саванна";
            case "jungle": return "Джунгли";
            case "swamp": return "Болото";
            case "beach": return "Пляж";
            case "ocean": return "Океан";
            case "river": return "Река";
            case "snowy_plains": return "Снежные равнины";
            case "ice_spikes": return "Ледяные шпили";
            case "mushroom_fields": return "Грибные поля";
            case "nether_wastes": return "Пустоши Ада";
            case "the_end": return "Энд";
            default: return null;
        }
    }

    // ============================================================
    //   ТЕКСТУРА КАРТЫ
    // ============================================================

    private static boolean ensureTexture(MinecraftClient mc, int wantedSize) {
        if (texture != null && texSize == wantedSize) return false;
        if (texture != null && textureId != null) {
            try { mc.getTextureManager().destroyTexture(textureId); } catch (Throwable ignored) {}
            texture = null;
            textureId = null;
        }
        texSize = wantedSize;
        NativeImage img = new NativeImage(NativeImage.Format.RGBA, texSize, texSize, true);
        for (int y = 0; y < texSize; y++) {
            for (int x = 0; x < texSize; x++) {
                img.setColor(x, y, 0);
            }
        }
        texture = new NativeImageBackedTexture(() -> "spothelper_minimap", img);
        textureId = Identifier.of("spothelper", "minimap");
        mc.getTextureManager().registerTexture(textureId, texture);
        texInitialized = false;
        return true;
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  updateTexture — детальная карта в стиле Xaero
    //  ключевые улучшения:
    //    1. Цвета биомов (трава, листья, вода)
    //    2. Тень/свет по высоте (рельеф)
    //    3. Глубина воды
    //    4. Полная таблица цветов блоков
    //    5. Тёмный цвет для незагруженных чанков
    // ─────────────────────────────────────────────────────────────────────────

    private static void updateTexture(MinecraftClient mc, int radius) {
        if (texture == null) return;
        NativeImage img = texture.getImage();
        if (img == null) return;
        World world = mc.world;
        if (lastWorld != world) { blockColorCache.clear(); lastWorld = world; }

        SpotHelperConfig cfg = SpotHelperConfig.INSTANCE;
        float waterBase = cfg.getMinimapWaterOpacity() / 100f;
        int waterDepthCap = cfg.getMinimapWaterDepth();

        int size = texSize;
        double worldWidth = radius * 2.0 * SQRT2;
        double bpt = worldWidth / size;            // blocks per texel
        double half = worldWidth / 2.0;

        int[] heights = new int[size * size];
        int[] colors  = new int[size * size];
        boolean[] water = new boolean[size * size];
        float[] amp = new float[size * size];      // амплитуда «зерна» (имитация текстуры блока)

        BlockPos.Mutable pos = new BlockPos.Mutable();

        // ── Проход 1: высоты и базовые цвета ──────────────────────────────
        for (int tz = 0; tz < size; tz++) {
            for (int tx = 0; tx < size; tx++) {
                int wx = (int) Math.floor(texCenterX - half + tx * bpt);
                int wz = (int) Math.floor(texCenterZ - half + tz * bpt);
                int idx = tz * size + tx;

                if (!world.isChunkLoaded(wx >> 4, wz >> 4)) {
                    heights[idx] = -1;
                    colors[idx]  = 0x1A1A1A;
                    continue;
                }

                int y;
                try { y = world.getTopY(Heightmap.Type.WORLD_SURFACE, wx, wz) - 1; }
                catch (Throwable t) { y = 0; }
                if (y < 0) y = 0;
                heights[idx] = y;

                pos.set(wx, y, wz);
                BlockState state = world.getBlockState(pos);
                Block block = state.getBlock();

                // Вода: просвечивает дно, чем глубже — тем плотнее и темнее
                if (block == Blocks.WATER || block == Blocks.BUBBLE_COLUMN) {
                    int waterColor;
                    try { waterColor = BiomeColors.getWaterColor(world, pos) & 0xFFFFFF; }
                    catch (Throwable t) { waterColor = 0x3F76E4; }

                    int bottom = y - 1;
                    while (bottom > world.getBottomY() && (y - bottom) < waterDepthCap) {
                        Block bb = world.getBlockState(pos.set(wx, bottom, wz)).getBlock();
                        if (bb != Blocks.WATER && bb != Blocks.BUBBLE_COLUMN
                                && bb != Blocks.KELP && bb != Blocks.KELP_PLANT
                                && bb != Blocks.SEAGRASS && bb != Blocks.TALL_SEAGRASS) break;
                        bottom--;
                    }
                    int depth = Math.max(1, y - bottom);

                    pos.set(wx, bottom, wz);
                    int floorRgb = surfaceRgb(world, pos, world.getBlockState(pos));

                    float a = waterBase + (1f - waterBase) * Math.min(1f, depth / 14f) * 0.85f;
                    int mixed = blend(floorRgb, waterColor, a);
                    float dark = 1f - Math.min(depth, 24) / 24f * 0.40f;
                    colors[idx] = mul(mixed, dark);
                    water[idx] = true;
                    amp[idx] = 0.03f;
                    continue;
                }

                // Стекло и стеклянные панели: просвечиваем до пола под ними
                int gk = glassKind(block);
                if (gk != 0) {
                    int by = y - 1;
                    int steps = 0;
                    while (by > world.getBottomY() && steps < 64) {
                        BlockState bs = world.getBlockState(pos.set(wx, by, wz));
                        if (glassKind(bs.getBlock()) == 0 && !bs.isAir()) break;
                        by--;
                        steps++;
                    }
                    pos.set(wx, by, wz);
                    BlockState floor = world.getBlockState(pos);
                    int floorRgb;
                    if (floor.getBlock() == Blocks.WATER) {
                        try { floorRgb = BiomeColors.getWaterColor(world, pos) & 0xFFFFFF; }
                        catch (Throwable t) { floorRgb = 0x3F76E4; }
                    } else {
                        floorRgb = surfaceRgb(world, pos, floor);
                    }

                    boolean stained = Registries.BLOCK.getId(block).getPath().contains("stained");
                    int tint;
                    if (stained) {
                        try { tint = block.getDefaultMapColor().color & 0xFFFFFF; }
                        catch (Throwable t) { tint = 0x9AA0A8; }
                    } else {
                        tint = (gk == 1) ? GLASS_TINT : PANE_TINT;
                    }

                    float a;
                    if (gk == 1) {
                        a = stained ? GLASS_ALPHA_STAINED : GLASS_ALPHA;
                    } else {
                        // панель: «решётка» через клетку, чтобы отличалась от сплошного стекла
                        boolean line = ((wx + wz) & 1) == 0;
                        a = stained ? (line ? PANE_ALPHA_STAINED : PANE_ALPHA_STAINED * 0.4f)
                                    : (line ? PANE_ALPHA : PANE_ALPHA * 0.35f);
                    }
                    heights[idx] = by;
                    colors[idx] = blend(floorRgb, tint, a);
                    amp[idx] = 0.08f;
                    continue;
                }

                colors[idx] = surfaceRgb(world, pos, state);
                amp[idx] = (isGrassLike(block) || isFoliage(block)) ? 0.16f : 0.08f;
            }
        }

        // ── Проход 2: рельеф (свет с северо-запада) + зерно + запись ──────
        for (int tz = 0; tz < size; tz++) {
            for (int tx = 0; tx < size; tx++) {
                int idx = tz * size + tx;
                int rgb = colors[idx];
                int h = heights[idx];

                if (h < 0) {
                    img.setColor(tx, tz, abgr(0xFF000000 | rgb));
                    continue;
                }

                float f = 1f;
                if (!water[idx]) {
                    int hN = (tz > 0 && heights[idx - size] >= 0) ? heights[idx - size] : h;
                    int hW = (tx > 0 && heights[idx - 1]    >= 0) ? heights[idx - 1]    : h;
                    float slope = (hN - h) + (hW - h) * 0.6f;
                    f -= MathHelper.clamp(slope * 0.065f, -0.30f, 0.42f);
                    f += MathHelper.clamp((h - 64) * 0.0012f, -0.06f, 0.10f);
                }

                int wx = (int) Math.floor(texCenterX - half + tx * bpt);
                int wz = (int) Math.floor(texCenterZ - half + tz * bpt);
                float n = hash01(wx, wz) - 0.5f;          // -0.5 .. +0.5
                f *= 1f + n * 2f * amp[idx];

                img.setColor(tx, tz, abgr(0xFF000000 | mul(rgb, f)));
            }
        }
        texture.upload();
    }

    // ── Цвет верхнего блока (с учётом биома) ─────────────────────────────
    private static int surfaceRgb(World world, BlockPos pos, BlockState state) {
        Block block = state.getBlock();
        try {
            if (isGrassLike(block)) return BiomeColors.getGrassColor(world, pos) & 0xFFFFFF;
            if (isFoliage(block))   return mul(BiomeColors.getFoliageColor(world, pos) & 0xFFFFFF, 0.85f);
        } catch (Throwable ignored) {}
        int rgb = getBlockRgb(state);
        return rgb == 0 ? 0x101010 : rgb;
    }

    // ── Стекло: 0 — не стекло, 1 — блок стекла, 2 — панель ────────────────
    private static final Map<Block, Integer> glassKindCache = new HashMap<>();
    private static final int   GLASS_TINT          = 0xB5E6EE;  // цвет обычного стекла
    private static final int   PANE_TINT           = 0xF2FAFC;  // цвет обычной панели
    private static final float GLASS_ALPHA         = 0.40f;     // плотность стекла (0 — невидимое, 1 — сплошное)
    private static final float GLASS_ALPHA_STAINED = 0.55f;
    private static final float PANE_ALPHA          = 0.45f;     // плотность линий панели
    private static final float PANE_ALPHA_STAINED  = 0.60f;

    private static int glassKind(Block b) {
        Integer c = glassKindCache.get(b);
        if (c != null) return c;
        String path = Registries.BLOCK.getId(b).getPath();
        int k = 0;
        if (path.equals("glass") || path.endsWith("_stained_glass")) k = 1;
        else if (path.equals("glass_pane") || path.endsWith("_stained_glass_pane")) k = 2;
        glassKindCache.put(b, k);
        return k;
    }

    private static boolean isGrassLike(Block b) {
        return b == Blocks.GRASS_BLOCK || b == Blocks.SHORT_GRASS
                || b == Blocks.TALL_GRASS || b == Blocks.FERN
                || b == Blocks.LARGE_FERN || b == Blocks.SUGAR_CANE
                || b == Blocks.WHEAT || b == Blocks.CARROTS
                || b == Blocks.POTATOES || b == Blocks.BEETROOTS;
    }

    private static boolean isFoliage(Block b) {
        return b == Blocks.OAK_LEAVES || b == Blocks.BIRCH_LEAVES
                || b == Blocks.JUNGLE_LEAVES || b == Blocks.ACACIA_LEAVES
                || b == Blocks.DARK_OAK_LEAVES || b == Blocks.MANGROVE_LEAVES;
    }

    private static int mul(int rgb, float f) {
        int r = clamp255(Math.round(((rgb >> 16) & 0xFF) * f));
        int g = clamp255(Math.round(((rgb >>  8) & 0xFF) * f));
        int b = clamp255(Math.round(( rgb        & 0xFF) * f));
        return (r << 16) | (g << 8) | b;
    }

    /** t = 0 → a, t = 1 → b */
    private static int blend(int a, int b, float t) {
        t = MathHelper.clamp(t, 0f, 1f);
        int r = Math.round(((a >> 16) & 0xFF) * (1f - t) + ((b >> 16) & 0xFF) * t);
        int g = Math.round(((a >>  8) & 0xFF) * (1f - t) + ((b >>  8) & 0xFF) * t);
        int bl = Math.round(( a        & 0xFF) * (1f - t) + ( b        & 0xFF) * t);
        return (clamp255(r) << 16) | (clamp255(g) << 8) | clamp255(bl);
    }

    /** Стабильный псевдослучайный шум 0..1 по мировым координатам (не мерцает при движении). */
    private static float hash01(int x, int z) {
        int h = x * 73856093 ^ z * 19349663;
        h ^= h >>> 13;
        h *= 0x5bd1e995;
        h ^= h >>> 15;
        return (h & 0xFFFF) / 65535f;
    }

    // ── Компас N/E/S/W по краям карты ────────────────────────────────────
    private static void drawCompass(DrawContext ctx, MinecraftClient mc, int cx, int cy,
                                    int size, boolean circle, boolean rotate, float yaw) {
        double theta = rotate ? Math.toRadians(180f - yaw) : 0.0;
        String[] letters = {"N", "E", "S", "W"};
        int[][] dirs = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};
        double edge = size / 2.0 - 7;
        for (int i = 0; i < 4; i++) {
            double dx = dirs[i][0], dy = dirs[i][1];
            double rx = dx * Math.cos(theta) - dy * Math.sin(theta);
            double ry = dx * Math.sin(theta) + dy * Math.cos(theta);
            double sc = circle
                    ? edge / Math.hypot(rx, ry)
                    : edge / Math.max(Math.abs(rx), Math.abs(ry));
            int px = (int) Math.round(cx + rx * sc);
            int py = (int) Math.round(cy + ry * sc);
            ctx.fill(px - 5, py - 5, px + 6, py + 6, 0xAA000000);
            int col = i == 0 ? 0xFFFF5555 : 0xFFE6E6E6;
            ctx.drawTextWithShadow(mc.textRenderer, letters[i],
                    px - mc.textRenderer.getWidth(letters[i]) / 2, py - 4, col);
        }
    }

    // ── Стрелка игрока: красный треугольник с чёрной обводкой ────────────
    private static void drawArrow(DrawContext ctx, int cx, int cy) {
        for (int i = 0; i < 11; i++) {
            int hw = (int) (i * 0.55) + 1;
            ctx.fill(cx - hw, cy - 6 + i, cx + hw + 1, cy - 5 + i, 0xFF000000);
        }
        for (int i = 0; i < 9; i++) {
            int hw = (int) (i * 0.55);
            ctx.fill(cx - hw, cy - 5 + i, cx + hw + 1, cy - 4 + i, 0xFFE53030);
        }
    }

    private static int clamp255(int v) { return v < 0 ? 0 : v > 255 ? 255 : v; }

    private static int getBlockRgb(BlockState state) {
        Block b = state.getBlock();
        Integer cached = blockColorCache.get(b);
        if (cached != null) return cached;
        int rgb = getDetailedBlockColor(b, state);
        if (rgb < 0) {
            try { rgb = b.getDefaultMapColor().color; }
            catch (Throwable t) { rgb = 0x808080; }
        }
        blockColorCache.put(b, rgb);
        return rgb;
    }

    /** Полная таблица цветов блоков — несколько сотен записей. */
    @SuppressWarnings("deprecation")
    private static int getDetailedBlockColor(Block b, BlockState s) {
        // ── Земля / камень ────────────────────────────────────────────────
        if (b == Blocks.GRASS_BLOCK)     return 0x7FB238;
        if (b == Blocks.DIRT)            return 0x966C4A;
        if (b == Blocks.DIRT_PATH)       return 0xA07048;
        if (b == Blocks.COARSE_DIRT)     return 0x7B5530;
        if (b == Blocks.ROOTED_DIRT)     return 0x8C6042;
        if (b == Blocks.PODZOL)          return 0x6A3E1A;
        if (b == Blocks.MYCELIUM)        return 0x8C6B8C;
        if (b == Blocks.STONE)           return 0x7A7A7A;
        if (b == Blocks.COBBLESTONE)     return 0x7A7A7A;
        if (b == Blocks.MOSSY_COBBLESTONE) return 0x5A7A4A;
        if (b == Blocks.DEEPSLATE)       return 0x505060;
        if (b == Blocks.COBBLED_DEEPSLATE) return 0x545462;
        if (b == Blocks.TUFF)            return 0x747063;
        if (b == Blocks.DRIPSTONE_BLOCK) return 0x87736A;
        if (b == Blocks.BEDROCK)         return 0x555555;
        if (b == Blocks.GRAVEL)          return 0x82807D;
        if (b == Blocks.CLAY)            return 0xA0A6B2;
        if (b == Blocks.MUD)             return 0x3D3830;
        if (b == Blocks.MUDDY_MANGROVE_ROOTS) return 0x5C4533;

        // ── Песок / песчаник ──────────────────────────────────────────────
        if (b == Blocks.SAND)            return 0xDBD3A0;
        if (b == Blocks.RED_SAND)        return 0xBE6621;
        if (b == Blocks.SANDSTONE)       return 0xD5CD9A;
        if (b == Blocks.RED_SANDSTONE)   return 0xB4571A;
        if (b == Blocks.SOUL_SAND)       return 0x5C4A3A;
        if (b == Blocks.SOUL_SOIL)       return 0x5C4A3A;

        // ── Снег / лёд ────────────────────────────────────────────────────
        if (b == Blocks.SNOW_BLOCK)      return 0xEEEEEE;
        if (b == Blocks.SNOW)            return 0xEEEEEE;
        if (b == Blocks.ICE)             return 0x7DA5EC;
        if (b == Blocks.PACKED_ICE)      return 0x5E8EDE;
        if (b == Blocks.BLUE_ICE)        return 0x4080C8;
        if (b == Blocks.POWDER_SNOW)     return 0xE0E8EE;

        // ── Вода / жидкости ───────────────────────────────────────────────
        if (b == Blocks.WATER)           return 0x3F5FD8;
        if (b == Blocks.LAVA)            return 0xE04000;

        // ── Дерево (брёвна) ───────────────────────────────────────────────
        if (b == Blocks.OAK_LOG || b == Blocks.OAK_WOOD)       return 0x6B5730;
        if (b == Blocks.SPRUCE_LOG || b == Blocks.SPRUCE_WOOD)  return 0x4A3520;
        if (b == Blocks.BIRCH_LOG || b == Blocks.BIRCH_WOOD)    return 0xC8C8B0;
        if (b == Blocks.JUNGLE_LOG || b == Blocks.JUNGLE_WOOD)  return 0x7A5530;
        if (b == Blocks.ACACIA_LOG || b == Blocks.ACACIA_WOOD)  return 0x8C5022;
        if (b == Blocks.DARK_OAK_LOG || b == Blocks.DARK_OAK_WOOD) return 0x3A2810;
        if (b == Blocks.MANGROVE_LOG || b == Blocks.MANGROVE_WOOD) return 0x703028;
        if (b == Blocks.CHERRY_LOG || b == Blocks.CHERRY_WOOD)  return 0xB87060;
        if (b == Blocks.BAMBOO || b == Blocks.BAMBOO_BLOCK)     return 0x88A830;

        // ── Планки ────────────────────────────────────────────────────────
        if (b == Blocks.OAK_PLANKS)      return 0xC8A860;
        if (b == Blocks.SPRUCE_PLANKS)   return 0x9C7840;
        if (b == Blocks.BIRCH_PLANKS)    return 0xD4C888;
        if (b == Blocks.JUNGLE_PLANKS)   return 0xB48050;
        if (b == Blocks.ACACIA_PLANKS)   return 0xB86030;
        if (b == Blocks.DARK_OAK_PLANKS) return 0x4C3010;
        if (b == Blocks.MANGROVE_PLANKS) return 0x8C4838;
        if (b == Blocks.CHERRY_PLANKS)   return 0xE0A090;
        if (b == Blocks.CRIMSON_PLANKS)  return 0x882050;
        if (b == Blocks.WARPED_PLANKS)   return 0x288878;

        // ── Листья ────────────────────────────────────────────────────────
        if (b == Blocks.SPRUCE_LEAVES)   return 0x2F4821;
        if (b == Blocks.AZALEA_LEAVES)   return 0x406830;
        if (b == Blocks.FLOWERING_AZALEA_LEAVES) return 0xC86080;
        if (b == Blocks.CHERRY_LEAVES)   return 0xE888B0;

        // ── Руды ──────────────────────────────────────────────────────────
        if (b == Blocks.COAL_ORE || b == Blocks.DEEPSLATE_COAL_ORE)       return 0x5A5A5A;
        if (b == Blocks.IRON_ORE || b == Blocks.DEEPSLATE_IRON_ORE)       return 0xA28B7C;
        if (b == Blocks.COPPER_ORE || b == Blocks.DEEPSLATE_COPPER_ORE)   return 0xB87040;
        if (b == Blocks.GOLD_ORE || b == Blocks.DEEPSLATE_GOLD_ORE)       return 0xC7A838;
        if (b == Blocks.REDSTONE_ORE || b == Blocks.DEEPSLATE_REDSTONE_ORE) return 0xC02020;
        if (b == Blocks.LAPIS_ORE || b == Blocks.DEEPSLATE_LAPIS_ORE)     return 0x2060C0;
        if (b == Blocks.DIAMOND_ORE || b == Blocks.DEEPSLATE_DIAMOND_ORE) return 0x4AEDD9;
        if (b == Blocks.EMERALD_ORE || b == Blocks.DEEPSLATE_EMERALD_ORE) return 0x2AB820;
        if (b == Blocks.NETHER_GOLD_ORE) return 0xC7A838;
        if (b == Blocks.NETHER_QUARTZ_ORE) return 0xE0D0C0;
        if (b == Blocks.ANCIENT_DEBRIS)   return 0x8A5540;

        // ── Металлы / минералы ────────────────────────────────────────────
        if (b == Blocks.COAL_BLOCK)       return 0x2A2A2A;
        if (b == Blocks.IRON_BLOCK)       return 0xC8C8C8;
        if (b == Blocks.COPPER_BLOCK)     return 0xC86840;
        if (b == Blocks.EXPOSED_COPPER || b == Blocks.EXPOSED_COPPER_BULB) return 0xB87858;
        if (b == Blocks.WEATHERED_COPPER || b == Blocks.WEATHERED_COPPER_BULB) return 0x6A9A70;
        if (b == Blocks.OXIDIZED_COPPER || b == Blocks.OXIDIZED_COPPER_BULB) return 0x509878;
        if (b == Blocks.GOLD_BLOCK)       return 0xF0D820;
        if (b == Blocks.DIAMOND_BLOCK)    return 0x5CE8D8;
        if (b == Blocks.EMERALD_BLOCK)    return 0x22CC22;
        if (b == Blocks.LAPIS_BLOCK)      return 0x1850B0;
        if (b == Blocks.REDSTONE_BLOCK)   return 0xCC1010;
        if (b == Blocks.NETHERITE_BLOCK)  return 0x483040;
        if (b == Blocks.AMETHYST_BLOCK || b == Blocks.BUDDING_AMETHYST || b == Blocks.AMETHYST_CLUSTER) return 0x9966CC;
        if (b == Blocks.CALCITE)          return 0xE8E0D0;
        if (b == Blocks.RAW_IRON_BLOCK)   return 0xB09078;
        if (b == Blocks.RAW_COPPER_BLOCK) return 0xB86840;
        if (b == Blocks.RAW_GOLD_BLOCK)   return 0xD8B830;

        // ── Камень / кирпич ───────────────────────────────────────────────
        if (b == Blocks.STONE_BRICKS || b == Blocks.MOSSY_STONE_BRICKS) return 0x787070;
        if (b == Blocks.CRACKED_STONE_BRICKS) return 0x686868;
        if (b == Blocks.ANDESITE || b == Blocks.POLISHED_ANDESITE) return 0x8C8C8C;
        if (b == Blocks.DIORITE || b == Blocks.POLISHED_DIORITE)   return 0xD0C8C8;
        if (b == Blocks.GRANITE || b == Blocks.POLISHED_GRANITE)   return 0xA05840;
        if (b == Blocks.BLACKSTONE)       return 0x282830;
        if (b == Blocks.GILDED_BLACKSTONE) return 0x403010;
        if (b == Blocks.BASALT || b == Blocks.POLISHED_BASALT) return 0x484850;
        if (b == Blocks.SMOOTH_BASALT)    return 0x404048;
        if (b == Blocks.OBSIDIAN || b == Blocks.CRYING_OBSIDIAN) return 0x1A1028;
        if (b == Blocks.REINFORCED_DEEPSLATE) return 0x484870;
        if (b == Blocks.PRISMARINE || b == Blocks.PRISMARINE_BRICKS || b == Blocks.DARK_PRISMARINE) return 0x4A8888;
        if (b == Blocks.SEA_LANTERN)      return 0xA8C8B8;
        if (b == Blocks.BRICKS)           return 0x9C5030;
        if (b == Blocks.TERRACOTTA)       return 0x975A3A;
        if (b == Blocks.PURPUR_BLOCK || b == Blocks.PURPUR_PILLAR) return 0xA078A0;

        // ── Нижний мир ────────────────────────────────────────────────────
        if (b == Blocks.NETHERRACK)       return 0x6E2424;
        if (b == Blocks.NETHER_BRICKS)    return 0x3A1818;
        if (b == Blocks.CRIMSON_NYLIUM)   return 0x882020;
        if (b == Blocks.WARPED_NYLIUM)    return 0x208080;
        if (b == Blocks.CRIMSON_STEM || b == Blocks.WARPED_STEM) return b == Blocks.CRIMSON_STEM ? 0x7A2050 : 0x307878;
        if (b == Blocks.NETHER_WART_BLOCK) return 0x8A0018;
        if (b == Blocks.SHROOMLIGHT)      return 0xE8A840;
        if (b == Blocks.MAGMA_BLOCK)      return 0xBC4800;
        if (b == Blocks.GLOWSTONE)        return 0xEECC80;

        // ── Край ──────────────────────────────────────────────────────────
        if (b == Blocks.END_STONE || b == Blocks.END_STONE_BRICKS) return 0xDBDEA1;
                if (b == Blocks.CHORUS_FLOWER || b == Blocks.CHORUS_PLANT) return 0x7A5070;

        // ── Растительность ────────────────────────────────────────────────
        if (b == Blocks.GRASS_BLOCK)      return 0x7FB238;
        if (b == Blocks.VINE)             return 0x3A6820;
        if (b == Blocks.LILY_PAD)         return 0x2D7B2D;
        if (b == Blocks.CACTUS)           return 0x3A7A18;
        if (b == Blocks.PUMPKIN || b == Blocks.CARVED_PUMPKIN) return 0xC87020;
        if (b == Blocks.MELON)            return 0x7A9820;
        if (b == Blocks.HAY_BLOCK)        return 0xB89020;
        if (b == Blocks.MOSS_BLOCK || b == Blocks.MOSS_CARPET) return 0x5A7840;
        if (b == Blocks.PINK_PETALS)      return 0xF090B0;

        // ── Утилиты / постройки ───────────────────────────────────────────
        if (b == Blocks.GLASS || b == Blocks.GLASS_PANE) return 0xC0D8E8;
        if (b == Blocks.BOOKSHELF)        return 0xC8A860;
        if (b == Blocks.CRAFTING_TABLE)   return 0xA07840;
        if (b == Blocks.FURNACE || b == Blocks.BLAST_FURNACE || b == Blocks.SMOKER) return 0x888888;
        if (b == Blocks.CHEST || b == Blocks.TRAPPED_CHEST) return 0xA07028;
        if (b == Blocks.BARREL)           return 0x7A5C2A;
        if (b == Blocks.SPONGE || b == Blocks.WET_SPONGE) return 0xC8C030;
                if (b == Blocks.SPAWNER || b == Blocks.TRIAL_SPAWNER) return 0x303040;
        if (b == Blocks.VAULT)            return 0x405060;
        if (b == Blocks.CRYING_OBSIDIAN)  return 0x301860;
        if (b == Blocks.INFESTED_STONE)   return 0x888090;
        if (b == Blocks.LECTERN)          return 0xC09040;
        if (b == Blocks.JUKEBOX)          return 0x6A4828;
        if (b == Blocks.NOTE_BLOCK)        return 0x7A5A30;
        if (b == Blocks.BONE_BLOCK)       return 0xD8D0C0;
        if (b == Blocks.SLIME_BLOCK)      return 0x70C870;
        if (b == Blocks.HONEY_BLOCK)      return 0xE8A020;
        if (b == Blocks.HONEYCOMB_BLOCK)  return 0xE09010;
        if (b == Blocks.DRIED_KELP_BLOCK) return 0x404820;
        if (b == Blocks.COMPOSTER)        return 0x8A6030;
        if (b == Blocks.LANTERN || b == Blocks.SOUL_LANTERN) return 0xE0C070;
        if (b == Blocks.CAMPFIRE || b == Blocks.SOUL_CAMPFIRE) return 0xE05010;

        // ── Дорожки / пути ────────────────────────────────────────────────
        if (b == Blocks.SMOOTH_STONE || b == Blocks.SMOOTH_STONE_SLAB) return 0x8A8A8A;
        if (b == Blocks.NETHER_BRICK_FENCE) return 0x401818;
        if (b == Blocks.OAK_FENCE || b == Blocks.OAK_FENCE_GATE) return 0xC8A860;

        // ── Конкрет / терракота окрашенные (базовый цвет) ─────────────────
        if (b == Blocks.WHITE_CONCRETE || b == Blocks.WHITE_CONCRETE_POWDER) return 0xCFCFCF;
        if (b == Blocks.ORANGE_CONCRETE)  return 0xE06000;
        if (b == Blocks.MAGENTA_CONCRETE) return 0xB020B0;
        if (b == Blocks.LIGHT_BLUE_CONCRETE) return 0x4090D0;
        if (b == Blocks.YELLOW_CONCRETE)  return 0xF0C000;
        if (b == Blocks.LIME_CONCRETE)    return 0x60C020;
        if (b == Blocks.PINK_CONCRETE)    return 0xE070A0;
        if (b == Blocks.GRAY_CONCRETE)    return 0x484848;
        if (b == Blocks.LIGHT_GRAY_CONCRETE) return 0x9A9A9A;
        if (b == Blocks.CYAN_CONCRETE)    return 0x009090;
        if (b == Blocks.PURPLE_CONCRETE)  return 0x700090;
        if (b == Blocks.BLUE_CONCRETE)    return 0x2020A0;
        if (b == Blocks.BROWN_CONCRETE)   return 0x604020;
        if (b == Blocks.GREEN_CONCRETE)   return 0x306020;
        if (b == Blocks.RED_CONCRETE)     return 0xA01010;
        if (b == Blocks.BLACK_CONCRETE)   return 0x0A0A0A;

        // ── Песок / снег / лёд ────────────────────────────────────────────
        if (b == Blocks.SAND)            return 0xDBD3A0;
        if (b == Blocks.RED_SAND)        return 0xBE6621;
        if (b == Blocks.SANDSTONE)       return 0xD8CB9B;
        if (b == Blocks.SNOW || b == Blocks.SNOW_BLOCK) return 0xF8FEFE;
        if (b == Blocks.ICE)             return 0x7DA0E8;
        if (b == Blocks.PACKED_ICE)      return 0x8EA8E0;
        if (b == Blocks.BLUE_ICE)        return 0x74A8F0;

        // ── Жидкости / нижний мир ─────────────────────────────────────────
        if (b == Blocks.LAVA)            return 0xE8620C;
        if (b == Blocks.MAGMA_BLOCK)     return 0x8A3A14;
        if (b == Blocks.OBSIDIAN)        return 0x14121E;
        if (b == Blocks.NETHERRACK)      return 0x6F3637;
        if (b == Blocks.SOUL_SAND)       return 0x514034;
        if (b == Blocks.BASALT)          return 0x4B4B50;
        if (b == Blocks.BLACKSTONE)      return 0x2B2830;
        if (b == Blocks.END_STONE)       return 0xDBDE9E;

        // ── Постройки ─────────────────────────────────────────────────────
        if (b == Blocks.OAK_PLANKS)      return 0xA2814F;
        if (b == Blocks.SPRUCE_PLANKS)   return 0x7A5A34;
        if (b == Blocks.BIRCH_PLANKS)    return 0xC8B77A;
        if (b == Blocks.JUNGLE_PLANKS)   return 0xB88764;
        if (b == Blocks.ACACIA_PLANKS)   return 0xA9582F;
        if (b == Blocks.DARK_OAK_PLANKS) return 0x4A2F17;
        if (b == Blocks.OAK_LOG)         return 0x6B5430;
        if (b == Blocks.SPRUCE_LOG)      return 0x3A2A18;
        if (b == Blocks.BIRCH_LOG)       return 0xD5CDB5;
        if (b == Blocks.JUNGLE_LOG)      return 0x584419;
        if (b == Blocks.ACACIA_LOG)      return 0x676157;
        if (b == Blocks.DARK_OAK_LOG)    return 0x3E2912;
        if (b == Blocks.STONE_BRICKS)    return 0x777777;
        if (b == Blocks.BRICKS)          return 0x96594B;
        if (b == Blocks.ANDESITE)        return 0x848484;
        if (b == Blocks.DIORITE)         return 0xBDBDBD;
        if (b == Blocks.GRANITE)         return 0x956756;
        if (b == Blocks.SMOOTH_STONE)    return 0x9E9E9E;
        if (b == Blocks.GLASS)           return 0xB5D8E0;
        if (b == Blocks.TERRACOTTA)      return 0x985E43;
        if (b == Blocks.WHITE_TERRACOTTA)  return 0xD1B2A1;
        if (b == Blocks.ORANGE_TERRACOTTA) return 0xA25426;
        if (b == Blocks.RED_TERRACOTTA)    return 0x8F3D2E;
        if (b == Blocks.YELLOW_TERRACOTTA) return 0xBA8524;
        if (b == Blocks.BROWN_TERRACOTTA)  return 0x4D3323;

        // ── Растительность без биомного оттенка ───────────────────────────
        if (b == Blocks.SPRUCE_LEAVES)   return 0x3F6B3F;
        if (b == Blocks.CHERRY_LEAVES)   return 0xE9A7C5;
        if (b == Blocks.AZALEA_LEAVES)   return 0x62883A;
        if (b == Blocks.FLOWERING_AZALEA_LEAVES) return 0x7A8F4F;
        if (b == Blocks.MOSS_BLOCK)      return 0x596D2D;
        if (b == Blocks.CACTUS)          return 0x587A2F;
        if (b == Blocks.PUMPKIN)         return 0xC57B1A;
        if (b == Blocks.HAY_BLOCK)       return 0xB8970A;
        if (b == Blocks.FARMLAND)        return 0x6F4A2D;

        return -1;
    }

    private static int abgr(int argb) {
        int a = (argb >>> 24) & 0xFF;
        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8) & 0xFF;
        int b = argb & 0xFF;
        return (a << 24) | (b << 16) | (g << 8) | r;
    }


    public static int clampRadius(int r) { return MinimapClamp.clampRadius(r); }

    public static void zoom(int dir) {
        SpotHelperConfig cfg = SpotHelperConfig.INSTANCE;
        int cur = cfg.minimapRadiusValue;
        cur += (dir > 0 ? -100 : 100);
        cfg.minimapRadiusValue = MathHelper.clamp(cur, 0, 1000);
        cfg.save();
    }
}