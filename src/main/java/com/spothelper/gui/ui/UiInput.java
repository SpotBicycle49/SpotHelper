package com.spothelper.gui.ui;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Режим «immediate»: каждый кадр компоненты регистрируют здесь области клика,
 * а Screen.mouseClicked берёт области, зарегистрированные в последнем кадре.
 * Стек clip не даёт кликать по элементам, скрытым за пределами скролл-области.
 */
public final class UiInput {
    private record Region(int x, int y, int w, int h, Runnable action, int[] clip) {}

    private record SliderReg(UiSlider slider, int[] clip) {}

    private final List<Region> regions = new ArrayList<>();
    private final List<SliderReg> sliders = new ArrayList<>();
    private final Deque<int[]> clips = new ArrayDeque<>();

    public double mx;
    public double my;
    /** Левая кнопка мыши зажата. */
    public boolean down;
    /** Слайдер, который сейчас тянут. */
    public UiSlider dragging;

    public void begin(double mouseX, double mouseY) {
        regions.clear();
        sliders.clear();
        clips.clear();
        mx = mouseX;
        my = mouseY;
    }

    public void pushClip(int x, int y, int w, int h) {
        int[] c = {x, y, x + w, y + h};
        int[] p = clips.peek();
        if (p != null) {
            c[0] = Math.max(c[0], p[0]);
            c[1] = Math.max(c[1], p[1]);
            c[2] = Math.min(c[2], p[2]);
            c[3] = Math.min(c[3], p[3]);
        }
        clips.push(c);
    }

    public void popClip() {
        clips.pop();
    }

    private static boolean inClip(double px, double py, int[] c) {
        return c == null || (px >= c[0] && px < c[2] && py >= c[1] && py < c[3]);
    }

    public boolean mouseOver(int x, int y, int w, int h) {
        return UiDraw.inside(mx, my, x, y, w, h) && inClip(mx, my, clips.peek());
    }

    public boolean pressed(int x, int y, int w, int h) {
        return down && mouseOver(x, y, w, h);
    }

    public void addClick(int x, int y, int w, int h, Runnable action) {
        if (action == null) return;
        regions.add(new Region(x, y, w, h, action, clips.peek()));
    }

    public void addSlider(UiSlider s) {
        sliders.add(new SliderReg(s, clips.peek()));
    }

    /** Выполняет действие верхней (последней нарисованной) области под курсором. */
    public boolean click(double px, double py) {
        for (int i = regions.size() - 1; i >= 0; i--) {
            Region r = regions.get(i);
            if (UiDraw.inside(px, py, r.x, r.y, r.w, r.h) && inClip(px, py, r.clip)) {
                r.action.run();
                return true;
            }
        }
        return false;
    }

    public UiSlider hitSlider(double px, double py) {
        for (int i = sliders.size() - 1; i >= 0; i--) {
            SliderReg r = sliders.get(i);
            if (r.slider.hit(px, py) && inClip(px, py, r.clip)) return r.slider;
        }
        return null;
    }
}
