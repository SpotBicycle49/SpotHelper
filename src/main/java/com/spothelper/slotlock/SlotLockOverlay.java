package com.spothelper.slotlock;

import com.spothelper.util.ScreenOrigin;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.screen.slot.Slot;

/** Красная подсветка заблокированных слотов в окнах с инвентарём игрока. */
public final class SlotLockOverlay {
    private SlotLockOverlay() {}

    /**
     * Обычный инвентарь (выживание/приключение) рисуем через событие Fabric после полного render:
     * хвост HandledScreen.render до него не доходит. Остальные экраны — через InventoryScreenMixin.
     */
    public static void init() {
        ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
            if (screen instanceof InventoryScreen inv) {
                ScreenEvents.afterRender(screen).register((sc, ctx, mx, my, delta) -> draw(inv, ctx));
            }
        });
    }

    // Цвета запретов: выброс — красный, перемещение — синий, продажа — жёлтый, все три вместе — фиолетовый.
    private static final int[] BITS = {SlotLockManager.MODE_DROP, SlotLockManager.MODE_MOVE, SlotLockManager.MODE_SELL};
    private static final int[] COLORS = {0xFFFF4040, 0xFF4080FF, 0xFFFFD030};
    private static final int ALL_COLOR = 0xFFB050FF;

    /**
     * Рисует метку запретов на квадрате size×size: один запрет — свой цвет, два — слот пополам
     * (левая половина — первый, правая — второй), все три — отдельный цвет.
     */
    public static void drawMark(DrawContext ctx, int x, int y, int size, int mode) {
        mode &= 7;
        if (mode == 0) return;
        int[] cols = new int[3];
        int n = 0;
        if (mode == 7) {
            cols[n++] = ALL_COLOR;
        } else {
            for (int i = 0; i < 3; i++) if ((mode & BITS[i]) != 0) cols[n++] = COLORS[i];
        }
        int half = size / 2;
        for (int i = 0; i < n; i++) {
            int x0 = n == 1 ? x : (i == 0 ? x : x + half);
            int x1 = n == 1 ? x + size : (i == 0 ? x + half : x + size);
            int col = cols[i];
            int fill = (col & 0x00FFFFFF) | 0x66000000;
            ctx.fill(x0, y, x1, y + size, fill);
            ctx.fill(x0, y, x1, y + 1, col);
            ctx.fill(x0, y + size - 1, x1, y + size, col);
        }
        ctx.fill(x, y, x + 1, y + size, cols[0]);
        ctx.fill(x + size - 1, y, x + size, y + size, cols[n - 1]);
    }

    public static void draw(HandledScreen<?> screen, DrawContext ctx) {
        if (!(screen instanceof ScreenOrigin o)) return;
        for (Slot slot : screen.getScreenHandler().slots) {
            int idx = SlotLockManager.playerIndex(slot);
            if (idx < 0 || !SlotLockManager.isLocked(idx)) continue;
            int x = o.spothelper$originX() + slot.x;
            int y = o.spothelper$originY() + slot.y;
            drawMark(ctx, x, y, 16, SlotLockManager.modeOf(idx));
        }
    }
}
