package com.spothelper.gui.ui;

import com.spothelper.gui.ui.UiTheme.Role;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

import java.util.function.IntConsumer;
import java.util.function.IntFunction;

/**
 * Слайдер: трек 6 px (SLIDER_TRACK), заливка HIGHLIGHT, перетаскивание мышью.
 * onLive вызывается при каждом изменении во время перетаскивания,
 * onCommit — один раз при отпускании кнопки (там делается cfg.save()).
 * Экземпляр живёт в экране, позиция задаётся при каждой отрисовке.
 */
public final class UiSlider {
    public static final int H = 14;

    public final int min;
    public final int max;
    public final int step;
    /** Ширина зоны подписи справа от трека. */
    public final int valueW;

    public IntConsumer onLive = v -> {};
    public IntConsumer onCommit = v -> {};
    public IntFunction<String> format = String::valueOf;

    private int value;
    private int x;
    private int y;
    private int w;
    private boolean enabled = true;

    public UiSlider(int min, int max, int step, int valueW) {
        this.min = min;
        this.max = max;
        this.step = Math.max(1, step);
        this.valueW = valueW;
        this.value = min;
    }

    public int value() {
        return value;
    }

    public void setValue(int v) {
        value = Math.max(min, Math.min(max, v));
    }

    private int trackW() {
        return Math.max(24, w - valueW - 8);
    }

    public void draw(DrawContext c, TextRenderer tr, UiInput in, int x, int y, int w, boolean enabled) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.enabled = enabled;

        int tw = trackW();
        double frac = max == min ? 0 : (value - min) / (double) (max - min);
        // отображаемая доля плавно догоняет значение (быстрее общих анимаций — чтобы перетаскивание не отставало)
        double shown = UiAnim.approach(this, (float) frac, 22f);
        int fw = (int) Math.round(shown * tw);

        boolean drag = in != null && in.dragging == this;
        boolean hov = enabled && in != null && (drag || in.mouseOver(x - 5, y, tw + 10, H));

        int track = UiTheme.get(Role.SLIDER_TRACK);
        int fill = UiTheme.get(Role.HIGHLIGHT);
        int knob = UiTheme.get(Role.TEXT_MAIN);
        int text = UiTheme.get(Role.HIGHLIGHT);
        if (drag) {
            knob = UiTheme.pressed(knob);
        } else if (hov) {
            knob = UiTheme.hover(knob);
        }
        if (!enabled) {
            track = UiTheme.dim(track);
            fill = UiTheme.dim(fill);
            knob = UiTheme.dim(knob);
            text = UiTheme.dim(text);
        }

        UiDraw.pill(c, x, y + 4, tw, 6, track);
        if (fw > 0) UiDraw.pill(c, x, y + 4, Math.max(fw, 6), 6, fill);
        UiDraw.disc(c, x + fw - 5, y + 2, 10, knob);

        if (valueW > 0) {
            UiDraw.textRight(c, tr, UiDraw.fit(tr, format.apply(value), valueW), x + w, y + 3, text);
        }
        if (in != null && enabled) in.addSlider(this);
    }

    /** Нарисованный без взаимодействия слайдер (предпросмотр темы). */
    public static void drawStatic(DrawContext c, int x, int y, int w, double frac, boolean enabled) {
        int track = UiTheme.get(Role.SLIDER_TRACK);
        int fill = UiTheme.get(Role.HIGHLIGHT);
        int knob = UiTheme.get(Role.TEXT_MAIN);
        if (!enabled) {
            track = UiTheme.dim(track);
            fill = UiTheme.dim(fill);
            knob = UiTheme.dim(knob);
        }
        int fw = (int) Math.round(Math.max(0, Math.min(1, frac)) * w);
        UiDraw.pill(c, x, y + 4, w, 6, track);
        if (fw > 0) UiDraw.pill(c, x, y + 4, Math.max(fw, 6), 6, fill);
        UiDraw.disc(c, x + fw - 5, y + 2, 10, knob);
    }

    public boolean hit(double mx, double my) {
        return enabled && mx >= x - 5 && mx < x + trackW() + 5 && my >= y && my < y + H;
    }

    public void dragTo(double mx) {
        double f = Math.max(0, Math.min(1, (mx - x) / trackW()));
        int v = min + (int) Math.round(f * (max - min) / (double) step) * step;
        v = Math.max(min, Math.min(max, v));
        if (v != value) {
            value = v;
            onLive.accept(v);
        }
    }

    /** Кнопку отпустили — значение фиксируется. */
    public void finish() {
        onCommit.accept(value);
    }
}
