package com.spothelper.gui.ui;

import com.spothelper.gui.ui.UiTheme.Role;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

/** Строка роли в палитре: название, квадрат с цветом, hex (#RRGGBB). Клик — onClick (открыть ColorPickerScreen). */
public final class UiColorRow {
    private UiColorRow() {}

    public static void draw(DrawContext c, TextRenderer tr, UiInput in, int x, int y, int w, int h,
                            Role role, boolean selected, Runnable onClick) {
        boolean hov = in != null && in.mouseOver(x, y, w, h);
        boolean prs = hov && in.down;

        int bg = UiTheme.get(Role.CARD_BG);
        if (selected) bg = UiTheme.mix(bg, UiTheme.raw(Role.ACCENT), 0.25);
        if (prs) {
            bg = UiTheme.pressed(bg);
        } else if (hov) {
            bg = UiTheme.hover(bg);
        }
        int border = UiTheme.get(selected || hov ? Role.HIGHLIGHT : Role.CARD_BORDER);
        UiDraw.box(c, x, y, w, h, border, bg);

        int ty = y + (h - 8) / 2;
        String hex = UiTheme.hex(role);
        int hexW = UiDraw.width(tr, hex);
        int sw = Math.min(12, h - 6);
        int hexX = x + w - 6 - hexW;
        int swX = hexX - 6 - sw;

        UiDraw.text(c, tr, UiDraw.fit(tr, role.label, swX - 6 - (x + 6)), x + 6, ty, UiTheme.get(Role.TEXT_MAIN));
        UiDraw.frame(c, swX - 1, y + (h - sw) / 2 - 1, sw + 2, sw + 2, UiTheme.get(Role.TEXT_DIM));
        UiDraw.rect(c, swX, y + (h - sw) / 2, sw, sw, UiTheme.raw(role));
        UiDraw.text(c, tr, hex, hexX, ty, UiTheme.get(Role.HIGHLIGHT));

        if (in != null) in.addClick(x, y, w, h, onClick);
    }
}
