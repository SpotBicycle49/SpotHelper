package com.spothelper.minimap;

import com.spothelper.config.SpotHelperConfig;
import com.spothelper.filter.EntityFilterScreen;
import com.spothelper.filter.ItemFilterScreen;
import com.spothelper.gui.ui.UiCard;
import com.spothelper.gui.ui.UiChip;
import com.spothelper.gui.ui.UiDraw;
import com.spothelper.gui.ui.UiScreen;
import com.spothelper.gui.ui.UiSlider;
import com.spothelper.gui.ui.UiTheme.Role;
import com.spothelper.gui.ui.UiToggle;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;
import java.util.function.IntSupplier;

/** Настройки миникарты: вкладки «Вид», «Сущности», «Радар», «Цвета». Оформление — как в главном меню. */
public class MinimapSettingsScreen extends UiScreen {

    private enum Tab {
        VIEW("Вид"), ENTITIES("Сущности"), RADAR("Радар"), COLORS("Цвета");

        final String title;

        Tab(String title) {
            this.title = title;
        }
    }

    private static final String[] RADAR_NAMES = {"Игроки", "Враждебные", "Мирные", "Предметы", "Прочие"};
    private static final String[] CORNERS = {"Верх-лево", "Верх-право", "Низ-лево", "Низ-право"};
    private static final String[] BORDERS = {"Нет", "Рамка", "Свечение"};

    private final SpotHelperConfig cfg = SpotHelperConfig.INSTANCE;
    private final Map<String, UiSlider> sliders = new HashMap<>();
    private Tab tab = Tab.VIEW;

    public MinimapSettingsScreen(Screen parent) {
        super(parent, "Настройки миникарты");
    }

    @Override
    protected int maxWidth() {
        return 600;
    }

    // ---------------------------------------------------------------- каркас

    @Override
    protected int drawBody(DrawContext ctx, int x, int top, int w) {
        Tab[] all = Tab.values();
        String[] names = new String[all.length];
        Runnable[] acts = new Runnable[all.length];
        boolean[] act = new boolean[all.length];
        for (int i = 0; i < all.length; i++) {
            final Tab t = all[i];
            names[i] = t.title;
            acts[i] = () -> {
                if (tab != t) {
                    tab = t;
                    scroll = 0;
                }
            };
            act[i] = t == tab;
        }
        int y = flowChips(ctx, x, top, w, names, acts, act, null);

        y = switch (tab) {
            case VIEW -> drawView(ctx, x, y, w);
            case ENTITIES -> drawEntities(ctx, x, y, w);
            case RADAR -> drawRadar(ctx, x, y, w);
            case COLORS -> drawColors(ctx, x, y, w);
        };
        return y - top;
    }

    // ---------------------------------------------------------------- вкладка «Вид»

    private int drawView(DrawContext ctx, int x, int y, int w) {
        y = slider(ctx, "radius", "Радиус", 0, 1000, 5, 64,
                () -> cfg.minimapRadiusValue, v -> cfg.minimapRadiusValue = v,
                v -> cfg.realMinimapRadius() + " бл.", x, y, w);
        y = slider(ctx, "size", "Размер", 0, 1000, 5, 64,
                () -> cfg.minimapSizeValue, v -> cfg.minimapSizeValue = v,
                v -> cfg.realMinimapSize() + " px", x, y, w);
        y = slider(ctx, "detail", "Детализация", 0, 1000, 5, 64,
                () -> cfg.minimapDetailValue, v -> cfg.minimapDetailValue = v,
                v -> cfg.realMinimapTexSize() + " px", x, y, w);
        y = slider(ctx, "rate", "Скорость обновления", 0, 100, 1, 40,
                () -> cfg.minimapUpdateRate, v -> cfg.minimapUpdateRate = v,
                v -> cfg.minimapUpdateRate + "%", x, y, w);
        y = slider(ctx, "opacity", "Прозрачность", 20, 255, 1, 40,
                () -> cfg.minimapOpacity, v -> cfg.minimapOpacity = v,
                v -> String.valueOf(cfg.minimapOpacity), x, y, w);
        y = slider(ctx, "water", "Прозрачность воды", 0, 100, 1, 40,
                () -> cfg.minimapWaterOpacity, v -> cfg.minimapWaterOpacity = v,
                v -> cfg.minimapWaterOpacity + "%", x, y, w);
        y = slider(ctx, "depth", "Глубина воды", 2, 256, 1, 40,
                () -> cfg.minimapWaterDepth, v -> cfg.minimapWaterDepth = v,
                v -> String.valueOf(cfg.minimapWaterDepth), x, y, w);
        y += 4;

        y = switchRow(ctx, "Режим пещер", cfg::isCaveModeEnabled, () -> cfg.minimapCaveEnabled ^= 1, x, y, w);
        y = switchRow(ctx, "Вращать по взгляду", cfg::isMinimapRotate, () -> cfg.minimapRotate ^= 1, x, y, w);
        y += 4;

        y = choice(ctx, "Угол", CORNERS[Math.floorMod(cfg.minimapCorner, 4)],
                () -> cfg.minimapCorner = (cfg.minimapCorner + 1) % 4, x, y, w);
        y = choice(ctx, "Форма", cfg.minimapShape == 1 ? "Круг" : "Квадрат",
                () -> cfg.minimapShape = cfg.minimapShape == 0 ? 1 : 0, x, y, w);
        y = choice(ctx, "Рамка", BORDERS[Math.floorMod(cfg.minimapBorder, 3)],
                () -> cfg.minimapBorder = (cfg.minimapBorder + 1) % 3, x, y, w);
        y += 4;

        y = switchRow(ctx, "Координаты", cfg::isMinimapCoords, () -> cfg.minimapCoords ^= 1, x, y, w);
        y = switchRow(ctx, "Биом", cfg::isMinimapBiome, () -> cfg.minimapBiome ^= 1, x, y, w);
        y = switchRow(ctx, "Время", cfg::isMinimapTime, () -> cfg.minimapTime ^= 1, x, y, w);
        return y;
    }

    // ---------------------------------------------------------------- вкладка «Сущности»

    private int drawEntities(DrawContext ctx, int x, int y, int w) {
        y = switchRow(ctx, "Игроки", cfg::isMinimapPlayers, () -> cfg.minimapPlayers ^= 1, x, y, w);
        y = entityRow(ctx, "Враждебные", cfg.isMinimapHostile(), () -> cfg.minimapHostile ^= 1,
                cfg.filterModeHostile, () -> cfg.filterModeHostile = cfg.filterModeHostile == 0 ? 1 : 0,
                cfg.filterHostile.size(), () -> openFilter("Враждебные"), x, y, w);
        y = entityRow(ctx, "Мирные", cfg.isMinimapFriendly(), () -> cfg.minimapFriendly ^= 1,
                cfg.filterModeFriendly, () -> cfg.filterModeFriendly = cfg.filterModeFriendly == 0 ? 1 : 0,
                cfg.filterFriendly.size(), () -> openFilter("Мирные"), x, y, w);
        y = entityRow(ctx, "Предметы", cfg.isMinimapItems(), () -> cfg.minimapItems ^= 1,
                cfg.filterModeItems, () -> cfg.filterModeItems = cfg.filterModeItems == 0 ? 1 : 0,
                cfg.filterItems.size(), () -> openFilter("Предметы"), x, y, w);
        y = entityRow(ctx, "Прочие", cfg.isMinimapOther(), () -> cfg.minimapOther ^= 1,
                cfg.filterModeOther, () -> cfg.filterModeOther = cfg.filterModeOther == 0 ? 1 : 0,
                cfg.filterOther.size(), () -> openFilter("Прочие"), x, y, w);
        return y;
    }

    private int entityRow(DrawContext ctx, String name, boolean on, Runnable toggle,
                          int mode, Runnable cycleMode, int count, Runnable open, int x, int y, int w) {
        TextRenderer tr = textRenderer;
        int h = 30;
        UiCard.draw(ctx, tr, in, x, y, w, h, null, null, 0, null);
        UiToggle.draw(ctx, in, x + 8, y + (h - UiToggle.H) / 2, on, true, () -> save(toggle));

        String filter = "Фильтр (" + count + ")";
        int fw = Math.max(80, UiChip.width(tr, filter));
        int fx = x + w - 8 - fw;
        UiChip.draw(ctx, tr, in, fx, y + 6, fw, 18, filter, false, true, open);

        String modeLabel = mode == 0 ? "Только" : "Скрывать";
        int mw = 84;
        int mx = fx - 6 - mw;
        UiChip.draw(ctx, tr, in, mx, y + 6, mw, 18, modeLabel, false, true, () -> save(cycleMode));

        int nx = x + 8 + UiToggle.W + 8;
        UiDraw.text(ctx, tr, UiDraw.fit(tr, name, mx - 8 - nx), nx, y + 11, c(Role.TEXT_MAIN));
        return y + h + 6;
    }

    private void openFilter(String category) {
        MinecraftClient client = MinecraftClient.getInstance();
        switch (category) {
            case "Предметы" -> client.setScreen(new ItemFilterScreen(self(), cfg.filterItems,
                    list -> { cfg.filterItems = list; cfg.save(); }));
            case "Враждебные" -> client.setScreen(new EntityFilterScreen(self(), "Враждебные", cfg.filterHostile,
                    list -> { cfg.filterHostile = list; cfg.save(); }));
            case "Мирные" -> client.setScreen(new EntityFilterScreen(self(), "Мирные", cfg.filterFriendly,
                    list -> { cfg.filterFriendly = list; cfg.save(); }));
            default -> client.setScreen(new EntityFilterScreen(self(), "Прочие", cfg.filterOther,
                    list -> { cfg.filterOther = list; cfg.save(); }));
        }
    }

    // ---------------------------------------------------------------- вкладка «Радар»

    private static String modeName(int m) {
        return switch (m) {
            case 0 -> "Всегда";
            case 2 -> "При Tab";
            default -> "Никогда";
        };
    }

    private int drawRadar(DrawContext ctx, int x, int y, int w) {
        for (int i = 0; i < RADAR_NAMES.length; i++) {
            final int cat = i;
            String iconWord = i == 0 ? "Голова" : "Иконка";

            y += label(ctx, x, y, RADAR_NAMES[i]);
            y = choice(ctx, iconWord, modeName(cfg.radarIconMode[cat]),
                    () -> cfg.radarIconMode[cat] = (cfg.radarIconMode[cat] + 1) % 3, x, y, w);
            y = choice(ctx, "Имя", modeName(cfg.radarNameMode[cat]),
                    () -> cfg.radarNameMode[cat] = (cfg.radarNameMode[cat] + 1) % 3, x, y, w);
            y = slider(ctx, "rsize" + cat, "Размер", 2, 24, 1, 48,
                    () -> cfg.radarSize[cat], v -> cfg.radarSize[cat] = v,
                    v -> cfg.radarSize[cat] + " px", x, y, w);
            y = slider(ctx, "rname" + cat, "Масштаб имени", 50, 250, 5, 48,
                    () -> cfg.radarNameScale[cat], v -> cfg.radarNameScale[cat] = v,
                    v -> cfg.radarNameScale[cat] + "%", x, y, w);
            y += 6;
        }

        y = switchRow(ctx, "Контур иконок", () -> cfg.radarOutline, () -> cfg.radarOutline = !cfg.radarOutline, x, y, w);
        y = slider(ctx, "faces", "Макс. лиц", 0, 300, 1, 40,
                () -> cfg.getRadarMaxFaces(), v -> cfg.radarMaxFaces = v,
                v -> String.valueOf(cfg.getRadarMaxFaces()), x, y, w);
        y += 4;

        String sizes = "Размеры по типам сущностей (" + cfg.radarEntitySize.size() + ") ›";
        y = flowChips(ctx, x, y, w, new String[]{sizes},
                new Runnable[]{() -> MinecraftClient.getInstance().setScreen(new RadarEntitiesScreen(self()))},
                null, null);
        return y;
    }

    // ---------------------------------------------------------------- вкладка «Цвета»

    private int drawColors(DrawContext ctx, int x, int y, int w) {
        y = colorRow(ctx, "Цвет: игроки", () -> cfg.mmColorPlayer, v -> cfg.mmColorPlayer = v, x, y, w);
        y = colorRow(ctx, "Цвет: враждебные", () -> cfg.mmColorHostile, v -> cfg.mmColorHostile = v, x, y, w);
        y = colorRow(ctx, "Цвет: мирные", () -> cfg.mmColorFriendly, v -> cfg.mmColorFriendly = v, x, y, w);
        y = colorRow(ctx, "Цвет: предметы", () -> cfg.mmColorItem, v -> cfg.mmColorItem = v, x, y, w);
        y = colorRow(ctx, "Цвет: прочие", () -> cfg.mmColorOther, v -> cfg.mmColorOther = v, x, y, w);
        return y;
    }

    private int colorRow(DrawContext ctx, String label, IntSupplier get, IntConsumer set, int x, int y, int w) {
        TextRenderer tr = textRenderer;
        int h = 28;
        int argb = get.getAsInt();
        String hex = String.format("#%02X%02X%02X", (argb >> 16) & 0xFF, (argb >> 8) & 0xFF, argb & 0xFF);
        int hexW = tr.getWidth(hex);
        UiCard.draw(ctx, tr, in, x, y, w, h, label, null, hexW + 40, () ->
                MinecraftClient.getInstance().setScreen(new ColorPickerScreen(self(), label, get.getAsInt(), v -> {
                    set.accept(v);
                    cfg.save();
                })));
        int hexX = x + w - 8 - hexW;
        int sw = 14;
        int swX = hexX - 8 - sw;
        UiDraw.frame(ctx, swX - 1, y + (h - sw) / 2 - 1, sw + 2, sw + 2, c(Role.TEXT_DIM));
        UiDraw.rect(ctx, swX, y + (h - sw) / 2, sw, sw, 0xFF000000 | (argb & 0xFFFFFF));
        UiDraw.text(ctx, tr, hex, hexX, y + 10, c(Role.HIGHLIGHT));
        return y + h + 6;
    }

    // ---------------------------------------------------------------- общие элементы

    private void save(Runnable change) {
        change.run();
        cfg.save();
    }

    private int switchRow(DrawContext ctx, String label, BooleanSupplier state, Runnable toggle, int x, int y, int w) {
        toggleCard(ctx, x, y, w, 28, label, null, state.getAsBoolean(), () -> save(toggle));
        return y + 34;
    }

    /** Строка «название — кнопка с текущим значением»; клик переключает значение. */
    private int choice(DrawContext ctx, String label, String value, Runnable next, int x, int y, int w) {
        TextRenderer tr = textRenderer;
        int cw = Math.min(130, Math.max(80, w / 3));
        UiDraw.text(ctx, tr, UiDraw.fit(tr, label, w - cw - 12), x, y + 6, c(Role.TEXT_MAIN));
        UiChip.draw(ctx, tr, in, x + w - cw, y, cw, 20, value, false, true, () -> save(next));
        return y + 26;
    }

    private int slider(DrawContext ctx, String key, String label, int min, int max, int step, int valueW,
                       IntSupplier get, IntConsumer set, IntFunction<String> fmt, int x, int y, int w) {
        TextRenderer tr = textRenderer;
        UiSlider s = sliders.computeIfAbsent(key, k -> {
            UiSlider n = new UiSlider(min, max, step, valueW);
            n.onLive = set::accept;
            n.onCommit = v -> cfg.save();
            return n;
        });
        s.format = fmt;
        if (in.dragging != s) s.setValue(get.getAsInt());

        int lw = Math.min(170, w * 2 / 5);
        UiDraw.text(ctx, tr, UiDraw.fit(tr, label, lw - 6), x, y + 3, c(Role.TEXT_MAIN));
        s.draw(ctx, tr, in, x + lw, y, w - lw, true);
        return y + 24;
    }
}
