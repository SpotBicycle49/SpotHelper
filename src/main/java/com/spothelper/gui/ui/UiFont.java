package com.spothelper.gui.ui;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;
import org.joml.Matrix3x2fStack;

/**
 * Шрифт GUI из растровых атласов (Inter, заранее отрисованные глифы, билинейная фильтрация через .mcmeta).
 * Текст собирается из картинок глифов, поэтому выглядит гладко при любом GUI scale и не зависит от font-провайдеров.
 * Если в строке есть символ, которого нет в атласе, она рисуется обычным шрифтом игры.
 */
public final class UiFont {
    private UiFont() {}

    /** Размер em в пикселях GUI (до масштаба). */
    private static final float SIZE = 9.5f;
    /** Базовая линия относительно верха строки (в пикселях GUI). */
    private static final float BASELINE = 7.6f;
    /** Смещение тени в пикселях GUI. */
    private static final float SHADOW = 0.75f;

    private static final float K = SIZE / UiGlyphData.EM;

    private record Glyph(int u, int v, int w, int h, int xoff, int yoff, float adv) {}

    private static final class Atlas {
        final Identifier id;
        final int texW;
        final int texH;
        final Map<Character, Glyph> glyphs = new HashMap<>();

        Atlas(String file, int texW, int texH, String data) {
            this.id = Identifier.of("spothelper", "textures/gui/" + file);
            this.texW = texW;
            this.texH = texH;
            for (String row : data.split(";")) {
                String[] p = row.split(",");
                if (p.length < 8) continue;
                glyphs.put((char) Integer.parseInt(p[0]), new Glyph(
                        Integer.parseInt(p[1]), Integer.parseInt(p[2]), Integer.parseInt(p[3]), Integer.parseInt(p[4]),
                        Integer.parseInt(p[5]), Integer.parseInt(p[6]), Float.parseFloat(p[7])));
            }
        }

        boolean canRender(String s) {
            for (int i = 0; i < s.length(); i++) {
                if (!glyphs.containsKey(s.charAt(i))) return false;
            }
            return true;
        }

        float advance(String s) {
            float a = 0;
            for (int i = 0; i < s.length(); i++) a += glyphs.get(s.charAt(i)).adv();
            return a;
        }
    }

    private static Atlas regular;
    private static Atlas bold;

    private static Atlas atlas(boolean b) {
        if (b) {
            if (bold == null) bold = new Atlas("font_bold.png", UiGlyphData.BOLD_W, UiGlyphData.BOLD_H, UiGlyphData.BOLD);
            return bold;
        }
        if (regular == null) {
            regular = new Atlas("font_regular.png", UiGlyphData.REGULAR_W, UiGlyphData.REGULAR_H, UiGlyphData.REGULAR);
        }
        return regular;
    }

    public static int width(TextRenderer tr, String s, boolean boldFace) {
        if (s == null || s.isEmpty()) return 0;
        Atlas a = atlas(boldFace);
        if (!a.canRender(s)) return tr.getWidth(s);
        return (int) Math.ceil(a.advance(s) * K);
    }

    public static void draw(DrawContext c, TextRenderer tr, String s, int x, int y, int color, boolean boldFace) {
        if (s == null || s.isEmpty()) return;
        if ((color & 0xFC000000) == 0) color |= 0xFF000000; // как у обычного текста: нулевая альфа = непрозрачный
        Atlas a = atlas(boldFace);
        if (!a.canRender(s)) {
            c.drawTextWithShadow(tr, s, x, y, color);
            return;
        }
        int alpha = (int) ((color >>> 24) * 0.8f);
        int shadow = ((color & 0xFCFCFC) >> 2) | (alpha << 24);
        run(c, a, s, x + SHADOW, y + SHADOW + BASELINE, shadow);
        run(c, a, s, x, y + BASELINE, color);
    }

    /** Рисует строку глифами; координаты внутри — в пикселях атласа (матрица масштабирует их до GUI). */
    private static void run(DrawContext c, Atlas a, String s, float x, float baselineY, int color) {
        Matrix3x2fStack m = c.getMatrices();
        m.pushMatrix();
        m.translate(x, baselineY);
        m.scale(K, K);
        float pen = 0f;
        for (int i = 0; i < s.length(); i++) {
            Glyph g = a.glyphs.get(s.charAt(i));
            if (g.w() > 0) {
                c.drawTexture(RenderPipelines.GUI_TEXTURED, a.id,
                        Math.round(pen) + g.xoff(), g.yoff(),
                        (float) g.u(), (float) g.v(),
                        g.w(), g.h(),
                        g.w(), g.h(),
                        a.texW, a.texH,
                        color);
            }
            pen += g.adv();
        }
        m.popMatrix();
    }
}
