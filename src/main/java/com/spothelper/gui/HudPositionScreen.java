package com.spothelper.gui;

import com.spothelper.config.SpotHelperConfig;
import com.spothelper.gui.ui.UiChip;
import com.spothelper.gui.ui.UiDraw;
import com.spothelper.gui.ui.UiInput;
import com.spothelper.gui.ui.UiTheme;
import com.spothelper.gui.ui.UiTheme.Role;
import com.spothelper.hud.StatusHud;
import com.spothelper.hud.StatusHud.Element;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

/**
 * Расположение HUD: элементы показываются поверх игры, их можно перетаскивать мышью.
 * Края экрана и центр «примагничивают». Положение сохраняется при отпускании кнопки.
 */
public class HudPositionScreen extends Screen {

    private static final int SNAP = 6;
    private static final int PAD = 3;

    private final Screen parent;
    private final UiInput in = new UiInput();

    private Element dragging;
    private int dragDX, dragDY;

    public HudPositionScreen(Screen parent) {
        super(Text.literal("Расположение HUD"));
        this.parent = parent;
    }

    private static int c(Role r) {
        return UiTheme.get(r);
    }

    @Override
    protected void init() {
        StatusHud.editing = true;
        in.down = false;
        in.dragging = null;
    }

    @Override
    public void removed() {
        StatusHud.editing = false;
        SpotHelperConfig.INSTANCE.save();
    }

    @Override
    public void close() {
        MinecraftClient.getInstance().setScreen(parent);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    // ---------------------------------------------------------------- положение

    private int clampX(int x, int w) {
        return Math.max(0, Math.min(x, Math.max(0, width - w)));
    }

    private int clampY(int y, int h) {
        return Math.max(0, Math.min(y, Math.max(0, height - h)));
    }

    // ---------------------------------------------------------------- отрисовка

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        in.begin(mouseX, mouseY);
        TextRenderer tr = textRenderer;

        ctx.fill(0, 0, width, height, 0x70000000);

        String hint = "Перетащи элементы мышью. Края и центр экрана притягивают.";
        UiDraw.text(ctx, tr, hint, (width - tr.getWidth(hint)) / 2, 8, c(Role.TEXT_MAIN));

        for (Element e : Element.values()) {
            int[] sz = StatusHud.previewSize(e);
            int x = clampX(StatusHud.x(e), sz[0]);
            int y = clampY(StatusHud.y(e), sz[1]);
            boolean on = StatusHud.enabled(e);
            boolean active = e == dragging;
            boolean hov = !active && dragging == null
                    && UiDraw.inside(mouseX, mouseY, x - PAD, y - PAD, sz[0] + PAD * 2, sz[1] + PAD * 2);

            StatusHud.drawPreview(ctx, e, x, y);

            int col = c(active ? Role.HIGHLIGHT : hov ? Role.ACCENT : on ? Role.CARD_BORDER : Role.TEXT_DIM);
            UiDraw.frame(ctx, x - PAD, y - PAD, sz[0] + PAD * 2, sz[1] + PAD * 2, col);

            String label = e.label + (on ? "" : " (выключено)");
            int ly = y - PAD - 11 >= 0 ? y - PAD - 11 : y + sz[1] + PAD + 3;
            UiDraw.text(ctx, tr, label, x - PAD, ly, col);
        }

        // нижняя панель
        String reset = "Сбросить";
        String done = "Готово";
        int rw = UiChip.width(tr, reset);
        int dw = Math.max(UiChip.width(tr, done), 70);
        int total = rw + 8 + dw;
        int bx = (width - total) / 2;
        int by = height - 30;
        UiChip.draw(ctx, tr, in, bx, by, rw, 20, reset, false, true, StatusHud::resetPositions);
        UiChip.draw(ctx, tr, in, bx + rw + 8, by, dw, 20, done, true, true, this::close);
    }

    // ---------------------------------------------------------------- мышь

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (click.button() == 0) {
            in.down = true;
            if (in.click(click.x(), click.y())) return true;

            Element[] all = Element.values();
            for (int i = all.length - 1; i >= 0; i--) {
                Element e = all[i];
                int[] sz = StatusHud.previewSize(e);
                int x = clampX(StatusHud.x(e), sz[0]);
                int y = clampY(StatusHud.y(e), sz[1]);
                if (UiDraw.inside(click.x(), click.y(), x - PAD, y - PAD, sz[0] + PAD * 2, sz[1] + PAD * 2)) {
                    dragging = e;
                    dragDX = (int) click.x() - x;
                    dragDY = (int) click.y() - y;
                    return true;
                }
            }
        }
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseDragged(Click click, double offsetX, double offsetY) {
        if (dragging != null) {
            int[] sz = StatusHud.previewSize(dragging);
            int nx = (int) click.x() - dragDX;
            int ny = (int) click.y() - dragDY;

            if (nx < SNAP) nx = 0;
            if (nx > width - sz[0] - SNAP) nx = width - sz[0];
            if (Math.abs(nx + sz[0] / 2 - width / 2) < SNAP) nx = (width - sz[0]) / 2;
            if (ny < SNAP) ny = 0;
            if (ny > height - sz[1] - SNAP) ny = height - sz[1];

            StatusHud.set(dragging, clampX(nx, sz[0]), clampY(ny, sz[1]));
            return true;
        }
        return super.mouseDragged(click, offsetX, offsetY);
    }

    @Override
    public boolean mouseReleased(Click click) {
        in.down = false;
        if (dragging != null) {
            dragging = null;
            SpotHelperConfig.INSTANCE.save();
            return true;
        }
        return super.mouseReleased(click);
    }
}
