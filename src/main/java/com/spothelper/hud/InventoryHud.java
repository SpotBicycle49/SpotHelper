package com.spothelper.hud;

import com.spothelper.config.SpotHelperConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.item.ItemStack;
import org.joml.Matrix3x2fStack;

/**
 * Inventory HUD: 27 слотов основного инвентаря (без хотбара) на экране, со счётчиками предметов.
 * Режимы: обычный (тёмная панель и ячейки) и mini (только предметы, компактнее).
 * Положение двигается в редакторе «Расположение HUD» (Element.INVENTORY), масштаб — слайдер в «Функциях».
 */
public final class InventoryHud {
    private static final int COLS = 9;
    private static final int ROWS = 3;
    private static final int FIRST_SLOT = 9; // индексы 9..35 в PlayerInventory

    private InventoryHud() {}

    // ---------------------------------------------------------------- настройки

    public static boolean enabled() {
        return SpotHelperConfig.INSTANCE.invHud == 1;
    }

    public static void toggle() {
        SpotHelperConfig.INSTANCE.invHud ^= 1;
        SpotHelperConfig.INSTANCE.save();
    }

    private static boolean mini() {
        return SpotHelperConfig.INSTANCE.invHudMini == 1;
    }

    private static float scale() {
        int pct = Math.max(50, Math.min(200, SpotHelperConfig.INSTANCE.invHudScale));
        return pct / 100f;
    }

    private static int cell() {
        return mini() ? 16 : 18;
    }

    private static int pad() {
        return mini() ? 0 : 2;
    }

    /** Размер блока на экране (уже с учётом масштаба). */
    public static int[] size() {
        float s = scale();
        int w = COLS * cell() + pad() * 2;
        int h = ROWS * cell() + pad() * 2;
        return new int[]{Math.max(1, Math.round(w * s)), Math.max(1, Math.round(h * s))};
    }

    // ---------------------------------------------------------------- отрисовка

    /** Рисует блок в игре. */
    public static void render(DrawContext ctx) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (StatusHud.editing || !enabled() || mc.player == null || mc.options.hudHidden) return;
        if (mc.currentScreen instanceof HandledScreen<?>) return; // в самом инвентаре HUD не нужен

        int[] sz = size();
        int sw = mc.getWindow().getScaledWidth();
        int sh = mc.getWindow().getScaledHeight();
        int px = Math.max(0, Math.min(SpotHelperConfig.INSTANCE.hudInvX, Math.max(0, sw - sz[0])));
        int py = Math.max(0, Math.min(SpotHelperConfig.INSTANCE.hudInvY, Math.max(0, sh - sz[1])));
        draw(ctx, px, py);
    }

    /** Рисует блок с левым верхним углом (x, y). Используется и в игре, и в редакторе положения. */
    public static void draw(DrawContext ctx, int x, int y) {
        MinecraftClient mc = MinecraftClient.getInstance();
        TextRenderer tr = mc.textRenderer;
        boolean mini = mini();
        int cell = cell();
        int pad = pad();
        int w = COLS * cell + pad * 2;
        int h = ROWS * cell + pad * 2;
        float s = scale();

        Matrix3x2fStack m = ctx.getMatrices();
        m.pushMatrix();
        m.translate(x, y);
        m.scale(s, s);

        if (!mini) {
            ctx.fill(0, 0, w, h, 0x90000000);
            ctx.fill(0, 0, w, 1, 0x40FFFFFF);
            ctx.fill(0, h - 1, w, h, 0x40FFFFFF);
            ctx.fill(0, 0, 1, h, 0x40FFFFFF);
            ctx.fill(w - 1, 0, w, h, 0x40FFFFFF);
        }

        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                int sx = pad + c * cell;
                int sy = pad + r * cell;
                if (!mini) ctx.fill(sx, sy, sx + cell, sy + cell, 0x30FFFFFF);

                if (mc.player == null) continue;
                ItemStack stack = mc.player.getInventory().getStack(FIRST_SLOT + r * COLS + c);
                if (stack.isEmpty()) continue;

                int ix = sx + (cell - 16) / 2;
                int iy = sy + (cell - 16) / 2;
                ctx.drawItem(stack, ix, iy);
                HealthOverlay.drawDurabilityBar(ctx, stack, ix, iy);
                if (stack.getCount() > 1) {
                    String count = String.valueOf(stack.getCount());
                    ctx.drawTextWithShadow(tr, count, ix + 17 - tr.getWidth(count), iy + 9, 0xFFFFFFFF);
                }
            }
        }
        m.popMatrix();
    }
}