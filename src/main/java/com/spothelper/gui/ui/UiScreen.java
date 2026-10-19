package com.spothelper.gui.ui;

import com.spothelper.gui.ui.UiTheme.Role;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * Каркас для дополнительных экранов в стиле главного меню:
 * окно по центру, заголовок, кнопка «‹ Назад», прокручиваемая область, подвал, всплывающая подсказка.
 * Потомок реализует drawBody (и при необходимости drawFooter / footerHeight / onKey).
 * Все цвета берутся из UiTheme.
 */
public abstract class UiScreen extends Screen {
    protected static final int ESC = 256;
    protected static final int ENTER = 257;
    protected static final int KP_ENTER = 335;

    protected final Screen parent;
    protected UiInput in = new UiInput();

    protected float scroll;
    protected int contentH;
    protected int wx, wy, ww, wh, viewL, viewT, viewR, viewB;

    private final List<UiField> fields = new ArrayList<>();
    /** Экран, в окно которого встроена эта страница (null — обычный самостоятельный экран). */
    private Screen embeddedHost;
    private BiConsumer<String, Boolean> toastSink;

    private String toastText = "";
    private boolean toastOk;
    private long toastUntil;

    protected UiScreen(Screen parent, String title) {
        super(Text.literal(title));
        this.parent = parent;
    }

    // ---------------------------------------------------------------- настройка потомком

    protected int maxWidth() {
        return 560;
    }

    protected int footerHeight() {
        return 0;
    }

    /** Рисует содержимое с левого верхнего угла (x, top) шириной w. Возвращает занятую высоту. */
    protected abstract int drawBody(DrawContext ctx, int x, int top, int w);

    protected void drawFooter(DrawContext ctx) {}

    /** Клавиша, пока ни одно поле не в фокусе. true — клавиша обработана. */
    protected boolean onKey(int key) {
        return false;
    }

    protected void afterInit() {}

    protected void onBack() {
        close();
    }

    /** Передаёт фокус полю (вызывать из afterInit). */
    protected void focus(UiField f) {
        if (f.widget() != null) setFocused(f.widget());
    }

    protected UiField field(UiField f) {
        f.focuser = w -> setFocused(w);
        fields.add(f);
        return f;
    }

    // ---------------------------------------------------------------- жизненный цикл

    @Override
    protected void init() {
        in.down = false;
        in.dragging = null;
        for (UiField f : fields) addSelectableChild(f.create(textRenderer));
        afterInit();
    }

    @Override
    public void close() {
        if (client != null) client.setScreen(parent);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        int key = input.key();
        if (getFocused() instanceof TextFieldWidget focused) {
            if (key == ESC) {
                setFocused(null);
                return true;
            }
            if (key == ENTER || key == KP_ENTER) {
                for (UiField f : fields) {
                    if (f.widget() == focused && f.onEnter != null) {
                        f.onEnter.run();
                        return true;
                    }
                }
            }
            return super.keyPressed(input);
        }
        if (onKey(key)) return true;
        if (key == ESC) {
            onBack();
            return true;
        }
        return super.keyPressed(input);
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (click.button() == 0) {
            in.down = true;
            setFocused(null);
            UiSlider s = in.hitSlider(click.x(), click.y());
            if (s != null) {
                in.dragging = s;
                s.dragTo(click.x());
                return true;
            }
            if (in.click(click.x(), click.y())) return true;
        }
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseDragged(Click click, double offsetX, double offsetY) {
        if (in.dragging != null) {
            in.dragging.dragTo(click.x());
            return true;
        }
        return super.mouseDragged(click, offsetX, offsetY);
    }

    @Override
    public boolean mouseReleased(Click click) {
        in.down = false;
        if (in.dragging != null) {
            in.dragging.finish();
            in.dragging = null;
            return true;
        }
        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int maxScroll = Math.max(0, contentH - (viewB - viewT));
        scroll = Math.max(0, Math.min(scroll - (float) (verticalAmount * 20), maxScroll));
        return true;
    }

    // ---------------------------------------------------------------- отрисовка

    protected static int c(Role r) {
        return UiTheme.get(r);
    }

    protected void toast(String text, boolean ok) {
        if (toastSink != null) {
            toastSink.accept(text, ok);
            return;
        }
        toastText = text;
        toastOk = ok;
        toastUntil = System.currentTimeMillis() + 3000;
    }

    protected void layout() {
        ww = Math.max(160, Math.min(maxWidth(), width - 40));
        wh = Math.max(120, Math.min(460, height - 40));
        wx = (width - ww) / 2;
        wy = (height - wh) / 2;
        viewL = wx + 14;
        viewR = wx + ww - 18;
        viewT = wy + 42;
        viewB = wy + wh - 10 - footerHeight();
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        in.begin(mouseX, mouseY);
        layout();
        TextRenderer tr = textRenderer;

        ctx.fill(0, 0, width, height, UiTheme.scrim());
        UiDraw.rect(ctx, wx, wy, ww, wh, c(Role.WINDOW_BG));
        UiDraw.rect(ctx, wx, wy + 34, ww, 1, c(Role.CARD_BORDER));
        UiDraw.frame(ctx, wx, wy, ww, wh, c(Role.ACCENT));

        String back = "‹ Назад";
        int bw = UiChip.width(tr, back);
        int bx = wx + ww - 14 - bw;
        UiChip.draw(ctx, tr, in, bx, wy + 8, bw, 18, back, false, true, this::onBack);
        UiDraw.text(ctx, tr, UiDraw.fit(tr, title.getString(), bx - 8 - viewL), viewL, wy + 13, c(Role.HIGHLIGHT));

        drawContent(ctx);
        if (footerHeight() > 0) drawFooter(ctx);

        if (System.currentTimeMillis() < toastUntil) {
            int col = c(toastOk ? Role.OK : Role.ERROR);
            String shown = UiDraw.fit(tr, toastText, ww - 40);
            int tw = UiDraw.width(tr, shown) + 16;
            int tx = wx + (ww - tw) / 2;
            int ty = wy + wh - 28;
            UiDraw.box(ctx, tx, ty, tw, 18, col, UiTheme.mix(c(Role.CARD_BG), col, 0.15));
            UiDraw.text(ctx, tr, shown, tx + 8, ty + 5, col);
        }
    }

    private void drawContent(DrawContext ctx) {
        int vw = viewR - viewL;
        int vh = viewB - viewT;
        if (vh < 10 || vw < 20) return;

        int maxScroll = Math.max(0, contentH - vh);
        scroll = Math.max(0, Math.min(scroll, maxScroll));

        ctx.enableScissor(viewL - 3, viewT, viewR + 3, viewB);
        in.pushClip(viewL - 3, viewT, vw + 6, vh);
        int used = drawBody(ctx, viewL, viewT - Math.round(scroll), vw);
        in.popClip();
        ctx.disableScissor();
        contentH = used + 4;

        if (contentH > vh) {
            int tx = viewR + 4;
            UiDraw.rect(ctx, tx, viewT, 3, vh, c(Role.SLIDER_TRACK));
            int th = Math.max(16, vh * vh / contentH);
            int ty = viewT + (int) ((vh - th) * (scroll / (float) (contentH - vh)));
            UiDraw.rect(ctx, tx, ty, 3, th, c(Role.ACCENT));
        }
    }

    // ---------------------------------------------------------------- встраивание в другой экран

    /** Экран, который надо указывать родителем при открытии вложенных экранов. */
    protected final Screen self() {
        return embeddedHost != null ? embeddedHost : this;
    }

    public final List<UiField> fieldList() {
        return fields;
    }

    /**
     * Подготовка страницы к показу внутри окна другого экрана. Хост после этого должен добавить
     * виджеты полей (UiField.widget()) в свои дочерние элементы, чтобы работал ввод с клавиатуры.
     */
    public final void embedInit(MinecraftClient mc, int w, int h, Screen host) {
        this.embeddedHost = host;
        this.width = w;
        this.height = h;
        clearChildren();
        init();
        for (UiField f : fields) f.focuser = wdg -> host.setFocused(wdg);
    }

    /**
     * Рисует содержимое страницы в области хоста. scrollIO[0] — прокрутка хоста
     * (страница может её сбросить, например при смене вкладки). Возвращает занятую высоту.
     */
    public final int drawEmbedded(DrawContext ctx, int x, int top, int w, int vt, int vb,
                                  UiInput sharedIn, float[] scrollIO, BiConsumer<String, Boolean> sink) {
        this.in = sharedIn;
        this.toastSink = sink;
        this.viewL = x;
        this.viewR = x + w;
        this.viewT = vt;
        this.viewB = vb;
        this.scroll = scrollIO[0];
        int used = drawBody(ctx, x, top, w);
        scrollIO[0] = this.scroll;
        return used;
    }

    /**
     * Клавиша, пока страница встроена. true — клавиша обработана страницей;
     * false — хосту нужно передать её дальше (например, в поле ввода).
     */
    public final boolean embeddedKey(int key, Screen host) {
        if (host.getFocused() instanceof TextFieldWidget focused) {
            if (key == ESC) {
                host.setFocused(null);
                return true;
            }
            if (key == ENTER || key == KP_ENTER) {
                for (UiField f : fields) {
                    if (f.widget() == focused && f.onEnter != null) {
                        f.onEnter.run();
                        return true;
                    }
                }
            }
            return false;
        }
        return onKey(key);
    }

    // ---------------------------------------------------------------- общие элементы

    /** Карточка с переключателем справа. */
    protected void toggleCard(DrawContext ctx, int x, int y, int w, int h,
                              String title, String desc, boolean on, Runnable toggle) {
        UiCard.draw(ctx, textRenderer, in, x, y, w, h, title, desc, UiToggle.W + 8, toggle);
        UiToggle.draw(ctx, in, x + w - 8 - UiToggle.W, y + (h - UiToggle.H) / 2, on, true, toggle);
    }

    /**
     * Чипы в строку с переносом. active и enabled могут быть null.
     * Возвращает y под последней строкой (с отступом).
     */
    protected int flowChips(DrawContext ctx, int x, int y, int w, String[] labels, Runnable[] actions,
                            boolean[] active, boolean[] enabled) {
        TextRenderer tr = textRenderer;
        int cx = x;
        for (int i = 0; i < labels.length; i++) {
            int cw = Math.min(UiChip.width(tr, labels[i]), w);
            if (cx + cw > x + w && cx > x) {
                cx = x;
                y += 24;
            }
            UiChip.draw(ctx, tr, in, cx, y, cw, 18, labels[i],
                    active != null && active[i], enabled == null || enabled[i], actions[i]);
            cx += cw + 6;
        }
        return y + 18 + 6;
    }

    /** Подпись-раздел серым цветом. Возвращает высоту. */
    protected int label(DrawContext ctx, int x, int y, String text) {
        UiDraw.text(ctx, textRenderer, text, x, y + 2, c(Role.TEXT_DIM));
        return 14;
    }
}