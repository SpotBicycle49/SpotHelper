package com.spothelper.gui.ui;

import com.spothelper.gui.ui.UiTheme.Role;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

/** Кнопка-чип. Активный: фон ACCENT, рамка HIGHLIGHT. Состояния: обычное, наведение, нажатие, неактивное. */
public final class UiChip {
    private UiChip() {}

    public static int width(TextRenderer tr, String label) {
        return UiDraw.width(tr, label) + 14;
    }

    public static void draw(DrawContext c, TextRenderer tr, UiInput in, int x, int y, int w, int h,
                            String label, boolean active, boolean enabled, Runnable onClick) {
        boolean hov = enabled && in != null && in.mouseOver(x, y, w, h);
        boolean prs = hov && in.down;

        float k = UiAnim.approach(UiAnim.pos(2, x, y), hov ? 1f : 0f);
        int fill;
        int border;
        int text;
        if (active) {
            fill = UiTheme.get(Role.ACCENT);
            border = UiTheme.get(Role.HIGHLIGHT);
            text = UiTheme.onColor(fill);
        } else {
            fill = UiTheme.get(Role.CARD_BG);
            border = UiTheme.mix(UiTheme.get(Role.CARD_BORDER), UiTheme.get(Role.HIGHLIGHT), k);
            text = UiTheme.mix(UiTheme.get(Role.TEXT_MAIN), UiTheme.get(Role.HIGHLIGHT), k);
        }
        fill = UiTheme.mix(fill, UiTheme.hover(fill), k);
        if (prs) fill = UiTheme.pressed(fill);
        if (!enabled) {
            fill = UiTheme.dim(fill);
            border = UiTheme.dim(border);
            text = UiTheme.dim(text);
        }

        UiDraw.boxR(c, x, y, w, h, Math.min(h / 2, 10), border, fill); // «таблетка»
        String t = UiDraw.fit(tr, label, w - 8);
        UiDraw.text(c, tr, t, x + (w - UiDraw.width(tr, t)) / 2, y + (h - 8) / 2, text);

        if (in != null && enabled) in.addClick(x, y, w, h, onClick);
    }
}
