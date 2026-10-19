package com.spothelper.gui;

import com.spothelper.config.SpotHelperConfig;
import com.spothelper.gui.ui.UiChip;
import com.spothelper.gui.ui.UiDraw;
import com.spothelper.gui.ui.UiField;
import com.spothelper.gui.ui.UiScreen;
import com.spothelper.gui.ui.UiTheme.Role;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** Список команд, которые блокирует «Блок шалкера». Оформление — как в главном меню. */
public class ShulkerCommandsScreen extends UiScreen {

    private static final int ROW_H = 22;
    private static final List<String> DEFAULTS = Arrays.asList("hub", "рги", "lobby");

    private final SpotHelperConfig cfg = SpotHelperConfig.INSTANCE;
    private final UiField input = new UiField("Команда без «/», например hub", 32);
    private String error = "";

    public ShulkerCommandsScreen(Screen parent) {
        super(parent, "Блок шалкера: команды");
        field(input);
        input.onEnter = this::add;
    }

    @Override
    protected int maxWidth() {
        return 460;
    }

    @Override
    protected int footerHeight() {
        return 30;
    }

    @Override
    protected void afterInit() {
        focus(input);
    }

    @Override
    protected void onBack() {
        cfg.save();
        close();
    }

    private List<String> list() {
        if (cfg.shulkerLockCommandList == null) cfg.shulkerLockCommandList = new ArrayList<>();
        return cfg.shulkerLockCommandList;
    }

    private static String clean(String raw) {
        String s = raw == null ? "" : raw.trim();
        if (s.startsWith("/")) s = s.substring(1);
        int sp = s.indexOf(' ');
        if (sp >= 0) s = s.substring(0, sp);
        return s.toLowerCase(Locale.ROOT);
    }

    private void add() {
        String s = clean(input.text());
        if (s.isEmpty()) {
            error = "Введите команду";
            return;
        }
        for (String e : list()) {
            if (e != null && clean(e).equals(s)) {
                error = "Эта команда уже в списке";
                return;
            }
        }
        list().add(s);
        cfg.save();
        input.setText("");
        error = "";
    }

    private void remove(String entry) {
        list().remove(entry);
        cfg.save();
    }

    private void resetList() {
        cfg.shulkerLockCommandList = new ArrayList<>(DEFAULTS);
        cfg.save();
        error = "";
    }

    @Override
    protected int drawBody(DrawContext ctx, int x, int top, int w) {
        TextRenderer tr = textRenderer;
        int y = top;

        UiDraw.text(ctx, tr, UiDraw.fit(tr, "Эти команды блокируются, пока шалкеров больше допустимого.", w),
                x, y, c(Role.TEXT_DIM));
        y += 16;

        String addLabel = "Добавить";
        int aw = UiChip.width(tr, addLabel);
        input.draw(ctx, in, x, y, w - aw - 6, 20, true);
        UiChip.draw(ctx, tr, in, x + w - aw, y + 1, aw, 18, addLabel, true, true, this::add);
        y += 26;

        if (!error.isEmpty()) {
            UiDraw.text(ctx, tr, UiDraw.fit(tr, error, w), x, y, c(Role.ERROR));
            y += 14;
        }

        List<String> list = list();
        UiDraw.text(ctx, tr, "Команд в списке: " + list.size(), x, y, c(Role.HIGHLIGHT));
        y += 16;

        if (list.isEmpty()) {
            UiDraw.text(ctx, tr, "Список пуст — команды не блокируются.", x, y, c(Role.TEXT_DIM));
            return y + 16 - top;
        }

        int rw = 24;
        for (String entry : new ArrayList<>(list)) {
            UiDraw.box(ctx, x, y, w, ROW_H - 2, c(Role.CARD_BORDER), c(Role.CARD_BG));
            UiDraw.text(ctx, tr, UiDraw.fit(tr, "/" + entry, w - rw - 20), x + 8, y + 6, c(Role.TEXT_MAIN));
            UiChip.draw(ctx, tr, in, x + w - rw - 4, y + 2, rw, ROW_H - 6, "✕", false, true, () -> remove(entry));
            y += ROW_H;
        }
        return y - top;
    }

    @Override
    protected void drawFooter(DrawContext ctx) {
        TextRenderer tr = textRenderer;
        int fy = viewB + 8;
        String reset = "Сбросить список";
        String done = "Готово";
        int rw = UiChip.width(tr, reset);
        int dw = Math.max(UiChip.width(tr, done), 70);
        UiChip.draw(ctx, tr, in, viewL, fy, rw, 20, reset, false, true, this::resetList);
        UiChip.draw(ctx, tr, in, viewR - dw, fy, dw, 20, done, true, true, this::onBack);
    }
}
