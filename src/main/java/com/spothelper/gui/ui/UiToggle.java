package com.spothelper.gui.ui;

import com.spothelper.gui.ui.UiTheme.Role;
import net.minecraft.client.gui.DrawContext;

/** Переключатель 30×14 с круглой ручкой. Включён: ACCENT, выключен: TOGGLE_OFF. */
public final class UiToggle {
    private UiToggle() {}

    public static final int W = 30;
    public static final int H = 14;

    /**
     * @param in       null — рисовать без взаимодействия (предпросмотр)
     * @param onToggle вызывается по клику; сам переключатель значение не хранит
     */
    public static void draw(DrawContext c, UiInput in, int x, int y, boolean on, boolean enabled, Runnable onToggle) {
        boolean hov = in != null && enabled && in.mouseOver(x, y, W, H);
        boolean prs = hov && in.down;

        float k = UiAnim.approach(UiAnim.pos(1, x, y), on ? 1f : 0f);
        int track = UiTheme.mix(UiTheme.get(Role.TOGGLE_OFF), UiTheme.get(Role.ACCENT), k);
        int knob = UiTheme.get(Role.TEXT_MAIN);
        if (prs) {
            track = UiTheme.pressed(track);
        } else if (hov) {
            track = UiTheme.hover(track);
        }
        if (!enabled) {
            track = UiTheme.dim(track);
            knob = UiTheme.dim(knob);
        }

        UiDraw.pill(c, x, y, W, H, track);
        UiDraw.disc(c, x + 2 + Math.round((W - 12) * k), y + 2, 10, knob);

        if (in != null && enabled) {
            in.addClick(x - 2, y - 2, W + 4, H + 4, onToggle);
        }
    }
}
