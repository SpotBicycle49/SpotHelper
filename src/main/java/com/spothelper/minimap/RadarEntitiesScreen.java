package com.spothelper.minimap;

import com.spothelper.config.SpotHelperConfig;
import com.spothelper.gui.ui.UiChip;
import com.spothelper.gui.ui.UiDraw;
import com.spothelper.gui.ui.UiField;
import com.spothelper.gui.ui.UiScreen;
import com.spothelper.gui.ui.UiSlider;
import com.spothelper.gui.ui.UiTheme;
import com.spothelper.gui.ui.UiTheme.Role;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Размеры иконок радара по типам сущностей. Оформление — как в главном меню. */
public class RadarEntitiesScreen extends UiScreen {

    private static final int ROW_H = 20;

    private final SpotHelperConfig cfg = SpotHelperConfig.INSTANCE;
    private final List<String> allIds = new ArrayList<>();
    private final List<String> shownIds = new ArrayList<>();
    private final UiField search = new UiField("Поиск по ID", 64);
    private final UiSlider sizeSlider = new UiSlider(2, 24, 1, 48);
    private String selectedId;

    public RadarEntitiesScreen(Screen parent) {
        super(parent, "Радар: размеры по типам");
        field(search);
        search.onChange = s -> {
            refreshShown();
            scroll = 0;
        };

        for (EntityType<?> type : Registries.ENTITY_TYPE) {
            allIds.add(Registries.ENTITY_TYPE.getId(type).toString());
        }
        allIds.sort(String::compareTo);
        refreshShown();

        sizeSlider.format = v -> v + " px";
        sizeSlider.onLive = v -> {
            if (selectedId != null) cfg.radarEntitySize.put(selectedId, v);
        };
        sizeSlider.onCommit = v -> cfg.save();
    }

    @Override
    protected int maxWidth() {
        return 460;
    }

    @Override
    protected int footerHeight() {
        return 56;
    }

    @Override
    protected void afterInit() {
        focus(search);
    }

    @Override
    protected void onBack() {
        cfg.save();
        close();
    }

    private void refreshShown() {
        shownIds.clear();
        String q = search.text().trim().toLowerCase(Locale.ROOT);
        for (String id : allIds) {
            if (q.isEmpty() || id.toLowerCase(Locale.ROOT).contains(q)) shownIds.add(id);
        }
    }

    private int guessCategory(String id) {
        Identifier ident = Identifier.tryParse(id);
        if (ident == null) return 4;
        EntityType<?> t = Registries.ENTITY_TYPE.get(ident);
        if (t == EntityType.PLAYER) return 0;
        SpawnGroup g = t.getSpawnGroup();
        if (g == SpawnGroup.MONSTER) return 1;
        if (g == SpawnGroup.MISC) return 4;
        return 2;
    }

    @Override
    protected int drawBody(DrawContext ctx, int x, int top, int w) {
        TextRenderer tr = textRenderer;
        int y = top;

        UiDraw.text(ctx, tr, UiDraw.fit(tr, "Без размера в списке берётся размер категории (вкладка «Радар»).", w),
                x, y, c(Role.TEXT_DIM));
        y += 16;
        search.draw(ctx, in, x, y, w, 20, true);
        y += 26;

        int listTop = y;
        int n = shownIds.size();
        int first = Math.max(0, (viewT - listTop) / ROW_H);
        int last = Math.min(n, (viewB - listTop) / ROW_H + 2);
        for (int i = first; i < last; i++) {
            int ry = listTop + i * ROW_H;
            String id = shownIds.get(i);
            boolean sel = id.equals(selectedId);
            boolean hov = in.mouseOver(x, ry, w, ROW_H - 2);
            Integer override = cfg.radarEntitySize.get(id);

            int fill = c(Role.CARD_BG);
            if (sel) fill = UiTheme.mix(fill, UiTheme.raw(Role.ACCENT), 0.35);
            if (hov) fill = UiTheme.hover(fill);
            UiDraw.box(ctx, x, ry, w, ROW_H - 2, c(sel || hov ? Role.HIGHLIGHT : Role.CARD_BORDER), fill);

            String right = override != null ? override + " px" : "";
            int rw = right.isEmpty() ? 0 : tr.getWidth(right) + 8;
            UiDraw.text(ctx, tr, UiDraw.fit(tr, id, w - 16 - rw), x + 8, ry + 5,
                    c(override != null ? Role.OK : Role.TEXT_MAIN));
            if (override != null) UiDraw.textRight(ctx, tr, right, x + w - 8, ry + 5, c(Role.OK));

            in.addClick(x, ry, w, ROW_H - 2, () -> selectedId = id);
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
        int w = viewR - viewL;

        if (selectedId == null) {
            UiDraw.text(ctx, tr, "Выбери сущность в списке", viewL, fy + 3, c(Role.TEXT_DIM));
        } else {
            if (in.dragging != sizeSlider) {
                sizeSlider.setValue(cfg.getRadarSizeFor(selectedId, guessCategory(selectedId)));
            }
            int lw = w * 2 / 5;
            UiDraw.text(ctx, tr, UiDraw.fit(tr, selectedId, lw - 6), viewL, fy + 3, c(Role.TEXT_MAIN));
            sizeSlider.draw(ctx, tr, in, viewL + lw, fy, w - lw, true);
        }

        int by = fy + 24;
        String reset = "Сбросить размер";
        String done = "Готово";
        int rw = UiChip.width(tr, reset);
        int dw = Math.max(UiChip.width(tr, done), 70);
        UiChip.draw(ctx, tr, in, viewL, by, rw, 20, reset, false, selectedId != null, () -> {
            cfg.radarEntitySize.remove(selectedId);
            cfg.save();
        });
        UiChip.draw(ctx, tr, in, viewR - dw, by, dw, 20, done, true, true, this::onBack);
    }
}
