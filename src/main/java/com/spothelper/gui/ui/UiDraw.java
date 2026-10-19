package com.spothelper.gui.ui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;
import org.joml.Matrix3x2fStack;

/** Примитивы рисования: только DrawContext.fill и drawTextWithShadow. Цветов здесь нет. */
public final class UiDraw {
    private UiDraw() {}

    public static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    public static void rect(DrawContext c, int x, int y, int w, int h, int color) {
        if (w <= 0 || h <= 0) return;
        c.fill(x, y, x + w, y + h, color);
    }

    /** Рамка 1 px. */
    public static void frame(DrawContext c, int x, int y, int w, int h, int color) {
        rect(c, x, y, w, 1, color);
        rect(c, x, y + h - 1, w, 1, color);
        rect(c, x, y + 1, 1, h - 2, color);
        rect(c, x + w - 1, y + 1, 1, h - 2, color);
    }

    /** Скруглённый прямоугольник, радиус 2 px. */
    public static void round(DrawContext c, int x, int y, int w, int h, int color) {
        roundRect(c, x, y, w, h, 2, color);
    }

    /** Скруглённый прямоугольник с радиусом r (сглаженный). */
    public static void roundR(DrawContext c, int x, int y, int w, int h, int r, int color) {
        roundRect(c, x, y, w, h, r, color);
    }

    /** Блок: рамка 1 px + заливка; радиус скругления зависит от размера (до 10 px). */
    public static void box(DrawContext c, int x, int y, int w, int h, int border, int fill) {
        boxR(c, x, y, w, h, Math.max(2, Math.min(10, Math.min(w, h) / 4)), border, fill);
    }

    /** Блок с заданным радиусом скругления (радиус не больше половины меньшей стороны). */
    public static void boxR(DrawContext c, int x, int y, int w, int h, int r, int border, int fill) {
        if (w <= 0 || h <= 0) return;
        r = Math.max(1, Math.min(r, Math.min(w, h) / 2));
        roundRect(c, x, y, w, h, r, border);
        roundRect(c, x + 1, y + 1, w - 2, h - 2, Math.max(1, r - 1), fill);
        if (w > 2 * r + 2 && h > 6) rect(c, x + r, y + 1, w - 2 * r, 1, 0x0CFFFFFF); // тонкий верхний блик
    }

    private static int guiScale() {
        try {
            return Math.max(1, (int) Math.round(MinecraftClient.getInstance().getWindow().getScaleFactor()));
        } catch (RuntimeException e) {
            return 1;
        }
    }

    private static int withCoverage(int color, double cov) {
        int a = (int) Math.round((color >>> 24) * Math.max(0, Math.min(1, cov)));
        return (a << 24) | (color & 0xFFFFFF);
    }

    private static int gradientAt(int top, int bottom, double t) {
        return top == bottom ? top : UiTheme.mix(top, bottom, Math.max(0, Math.min(1, t)));
    }

    /**
     * Скруглённый прямоугольник с вертикальным градиентом top → bottom (для одного цвета передайте его дважды).
     * Углы рисуются в экранных пикселях со сглаживанием по краю дуги, поэтому «лесенки» нет при любом GUI scale.
     */
    public static void roundGradient(DrawContext c, int x, int y, int w, int h, int r,
                                     int top, int bottom, boolean roundLeft, boolean roundRight) {
        if (USE_TEXTURES) roundGradientTex(c, x, y, w, h, r, top, bottom, roundLeft, roundRight);
        else roundGradientAA(c, x, y, w, h, r, top, bottom, roundLeft, roundRight);
    }

    /** true: углы из готовых сглаженных текстур (билинейная фильтрация); false: расчёт по экранным пикселям. */
    public static final boolean USE_TEXTURES = true;

    /** Четвертинки круга: сторона квадранта в пикселях текстуры (сама текстура вдвое больше). */
    private static final int[] ROUND_Q = {16, 32, 64, 128};
    private static final Identifier[] ROUND_TEX = {
            Identifier.of("spothelper", "textures/gui/round_16.png"),
            Identifier.of("spothelper", "textures/gui/round_32.png"),
            Identifier.of("spothelper", "textures/gui/round_64.png"),
            Identifier.of("spothelper", "textures/gui/round_128.png")};

    /** Угол r×r: четвертинка круга нужного размера, окрашенная в color. quad: 0 ЛВ, 1 ПВ, 2 ЛН, 3 ПН. */
    private static void corner(DrawContext c, int x, int y, int r, int quad, int color, int screenR) {
        int idx = ROUND_Q.length - 1;
        for (int i = 0; i < ROUND_Q.length; i++) {
            if (ROUND_Q[i] >= screenR) { idx = i; break; }
        }
        int q = ROUND_Q[idx];
        float u = (quad & 1) != 0 ? q : 0f;
        float v = (quad & 2) != 0 ? q : 0f;
        c.drawTexture(RenderPipelines.GUI_TEXTURED, ROUND_TEX[idx], x, y, u, v, r, r, q, q, 2 * q, 2 * q, color);
    }

    private static void roundGradientTex(DrawContext c, int x, int y, int w, int h, int r,
                                         int top, int bottom, boolean roundLeft, boolean roundRight) {
        if (w <= 0 || h <= 0) return;
        r = Math.min(r, Math.min(w, h) / 2);
        boolean flat = top == bottom;
        if (r < 1) {
            roundGradientAA(c, x, y, w, h, r, top, bottom, roundLeft, roundRight);
            return;
        }
        int screenR = r * guiScale();
        // середина по высоте и полосы между углами
        if (flat) {
            if (h > 2 * r) c.fill(x, y + r, x + w, y + h - r, top);
            if (w > 2 * r) {
                c.fill(x + r, y, x + w - r, y + r, top);
                c.fill(x + r, y + h - r, x + w - r, y + h, top);
            }
        } else {
            for (int i = 0; i < h; i++) {
                int col = gradientAt(top, bottom, (i + 0.5) / h);
                boolean edge = i < r || i >= h - r;
                int x0 = edge ? x + r : x;
                int x1 = edge ? x + w - r : x + w;
                if (x1 > x0) c.fill(x0, y + i, x1, y + i + 1, col);
            }
        }
        int topCol = gradientAt(top, bottom, (r * 0.5) / h);
        int botCol = gradientAt(top, bottom, (h - r * 0.5) / h);
        if (roundLeft) corner(c, x, y, r, 0, topCol, screenR); else c.fill(x, y, x + r, y + r, topCol);
        if (roundRight) corner(c, x + w - r, y, r, 1, topCol, screenR); else c.fill(x + w - r, y, x + w, y + r, topCol);
        if (roundLeft) corner(c, x, y + h - r, r, 2, botCol, screenR); else c.fill(x, y + h - r, x + r, y + h, botCol);
        if (roundRight) corner(c, x + w - r, y + h - r, r, 3, botCol, screenR); else c.fill(x + w - r, y + h - r, x + w, y + h, botCol);
    }

    private static void roundGradientAA(DrawContext c, int x, int y, int w, int h, int r,
                                        int top, int bottom, boolean roundLeft, boolean roundRight) {
        if (w <= 0 || h <= 0) return;
        r = Math.min(r, Math.min(w, h) / 2);
        if (r < 1) {
            for (int i = 0; i < h; i++) {
                c.fill(x, y + i, x + w, y + i + 1, gradientAt(top, bottom, (i + 0.5) / h));
            }
            return;
        }
        // прямые строки между углами — в координатах GUI
        for (int i = r; i < h - r; i++) {
            c.fill(x, y + i, x + w, y + i + 1, gradientAt(top, bottom, (i + 0.5) / h));
        }
        // углы — в экранных пикселях
        int s = guiScale();
        int rs = r * s;
        int hs = h * s;
        long sx = (long) x * s;
        long sy = (long) y * s;
        long ws = (long) w * s;
        Matrix3x2fStack m = c.getMatrices();
        m.pushMatrix();
        m.scale(1f / s, 1f / s);

        boolean flat = top == bottom;
        int runStart = 0;
        int runFi = -1;
        int runCovQ = -1;
        for (int k = 0; k <= rs; k++) {
            int fi = 0;
            int covQ = 0;
            if (k < rs) {
                double dy = rs - k - 0.5;
                double inset = rs - Math.sqrt(Math.max(0.0, (double) rs * rs - dy * dy));
                fi = (int) Math.floor(inset);
                covQ = (int) Math.round((1.0 - (inset - fi)) * 16);
            }
            // одинаковые по форме строки склеиваем в один прямоугольник (только без градиента)
            boolean extend = k < rs && k > runStart && flat && fi == runFi && covQ == runCovQ;
            if (extend) continue;
            if (k > runStart) {
                int k0 = runStart;
                int k1 = k;
                int topCol = gradientAt(top, bottom, (k0 + 0.5) / hs);
                int botCol = gradientAt(top, bottom, (hs - k0 - 0.5) / hs);
                drawCornerRun(c, sx, sy, ws, hs, k0, k1, runFi, runCovQ, topCol, botCol, roundLeft, roundRight);
            }
            runStart = k;
            runFi = fi;
            runCovQ = covQ;
        }
        m.popMatrix();
    }

    /** Серия строк [k0, k1) одинаковой формы: верхний ряд и зеркальный нижний. */
    private static void drawCornerRun(DrawContext c, long sx, long sy, long ws, int hs, int k0, int k1,
                                      int fi, int covQ, int topCol, int botCol,
                                      boolean roundLeft, boolean roundRight) {
        double cov = covQ / 16.0;
        int l = (int) sx + (roundLeft ? fi : 0);
        int rgt = (int) (sx + ws) - (roundRight ? fi : 0);
        int fullL = l + (roundLeft ? 1 : 0);
        int fullR = rgt - (roundRight ? 1 : 0);
        for (int pass = 0; pass < 2; pass++) {
            int yA = pass == 0 ? (int) sy + k0 : (int) sy + hs - k1;
            int yB = pass == 0 ? (int) sy + k1 : (int) sy + hs - k0;
            int col = pass == 0 ? topCol : botCol;
            if (fullR > fullL) c.fill(fullL, yA, fullR, yB, col);
            if (roundLeft) c.fill(l, yA, l + 1, yB, withCoverage(col, cov));
            if (roundRight) c.fill(rgt - 1, yA, rgt, yB, withCoverage(col, cov));
        }
    }

    /** Скруглённый прямоугольник произвольного радиуса (сглаженный). */
    public static void roundRect(DrawContext c, int x, int y, int w, int h, int r, int color) {
        roundGradient(c, x, y, w, h, r, color, color, true, true);
    }

    /** Мягкая тень под окном: несколько слабых слоёв вокруг. */
    public static void shadow(DrawContext c, int x, int y, int w, int h, int r) {
        for (int k = 4; k >= 1; k--) {
            roundRect(c, x - k * 3 / 2, y - k * 3 / 2 + 3, w + k * 3, h + k * 3, r + k * 3 / 2, 0x0C000000);
        }
    }

    /** Круг диаметром d с левым верхним углом (x, y), сглаженный. */
    public static void disc(DrawContext c, int x, int y, int d, int color) {
        roundRect(c, x, y, d, d, d / 2, color);
    }

    /** Капсула (трек переключателя / слайдера), сглаженная. */
    public static void pill(DrawContext c, int x, int y, int w, int h, int color) {
        roundRect(c, x, y, w, h, Math.min(w, h) / 2, color);
    }

    /** Ширина строки шрифтом GUI. */
    public static int width(TextRenderer tr, String s) {
        return UiFont.width(tr, s, false);
    }

    public static void text(DrawContext c, TextRenderer tr, String s, int x, int y, int color) {
        draw(c, tr, s, x, y, color, false);
    }

    /** Заголовок: жирное начертание. */
    public static void textBold(DrawContext c, TextRenderer tr, String s, int x, int y, int color) {
        draw(c, tr, s, x, y, color, true);
    }

    private static void draw(DrawContext c, TextRenderer tr, String s, int x, int y, int color, boolean bold) {
        if (s == null || s.isEmpty()) return;
        UiFont.draw(c, tr, s, x, y, color, bold);
    }

    public static void textRight(DrawContext c, TextRenderer tr, String s, int rightX, int y, int color) {
        text(c, tr, s, rightX - width(tr, s), y, color);
    }

    /** Обрезает строку по ширине, добавляя «…». */
    public static String fit(TextRenderer tr, String s, int maxW) {
        return fit(tr, s, maxW, false);
    }

    public static String fit(TextRenderer tr, String s, int maxW, boolean bold) {
        if (s == null || maxW <= 0) return "";
        if (UiFont.width(tr, s, bold) <= maxW) return s;
        String dots = "…";
        int dw = UiFont.width(tr, dots, bold);
        if (dw > maxW) return "";
        while (!s.isEmpty() && UiFont.width(tr, s, bold) + dw > maxW) {
            s = s.substring(0, s.length() - 1);
        }
        return s + dots;
    }
}
