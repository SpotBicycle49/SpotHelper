package com.spothelper.filter;

import com.spothelper.config.SpotHelperConfig;
import com.spothelper.gui.ui.UiChip;
import com.spothelper.gui.ui.UiDraw;
import com.spothelper.gui.ui.UiField;
import com.spothelper.gui.ui.UiScreen;
import com.spothelper.gui.ui.UiTheme;
import com.spothelper.gui.ui.UiTheme.Role;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/** Список с поиском и галочками. Оформление — как в главном меню. */
public abstract class AbstractFilterScreen extends UiScreen {

    protected static final int ROW_H = 20;

    protected final SpotHelperConfig cfg = SpotHelperConfig.INSTANCE;

    protected final List<String> allIds = new ArrayList<>();
    protected final List<String> shownIds = new ArrayList<>();
    protected final List<String> selected = new ArrayList<>();

    protected final UiField search = new UiField("Поиск по названию или ID", 64);
    protected final Consumer<List<String>> onDone;

    protected AbstractFilterScreen(Screen parent, String title, List<String> initial, Consumer<List<String>> onDone) {
        super(parent, title);
        this.onDone = onDone;
        if (initial != null) this.selected.addAll(initial);
        field(search);
        search.onChange = s -> {
            refreshShown();
            scroll = 0;
        };
    }

    protected abstract void collectIds();

    protected abstract String headerText();

    protected String getDisplayName(String id) {
        return id;
    }

    /** Рисует значок слева от названия; возвращает занятую ширину. y — верх строки. */
    protected int drawRowDecoration(DrawContext ctx, String id, int x, int y) {
        return 0;
    }

    /** Дополнительный блок над поиском (категории). Возвращает y под блоком. */
    protected int drawExtra(DrawContext ctx, int x, int y, int w) {
        return y;
    }

    @Override
    protected int maxWidth() {
        return 600;
    }

    @Override
    protected int footerHeight() {
        return 30;
    }

    @Override
    protected void afterInit() {
        reload();
        focus(search);
    }

    /** Пересобрать список id (например, при смене категории). */
    protected void reload() {
        allIds.clear();
        collectIds();
        refreshShown();
        scroll = 0;
    }

    protected void refreshShown() {
        shownIds.clear();
        String q = search.text().trim().toLowerCase(Locale.ROOT);
        for (String id : allIds) {
            if (q.isEmpty() || id.toLowerCase(Locale.ROOT).contains(q)
                    || getDisplayName(id).toLowerCase(Locale.ROOT).contains(q)) shownIds.add(id);
        }
    }

    private void toggle(String id) {
        if (!selected.remove(id)) selected.add(id);
    }

    @Override
    protected int drawBody(DrawContext ctx, int x, int top, int w) {
        TextRenderer tr = textRenderer;
        int y = top;

        UiDraw.text(ctx, tr, UiDraw.fit(tr, headerText(), w), x, y, c(Role.TEXT_DIM));
        y += 16;

        y = drawExtra(ctx, x, y, w);

        search.draw(ctx, in, x, y, w, 20, true);
        y += 26;

        String info = "Выбрано: " + selected.size() + " · Показано: " + shownIds.size();
        UiDraw.text(ctx, tr, UiDraw.fit(tr, info, w), x, y, c(Role.HIGHLIGHT));
        y += 16;

        int listTop = y;
        int n = shownIds.size();
        int first = Math.max(0, (viewT - listTop) / ROW_H);
        int last = Math.min(n, (viewB - listTop) / ROW_H + 2);
        for (int i = first; i < last; i++) {
            int ry = listTop + i * ROW_H;
            String id = shownIds.get(i);
            boolean sel = selected.contains(id);
            boolean hov = in.mouseOver(x, ry, w, ROW_H - 2);

            int fill = c(Role.CARD_BG);
            if (sel) fill = UiTheme.mix(fill, UiTheme.raw(Role.ACCENT), 0.35);
            if (hov) fill = UiTheme.hover(fill);
            UiDraw.box(ctx, x, ry, w, ROW_H - 2, c(sel || hov ? Role.HIGHLIGHT : Role.CARD_BORDER), fill);

            UiDraw.text(ctx, tr, sel ? "✓" : "·", x + 7, ry + 5, c(sel ? Role.OK : Role.TEXT_DIM));
            int dw = drawRowDecoration(ctx, id, x + 22, ry + 1);
            int nx = x + 24 + dw;
            UiDraw.text(ctx, tr, UiDraw.fit(tr, getDisplayName(id), w - (nx - x) - 8), nx, ry + 5,
                    c(Role.TEXT_MAIN));

            in.addClick(x, ry, w, ROW_H - 2, () -> toggle(id));
        }
        if (n == 0) {
            UiDraw.text(ctx, tr, "Ничего не найдено.", x, listTop + 2, c(Role.TEXT_DIM));
            return listTop + 18 - top;
        }
        return listTop + n * ROW_H - top;
    }

    @Override
    protected void drawFooter(DrawContext ctx) {
        TextRenderer tr = textRenderer;
        int fy = viewB + 8;
        String all = "Выбрать всё";
        String none = "Снять всё";
        String done = "Готово";
        int aw = UiChip.width(tr, all);
        int nw = UiChip.width(tr, none);
        int dw = Math.max(UiChip.width(tr, done), 70);

        UiChip.draw(ctx, tr, in, viewL, fy, aw, 20, all, false, true, () -> {
            for (String id : shownIds) if (!selected.contains(id)) selected.add(id);
        });
        UiChip.draw(ctx, tr, in, viewL + aw + 6, fy, nw, 20, none, false, true,
                () -> selected.removeAll(shownIds));
        UiChip.draw(ctx, tr, in, viewR - dw, fy, dw, 20, done, true, true, () -> {
            onDone.accept(new ArrayList<>(selected));
            cfg.save();
            close();
        });
    }
}
