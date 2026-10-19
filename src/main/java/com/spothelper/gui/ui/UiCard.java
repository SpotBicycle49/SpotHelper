package com.spothelper.gui.ui;

import com.spothelper.gui.ui.UiTheme.Role;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

/**
 * Карточка: рамка (CARD_BORDER, при наведении HIGHLIGHT), заголовок, описание.
 * Зону элементов (переключатель, слайдер и т.д.) рисует вызывающий код поверх карточки:
 * регистрируются они позже, поэтому получают клик раньше, чем сама карточка.
 */
public final class UiCard {
    private UiCard() {}

    /**
     * @param title         может быть null
     * @param desc          может быть null
     * @param rightReserved ширина справа, занятая переключателем/счётчиком (заголовок обрезается раньше)
     * @param onClick       клик по карточке; может быть null
     * @return true, если курсор над карточкой
     */
    public static boolean draw(DrawContext c, TextRenderer tr, UiInput in, int x, int y, int w, int h,
                               String title, String desc, int rightReserved, Runnable onClick) {
        boolean hov = in != null && in.mouseOver(x, y, w, h);
        float k = title != null ? UiAnim.approach("card:" + title, hov ? 1f : 0f) : (hov ? 1f : 0f);
        int fill = UiTheme.get(Role.CARD_BG);
        fill = UiTheme.mix(fill, UiTheme.hover(fill), k);
        if (hov && in.down && onClick != null) fill = UiTheme.pressed(fill);
        int border = UiTheme.mix(UiTheme.get(Role.CARD_BORDER), UiTheme.get(Role.HIGHLIGHT), k);

        UiDraw.box(c, x, y, w, h, border, fill);
        if (title != null) {
            UiDraw.textBold(c, tr, UiDraw.fit(tr, title, w - 16 - rightReserved, true), x + 8, y + 8, UiTheme.get(Role.TEXT_MAIN));
        }
        if (desc != null) {
            UiDraw.text(c, tr, UiDraw.fit(tr, desc, w - 16), x + 8, y + 21, UiTheme.get(Role.TEXT_DIM));
        }
        if (in != null) in.addClick(x, y, w, h, onClick);
        return hov;
    }

    /** Блок внутри панели настроек модуля: заголовок HIGHLIGHT, без реакции на клик. */
    public static void block(DrawContext c, TextRenderer tr, UiInput in, int x, int y, int w, int h,
                             String title, boolean enabled) {
        boolean hov = in != null && in.mouseOver(x, y, w, h);
        int border = UiTheme.get(hov && enabled ? Role.HIGHLIGHT : Role.CARD_BORDER);
        int titleColor = UiTheme.get(Role.HIGHLIGHT);
        if (!enabled) titleColor = UiTheme.dim(titleColor);
        UiDraw.box(c, x, y, w, h, border, UiTheme.get(Role.CARD_BG));
        UiDraw.text(c, tr, UiDraw.fit(tr, title, w - 16), x + 8, y + 8, titleColor);
    }
}
