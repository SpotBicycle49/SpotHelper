package com.spothelper.minimap;

import com.spothelper.gui.ui.UiChip;
import com.spothelper.gui.ui.UiDraw;
import com.spothelper.gui.ui.UiScreen;
import com.spothelper.gui.ui.UiSlider;
import com.spothelper.gui.ui.UiTheme.Role;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;

import java.util.function.IntConsumer;

/** Выбор цвета (ARGB) четырьмя слайдерами. Оформление — как в главном меню. */
public class ColorPickerScreen extends UiScreen {

    private final IntConsumer onPick;
    private final UiSlider sr = new UiSlider(0, 255, 1, 30);
    private final UiSlider sg = new UiSlider(0, 255, 1, 30);
    private final UiSlider sb = new UiSlider(0, 255, 1, 30);
    private final UiSlider sa = new UiSlider(0, 255, 1, 30);
    private int r, g, b, a;

    public ColorPickerScreen(Screen parent, String label, int initialColor, IntConsumer onPick) {
        super(parent, label);
        this.onPick = onPick;
        this.a = (initialColor >>> 24) & 0xFF;
        this.r = (initialColor >>> 16) & 0xFF;
        this.g = (initialColor >>> 8) & 0xFF;
        this.b = initialColor & 0xFF;

        sr.setValue(r);
        sg.setValue(g);
        sb.setValue(b);
        sa.setValue(a);
        sr.onLive = v -> r = v;
        sg.onLive = v -> g = v;
        sb.onLive = v -> b = v;
        sa.onLive = v -> a = v;
    }

    @Override
    protected int maxWidth() {
        return 380;
    }

    @Override
    protected int footerHeight() {
        return 30;
    }

    private int argb() {
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    @Override
    protected int drawBody(DrawContext ctx, int x, int top, int w) {
        TextRenderer tr = textRenderer;
        int y = top;

        // предпросмотр
        UiDraw.box(ctx, x, y, w, 40, c(Role.CARD_BORDER), c(Role.CARD_BG));
        UiDraw.rect(ctx, x + 3, y + 3, w - 6, 34, argb());
        y += 48;

        String hex = String.format("#%02X%02X%02X · α %d", r, g, b, a);
        UiDraw.text(ctx, tr, hex, x, y, c(Role.HIGHLIGHT));
        y += 18;

        y = channel(ctx, "R", sr, x, y, w);
        y = channel(ctx, "G", sg, x, y, w);
        y = channel(ctx, "B", sb, x, y, w);
        y = channel(ctx, "A", sa, x, y, w);
        return y - top;
    }

    private int channel(DrawContext ctx, String name, UiSlider s, int x, int y, int w) {
        UiDraw.text(ctx, textRenderer, name, x, y + 3, c(Role.TEXT_MAIN));
        s.draw(ctx, textRenderer, in, x + 18, y, w - 18, true);
        return y + 24;
    }

    @Override
    protected void drawFooter(DrawContext ctx) {
        TextRenderer tr = textRenderer;
        int fy = viewB + 8;
        String cancel = "Отмена";
        String ok = "ОК";
        int cw = UiChip.width(tr, cancel);
        int ow = Math.max(UiChip.width(tr, ok), 60);
        UiChip.draw(ctx, tr, in, viewL, fy, cw, 20, cancel, false, true, this::close);
        UiChip.draw(ctx, tr, in, viewR - ow, fy, ow, 20, ok, true, true, () -> {
            onPick.accept(argb());
            close();
        });
    }
}
