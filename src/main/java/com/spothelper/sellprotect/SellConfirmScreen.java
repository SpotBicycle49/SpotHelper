package com.spothelper.sellprotect;

import com.spothelper.gui.ui.UiChip;
import com.spothelper.gui.ui.UiDraw;
import com.spothelper.gui.ui.UiScreen;
import com.spothelper.gui.ui.UiTheme.Role;
import com.spothelper.util.NumberFormat;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.item.ItemStack;

/** Подтверждение продажи. Оформление — как в главном меню. */
public class SellConfirmScreen extends UiScreen {

    private final ItemStack stack;
    private final int unitPrice;
    private final int totalCount;
    private final int totalSum;
    private final Runnable onConfirm;

    private int itemX, itemY;

    public SellConfirmScreen(Screen parent, ItemStack stack, int unitPrice,
                             int totalCount, int totalSum, Runnable onConfirm) {
        super(parent, "Подтверждение продажи");
        this.stack = stack;
        this.unitPrice = unitPrice;
        this.totalCount = totalCount;
        this.totalSum = totalSum;
        this.onConfirm = onConfirm;
    }

    @Override
    protected int maxWidth() {
        return 380;
    }

    @Override
    protected int footerHeight() {
        return 30;
    }

    @Override
    protected int drawBody(DrawContext ctx, int x, int top, int w) {
        TextRenderer tr = textRenderer;
        int y = top;
        itemX = x + 4;
        itemY = y + 4;

        // иконка предмета в рамке
        UiDraw.box(ctx, x, y, 24, 24, c(Role.CARD_BORDER), c(Role.CARD_BG));
        ctx.drawItem(stack, itemX, itemY);

        int tx = x + 34;
        int tw = w - 34;
        UiDraw.text(ctx, tr, UiDraw.fit(tr, stack.getName().getString(), tw), tx, y + 8, c(Role.TEXT_MAIN));
        y += 36;

        y = row(ctx, tr, x, y, w, "Количество", NumberFormat.format(totalCount) + " шт.", Role.TEXT_MAIN);
        y = row(ctx, tr, x, y, w, "Цена за шт.", NumberFormat.format(unitPrice), Role.HIGHLIGHT);
        y = row(ctx, tr, x, y, w, "Итого", NumberFormat.format(totalSum) + " монет", Role.OK);
        return y - top;
    }

    private int row(DrawContext ctx, TextRenderer tr, int x, int y, int w, String name, String value, Role col) {
        UiDraw.box(ctx, x, y, w, 24, c(Role.CARD_BORDER), c(Role.CARD_BG));
        UiDraw.text(ctx, tr, name, x + 8, y + 8, c(Role.TEXT_DIM));
        UiDraw.textRight(ctx, tr, UiDraw.fit(tr, value, w - 24 - tr.getWidth(name)), x + w - 8, y + 8, c(col));
        return y + 30;
    }

    @Override
    protected void drawFooter(DrawContext ctx) {
        TextRenderer tr = textRenderer;
        int fy = viewB + 8;
        String cancel = "Отмена";
        String ok = "Подтвердить";
        int cw = UiChip.width(tr, cancel) + 10;
        int ow = UiChip.width(tr, ok) + 10;
        UiChip.draw(ctx, tr, in, viewL, fy, cw, 20, cancel, false, true, this::close);
        UiChip.draw(ctx, tr, in, viewR - ow, fy, ow, 20, ok, true, true, () -> {
            MinecraftClient.getInstance().setScreen(parent);
            try {
                onConfirm.run();
            } catch (Throwable t) {
                com.spothelper.util.Log.error("sell confirm", t);
            }
        });
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        if (mouseX >= itemX && mouseX <= itemX + 16 && mouseY >= itemY && mouseY <= itemY + 16) {
            ctx.drawItemTooltip(textRenderer, stack, mouseX, mouseY);
        }
    }
}
