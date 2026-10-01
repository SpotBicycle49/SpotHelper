package com.spothelper.client;

import com.spothelper.SpotHelperConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.Window;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * SpotHelper GUI - neon blue style.
 *
 * Раскладка задана в "дизайн-пикселях" (панель 1600x800) и масштабируется целиком.
 * Текст рисуется отдельно: для текущего размера окна выбирается шрифт с подходящим
 * разрешением (font/r*.json, font/b*.json), а размер и позиция подгоняются так, чтобы
 * один пиксель шрифта = один пиксель экрана. Отсюда чёткие гладкие буквы при любом масштабе.
 */
public class SpotHelperScreen extends Screen {

    // ---- палитра ----
    private static final int WHITE       = 0xFFFFFFFF;
    private static final int TEXT_DIM    = 0xFF4AC3FF;
    private static final int TEXT_SUB    = 0xFF1F93FF;
    private static final int CYAN        = 0xFF17DDEB;
    private static final int CYAN_BAR    = 0xFF1FD6FF;
    private static final int TEXT_WORLD  = 0xFFC3D6FF;
    private static final int TEXT_TIME   = 0xFFE4ECFF;
    private static final int OK_GREEN    = 0xFF3DFFB0;
    private static final int WARN        = 0xFFFFB347;

    private static final int GLOW_RGB    = 0x1E6BFF;
    private static final int RIM         = 0xFF1660F0;
    private static final int BORDER      = 0xFF1F5FE8;
    private static final int BORDER_HOT  = 0xFF3FA6FF;
    private static final int BORDER_CYAN = 0xFF2EC4FF;

    private static final int BODY_TOP    = 0xFF030A29;
    private static final int BODY_BOT    = 0xFF061446;
    private static final int SHELL_TOP   = 0xFF040D33;
    private static final int SHELL_BOT   = 0xFF05134A;
    private static final int CARD_TOP    = 0xFF061340;
    private static final int CARD_BOT    = 0xFF040C2F;

    // ---- раскладка (дизайн-пиксели) ----
    private static final int DW = 1600;
    private static final int DH = 800;

    private static final int TAB_Y = 20, TAB_H = 72, TAB_X = 28, TAB_GAP = 16;
    private static final int SHELL_X = 16, SHELL_Y = 108, SHELL_W = DW - 32, SHELL_H = DH - 108 - 14;
    private static final int PAD_X = 40;
    private static final int TITLE_CY = 139, SUBTITLE_CY = 167;
    private static final int PILL_Y = 191, PILL_H = 48, PILL_GAP = 15;
    private static final int BTN_W = 196, BTN_H = 66, BTN_Y = 122;
    private static final int CARDS_Y = 255, CARD_H = 96, ROW_STEP = 105, CARD_GAP = 20;
    private static final int CLIP_TOP = 246, CLIP_BOTTOM = SHELL_Y + SHELL_H - 8;

    // ---- размеры текста (множитель к базовому размеру 14) ----
    private static final float SC_TAB = 1.55f;
    private static final float SC_TITLE = 1.75f;
    private static final float SC_SUBTITLE = 1.1f;
    private static final float SC_PILL = 1.2f;
    private static final float SC_CARD_TITLE = 1.4f;
    private static final float SC_CARD_SUB = 1.2f;
    private static final float SC_CARD_INFO = 1.15f;
    /** Смещение центра глифа от верха строки (в единицах шрифта). Если текст выше/ниже центра - поправь. */
    private static final float TEXT_CY = 4.0f;

    // ---- шрифты: набор разрешений (oversample) ----
    private static final float[] LV = {1f, 1.25f, 1.5f, 1.75f, 2f, 2.5f, 3f, 3.5f, 4f, 5f, 6f, 7f, 8f};
    private static final Identifier[][] FONTS = new Identifier[2][LV.length];
    static {
        for (int i = 0; i < LV.length; i++) {
            String tag = String.valueOf(Math.round(LV[i] * 100));
            FONTS[0][i] = new Identifier("spothelper", "r" + tag);
            FONTS[1][i] = new Identifier("spothelper", "b" + tag);
        }
    }

    private static final String[] EVENT_FILTERS = {"Все", "Кубик", "Корабль", "Груз", "Поляна", "Торговец", "Бункер"};
    private static final String[] WORLD_FILTERS = {"Обычный мир", "Ад", "Энд"};

    private final Screen parent;
    private final HolyWorldApiClient api;
    private HolyWorldData.Snapshot data;

    private int mainTab = 0;
    private int mineWorldTab = 0;
    private int eventFilter = 0;
    private int scroll = 0;
    private boolean busy = false;
    private int guardFrames = -1;                    // кадры после открытия, пока действует предохранитель шрифта
    private static boolean cursorTried = false;
    /** Во сколько раз увеличить обычный шрифт Minecraft в безопасном режиме (он мельче DejaVu). */
    private static final float SAFE_TEXT = 1.45f;

    // ---- размер / позиция окна ----
    private static final float GRAB = 7.0f;
    private static final float MIN_SCALE = 0.35f;
    private static final long[] CURSORS = new long[5];
    private float userScale;
    private float offX, offY;
    private boolean resizing = false;
    private int dragEdge = 0;
    private int hoverEdge = 0;
    private int currentCursor = 0;

    // ---- перенос окна / клик по карточке ----
    private boolean pressing = false;
    private boolean moved = false;
    private int pressCard = -1;
    private double pressX, pressY;
    private float pressOffX, pressOffY;

    // ---- события на экране и обратная связь ----
    private List<HolyWorldData.EventRow> shownEvents = new ArrayList<HolyWorldData.EventRow>();

    public SpotHelperScreen(Screen parent) {
        super(new LiteralText("SpotHelper"));
        this.parent = parent;
        this.api = SpotHelperClient.API;
        this.data = api.getSnapshot();
        SpotHelperConfig cfg = SpotHelperConfig.INSTANCE;
        this.userScale = cfg.guiScale;
        this.offX = cfg.guiOffsetX;
        this.offY = cfg.guiOffsetY;
    }

    @Override
    protected void init() {
        api.refreshStatus(null);
        api.refreshEvents(null);
        api.refreshMinesCached(null);
        mainTab = 0;
        eventFilter = 0;
        mineWorldTab = 0;
        scroll = 0;
        guardFrames = 0;
        if (fancy()) GuiGuard.begin("font");
    }

    private static boolean fancy() {
        return SpotHelperConfig.INSTANCE.fancyText == 1;
    }

    // ------------------------------------------------------------------ масштаб и позиция

    private float fitScale() {
        return Math.min((width - 8.0f) / DW, (height - 8.0f) / DH);
    }

    private float scale() {
        float fit = fitScale();
        float s = userScale > 0 ? userScale : Math.min(1.0f, fit);
        return Math.max(Math.min(MIN_SCALE, fit), Math.min(fit, s));
    }

    private float clampLeft(float v, float k) {
        return Math.max(80.0f - DW * k, Math.min(width - 80.0f, v));
    }

    private float clampTop(float v, float k) {
        return Math.max(0.0f, Math.min(height - 40.0f, v));
    }

    private float left() {
        float k = scale();
        return clampLeft((width - DW * k) / 2.0f + offX, k);
    }

    private float top() {
        float k = scale();
        return clampTop((height - DH * k) / 2.0f + offY, k);
    }

    private void applyOffset(float ox, float oy) {
        float k = scale();
        offX = clampLeft((width - DW * k) / 2.0f + ox, k) - (width - DW * k) / 2.0f;
        offY = clampTop((height - DH * k) / 2.0f + oy, k) - (height - DH * k) / 2.0f;
    }

    private void saveLayout() {
        SpotHelperConfig cfg = SpotHelperConfig.INSTANCE;
        cfg.guiScale = userScale > 0 ? scale() : 0f;
        cfg.guiOffsetX = offX;
        cfg.guiOffsetY = offY;
        cfg.save();
    }

    // ------------------------------------------------------------------ render

    @Override
    public void render(MatrixStack m, int mouseX, int mouseY, float delta) {
        data = api.getSnapshot();
        renderBackground(m);
        fill(m, 0, 0, width, height, 0x90030612);

        float k = scale();
        float mx = (mouseX - left()) / k;
        float my = (mouseY - top()) / k;

        hoverEdge = resizing ? dragEdge : (pressing && moved ? 0 : edgeAt(mouseX, mouseY));
        int cursor = cursorFor(hoverEdge);
        if (cursor == 0 && !pressing && isInteractive(mx, my)) cursor = 4;
        setCursor(cursor);

        m.push();
        m.translate(left(), top(), 0);
        m.scale(k, k, 1.0f);

        drawPanel(m);
        drawTabs(m, mx, my);
        if (mainTab == 0) drawEventsPage(m, mx, my);
        else drawMinesPage(m, mx, my);

        m.pop();

        if (guardFrames >= 0 && ++guardFrames >= 40) {      // первые кадры прошли - шрифт безопасен
            GuiGuard.end("font");
            guardFrames = -1;
        }
    }

    private void drawPanel(MatrixStack m) {
        boolean hot = hoverEdge != 0;
        glow(m, 0, 0, DW, DH, 36, hot ? 0x3FA6FF : GLOW_RGB, hot ? 0x50 : 0x2A, 4);
        int rim = hot ? BORDER_CYAN : RIM;
        rr(m, 0, 0, DW, DH, 36, rim, rim);
        rr(m, 3, 3, DW - 6, DH - 6, 33, BODY_TOP, BODY_BOT);
    }

    // ------------------------------------------------------------------ вкладки

    private int tabWidth() {
        return (DW - TAB_X * 2 - TAB_GAP) / 2;
    }

    private void drawTabs(MatrixStack m, float mx, float my) {
        int w = tabWidth();
        tab(m, TAB_X, TAB_Y, w, TAB_H, "Events", mainTab == 0, hit(mx, my, TAB_X, TAB_Y, w, TAB_H));
        int x2 = TAB_X + w + TAB_GAP;
        tab(m, x2, TAB_Y, w, TAB_H, "Mines", mainTab == 1, hit(mx, my, x2, TAB_Y, w, TAB_H));
    }

    private void tab(MatrixStack m, int x, int y, int w, int h, String label, boolean active, boolean hover) {
        int r = h / 2;
        if (active) {
            glow(m, x, y, w, h, r, GLOW_RGB, 0x50, 4);
            rr(m, x, y, w, h, r, BORDER_CYAN, BORDER_CYAN);
            rr(m, x + 3, y + 3, w - 6, h - 6, r - 3, 0xFF1878FF, 0xFF0A48D2);
            rr(m, x + 30, y + 6, w - 60, 5, 2, 0x40FFFFFF, 0x40FFFFFF);
        } else {
            if (hover) glow(m, x, y, w, h, r, GLOW_RGB, 0x28, 3);
            int b = hover ? BORDER_HOT : BORDER;
            rr(m, x, y, w, h, r, b, b);
            rr(m, x + 3, y + 3, w - 6, h - 6, r - 3, 0xFF071547, 0xFF040D30);
        }
        textC(m, label, true, x + w / 2.0f, y + h / 2.0f, SC_TAB, active ? WHITE : TEXT_DIM);
    }

    // ------------------------------------------------------------------ шапка секции

    private int refreshX() {
        return DW - PAD_X + 6 - BTN_W;
    }

    private void drawSectionHeader(MatrixStack m, float mx, float my, String title, String subtitle) {
        shell(m);
        text(m, title, true, PAD_X, TITLE_CY, SC_TITLE, WHITE);
        text(m, fancy() ? subtitle : subtitle + " — безопасный режим шрифта (fancyText в config/spothelper.json)",
                false, PAD_X, SUBTITLE_CY, SC_SUBTITLE, TEXT_SUB);
        int bx = refreshX();
        refreshButton(m, bx, BTN_Y, BTN_W, BTN_H, hit(mx, my, bx, BTN_Y, BTN_W, BTN_H));
    }

    private void shell(MatrixStack m) {
        rr(m, SHELL_X, SHELL_Y, SHELL_W, SHELL_H, 30, 0xFF1650D8, 0xFF1650D8);
        rr(m, SHELL_X + 2, SHELL_Y + 2, SHELL_W - 4, SHELL_H - 4, 28, SHELL_TOP, SHELL_BOT);
    }

    private void refreshButton(MatrixStack m, int x, int y, int w, int h, boolean hover) {
        int r = h / 2;
        glow(m, x, y, w, h, r, GLOW_RGB, hover ? 0x60 : 0x40, 3);
        int b = hover ? 0xFF6CCBFF : 0xFF3FB0FF;
        rr(m, x, y, w, h, r, b, b);
        rr(m, x + 3, y + 3, w - 6, h - 6, r - 3, hover ? 0xFF2C88FF : 0xFF1C74FF, hover ? 0xFF1058E8 : 0xFF0A4CD8);
        rr(m, x + 26, y + 6, w - 52, 4, 2, 0x35FFFFFF, 0x35FFFFFF);

        drawRefreshIcon(m, x + 46, y + h / 2);
        text(m, busy ? "..." : "Обновить", true, x + 82, y + h / 2.0f, 1.25f, WHITE);
    }

    private void drawRefreshIcon(MatrixStack m, int cx, int cy) {
        final double R = 11.0;
        for (int a = 20; a >= -250; a -= 9) {
            double rad = Math.toRadians(a);
            int px = (int) Math.round(cx + Math.cos(rad) * R);
            int py = (int) Math.round(cy - Math.sin(rad) * R);
            fill(m, px - 1, py - 1, px + 2, py + 2, WHITE);
        }
        int ax = (int) Math.round(cx + Math.cos(Math.toRadians(104)) * R) - 3;
        int ay = (int) Math.round(cy - Math.sin(Math.toRadians(104)) * R);
        for (int i = 0; i < 7; i++) {
            int half = 6 - i;
            fill(m, ax + i, ay - half, ax + i + 1, ay + half + 1, WHITE);
        }
    }

    // ------------------------------------------------------------------ плашки-фильтры

    private String[] pillLabels() {
        return mainTab == 0 ? EVENT_FILTERS : WORLD_FILTERS;
    }

    /** {x[], w[]} для плашек текущей страницы. */
    private int[][] pillLayout() {
        String[] labels = pillLabels();
        int[] xs = new int[labels.length];
        int[] ws = new int[labels.length];
        int x = PAD_X - 6;
        for (int i = 0; i < labels.length; i++) {
            int w = Math.max(110, Math.round(tw(labels[i], false, SC_PILL)) + 66);
            if (mainTab == 1) w += 24;
            xs[i] = x;
            ws[i] = w;
            x += w + PILL_GAP;
        }
        return new int[][]{xs, ws};
    }

    private void pill(MatrixStack m, int x, int y, int w, int h, String label, boolean active, boolean hover) {
        int r = h / 2;
        if (active) {
            glow(m, x, y, w, h, r, GLOW_RGB, 0x50, 3);
            rr(m, x, y, w, h, r, BORDER_CYAN, BORDER_CYAN);
            rr(m, x + 3, y + 3, w - 6, h - 6, r - 3, 0xFF1A7CFF, 0xFF0B4FE0);
        } else {
            if (hover) glow(m, x, y, w, h, r, GLOW_RGB, 0x28, 2);
            int b = hover ? BORDER_HOT : BORDER;
            rr(m, x, y, w, h, r, b, b);
            rr(m, x + 3, y + 3, w - 6, h - 6, r - 3, 0xFF061344, 0xFF040C2E);
        }
        textC(m, label, false, x + w / 2.0f, y + h / 2.0f, SC_PILL, active ? 0xFF3FE6FF : (hover ? WHITE : 0xFFB4D4FF));
    }

    private void drawPills(MatrixStack m, float mx, float my, int activeIndex) {
        String[] labels = pillLabels();
        int[][] lay = pillLayout();
        for (int i = 0; i < labels.length; i++) {
            pill(m, lay[0][i], PILL_Y, lay[1][i], PILL_H, labels[i], activeIndex == i,
                    hit(mx, my, lay[0][i], PILL_Y, lay[1][i], PILL_H));
        }
    }

    // ------------------------------------------------------------------ Events

    private void drawEventsPage(MatrixStack m, float mx, float my) {
        drawSectionHeader(m, mx, my, "События", "Показываются только ближайшие события");
        drawPills(m, mx, my, eventFilter);

        shownEvents = filteredEvents();
        List<CardData> cards = new ArrayList<CardData>();
        for (HolyWorldData.EventRow e : shownEvents) {
            String time = e.available ? "через " + e.liveSeconds() + " сек." : "";
            CardData c = new CardData(trim(e.displayName, 34), trim(e.serverName, 48), eventWorld(e), time);
            if (e == EventFlow.badgeRow() && EventFlow.badgeVisible()) {
                c.flash = EventFlow.badgeText();
                c.flashKind = EventFlow.badgeKind();
            }
            cards.add(c);
        }
        drawCards(m, mx, my, cards, "Нет событий", "Для выбранного фильтра пока нет данных.");
    }

    private List<HolyWorldData.EventRow> filteredEvents() {
        List<HolyWorldData.EventRow> result = new ArrayList<HolyWorldData.EventRow>();
        String wanted = eventFilterName(eventFilter);
        for (HolyWorldData.EventRow row : data.events) {
            if (eventFilter == 0 || eventMatches(row, wanted)) result.add(row);
        }
        Collections.sort(result, new Comparator<HolyWorldData.EventRow>() {
            @Override public int compare(HolyWorldData.EventRow a, HolyWorldData.EventRow b) {
                if (a.available && b.available) return Integer.compare(a.liveSeconds(), b.liveSeconds());
                if (a.available) return -1;
                if (b.available) return 1;
                return 0;
            }
        });
        return result;
    }

    private boolean eventMatches(HolyWorldData.EventRow row, String wanted) {
        String value = ((row.displayName == null ? "" : row.displayName) + " "
                + (row.serverName == null ? "" : row.serverName) + " "
                + (row.id == null ? "" : row.id)).toLowerCase(Locale.ROOT);
        return value.contains(wanted.toLowerCase(Locale.ROOT));
    }

    private String eventFilterName(int index) {
        switch (index) {
            case 1: return "куб";
            case 2: return "кораб";
            case 3: return "груз";
            case 4: return "полян";
            case 5: return "торгов";
            case 6: return "бункер";
            default: return "";
        }
    }

    private String eventWorld(HolyWorldData.EventRow e) {
        String v = ((e.rare == null ? "" : e.rare) + " " + (e.id == null ? "" : e.id)).toLowerCase(Locale.ROOT);
        if (v.contains("end") || v.contains("энд")) return "Энд";
        if (v.contains("nether") || v.contains("ад")) return "Ад";
        return e.rare == null || e.rare.trim().isEmpty() ? "Обычный мир" : trim(e.rare, 40);
    }

    // ------------------------------------------------------------------ Mines

    private void drawMinesPage(MatrixStack m, float mx, float my) {
        drawSectionHeader(m, mx, my, "Next Mines", "Показываются только NEXT");
        drawPills(m, mx, my, mineWorldTab);

        List<CardData> cards = new ArrayList<CardData>();
        for (HolyWorldData.MineRow row : visibleMines()) {
            cards.add(new CardData(row.server == null ? "—" : row.server, trim(row.next.mine, 42), worldLabel(row.world),
                    "через " + row.next.liveSeconds(false) + " сек."));
        }
        drawCards(m, mx, my, cards, "Нет шахт", "Для выбранного мира сейчас нет шахт с данными NEXT.");
    }

    private List<HolyWorldData.MineRow> visibleMines() {
        List<HolyWorldData.MineRow> result = new ArrayList<HolyWorldData.MineRow>();
        List<HolyWorldData.MineRow> source = mineWorldTab == 0 ? data.overworld : (mineWorldTab == 1 ? data.nether : data.end);
        for (HolyWorldData.MineRow row : source) {
            if (row != null && row.next != null && row.next.available
                    && row.next.mine != null && !row.next.mine.trim().isEmpty()
                    && !"—".equals(row.next.mine.trim())) {
                result.add(row);
            }
        }
        return result;
    }

    // ------------------------------------------------------------------ карточки

    private static final class CardData {
        final String title, sub, info, time;
        String flash = null;
        int flashKind = 0;              // 1 идёт, 2 готово, 3 ошибка
        CardData(String title, String sub, String info, String time) {
            this.title = title; this.sub = sub; this.info = info; this.time = time;
        }
    }

    private int cardWidth() {
        return (DW - (PAD_X - 10) * 2 - CARD_GAP) / 2;
    }

    private int cardCount() {
        return mainTab == 0 ? shownEvents.size() : visibleMines().size();
    }

    /** Индекс карточки под точкой (в дизайн-координатах) или -1. */
    private int cardAt(float mx, float my) {
        if (my < CLIP_TOP || my > CLIP_BOTTOM || mx < SHELL_X || mx > SHELL_X + SHELL_W) return -1;
        int x0 = PAD_X - 10;
        int cw = cardWidth();
        int count = cardCount();
        for (int i = 0; i < count; i++) {
            int cx = x0 + (i & 1) * (cw + CARD_GAP);
            int cy = CARDS_Y + (i / 2) * ROW_STEP - scroll;
            if (hit(mx, my, cx, cy, cw, CARD_H)) return i;
        }
        return -1;
    }

    private void drawCards(MatrixStack m, float mx, float my, List<CardData> cards, String emptyTitle, String emptyText) {
        int x0 = PAD_X - 10;
        int cw = cardWidth();

        if (cards.isEmpty()) {
            card(m, x0, CARDS_Y, DW - x0 * 2, CARD_H, new CardData(emptyTitle, emptyText, "", ""), false, true);
            return;
        }

        clampScroll(cards.size());
        scissorOn(SHELL_X, CLIP_TOP, SHELL_W, CLIP_BOTTOM - CLIP_TOP);
        int hovered = pressing && moved ? -1 : cardAt(mx, my);
        for (int i = 0; i < cards.size(); i++) {
            int cx = x0 + (i & 1) * (cw + CARD_GAP);
            int cy = CARDS_Y + (i / 2) * ROW_STEP - scroll;
            if (cy + CARD_H < CLIP_TOP || cy > CLIP_BOTTOM) continue;
            card(m, cx, cy, cw, CARD_H, cards.get(i), i == hovered, false);
        }
        scissorOff();
    }

    private void card(MatrixStack m, int x, int y, int w, int h, CardData c, boolean hover, boolean single) {
        int r = 24;
        boolean flashing = c.flash != null;
        int accent = c.flashKind == 2 ? OK_GREEN : (c.flashKind == 3 ? WARN : BORDER_CYAN);
        int accentRgb = c.flashKind == 2 ? 0x2EE6A0 : (c.flashKind == 3 ? 0xFFB347 : 0x2EC4FF);
        glow(m, x, y, w, h, r, flashing ? accentRgb : GLOW_RGB, flashing ? 0x50 : (hover ? 0x40 : 0x1C), flashing ? 3 : 2);
        int b = flashing ? accent : (hover ? BORDER_HOT : BORDER);
        rr(m, x, y, w, h, r, b, b);
        rr(m, x + 3, y + 3, w - 6, h - 6, r - 3, hover ? 0xFF0A1B52 : CARD_TOP, hover ? 0xFF061240 : CARD_BOT);

        if (single) {
            text(m, c.title, true, x + 34, y + 34, SC_CARD_TITLE, WHITE);
            text(m, c.sub, false, x + 34, y + 64, SC_CARD_SUB, TEXT_DIM);
            return;
        }

        rr(m, x + 10, y + 15, 14, h - 30, 7, 0x3018E0FF, 0x3018E0FF);
        rr(m, x + 13, y + 18, 8, h - 36, 4, CYAN_BAR, 0xFF12A8FF);

        int tx = x + 44;
        text(m, c.title, true, tx, y + 26, SC_CARD_TITLE, WHITE);
        text(m, c.sub, false, tx, y + 51, SC_CARD_SUB, CYAN);
        text(m, c.info, false, tx, y + 76, SC_CARD_INFO, TEXT_WORLD);
        if (flashing) {
            text(m, c.flash, true, x + w - 32 - tw(c.flash, true, SC_CARD_INFO), y + 51, SC_CARD_INFO, accent);
        } else if (!c.time.isEmpty()) {
            text(m, c.time, false, x + w - 32 - tw(c.time, false, SC_CARD_INFO), y + 51, SC_CARD_INFO, TEXT_TIME);
        }
    }

    // ------------------------------------------------------------------ ввод

    private boolean isInteractive(float mx, float my) {
        int tabW = tabWidth();
        if (hit(mx, my, TAB_X, TAB_Y, tabW, TAB_H) || hit(mx, my, TAB_X + tabW + TAB_GAP, TAB_Y, tabW, TAB_H)) return true;
        if (hit(mx, my, refreshX(), BTN_Y, BTN_W, BTN_H)) return true;
        int[][] lay = pillLayout();
        for (int i = 0; i < lay[0].length; i++) {
            if (hit(mx, my, lay[0][i], PILL_Y, lay[1][i], PILL_H)) return true;
        }
        int c = cardAt(mx, my);
        return c >= 0 && mainTab == 0;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int edge = edgeAt(mouseX, mouseY);
        if (edge != 0) {
            if (button == 0) {
                resizing = true;
                dragEdge = edge;
                return true;
            }
            if (button == 1) {          // ПКМ у края - сбросить размер и позицию
                userScale = 0;
                offX = 0;
                offY = 0;
                saveLayout();
                return true;
            }
        }
        if (button != 0) return false;

        float k = scale();
        float mx = (float) ((mouseX - left()) / k);
        float my = (float) ((mouseY - top()) / k);

        int tabW = tabWidth();
        if (hit(mx, my, TAB_X, TAB_Y, tabW, TAB_H)) { mainTab = 0; scroll = 0; return true; }
        if (hit(mx, my, TAB_X + tabW + TAB_GAP, TAB_Y, tabW, TAB_H)) { mainTab = 1; scroll = 0; return true; }

        if (hit(mx, my, refreshX(), BTN_Y, BTN_W, BTN_H)) {
            if (!busy) {
                busy = true;
                HolyWorldApiClient.Callback cb = new HolyWorldApiClient.Callback() {
                    @Override public void onSuccess(HolyWorldData.Snapshot snapshot) { busy = false; }
                    @Override public void onError(String message) { busy = false; }
                };
                if (mainTab == 0) api.refreshEventsNow(cb);
                else api.refreshMines(cb);
            }
            return true;
        }

        int[][] lay = pillLayout();
        for (int i = 0; i < lay[0].length; i++) {
            if (hit(mx, my, lay[0][i], PILL_Y, lay[1][i], PILL_H)) {
                if (mainTab == 0) eventFilter = i; else mineWorldTab = i;
                scroll = 0;
                return true;
            }
        }

        // остальное: тянем окно; если отпустили без движения на карточке события - клик
        if (mx >= 0 && mx <= DW && my >= 0 && my <= DH) {
            pressing = true;
            moved = false;
            pressCard = mainTab == 0 ? cardAt(mx, my) : -1;
            pressX = mouseX;
            pressY = mouseY;
            pressOffX = offX;
            pressOffY = offY;
        }
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
        if (resizing) {
            double cx = width / 2.0 + offX;
            double cy = height / 2.0 + offY;
            float sx = (float) (Math.abs(mouseX - cx) * 2.0 / DW);
            float sy = (float) (Math.abs(mouseY - cy) * 2.0 / DH);
            boolean horiz = (dragEdge & 3) != 0;
            boolean vert = (dragEdge & 12) != 0;
            float target = (horiz && vert) ? Math.max(sx, sy) : (horiz ? sx : sy);
            float fit = fitScale();
            userScale = Math.max(Math.min(MIN_SCALE, fit), Math.min(fit, target));
            return true;
        }
        if (pressing) {
            double mdx = mouseX - pressX;
            double mdy = mouseY - pressY;
            if (!moved && Math.sqrt(mdx * mdx + mdy * mdy) > 3.0) moved = true;
            if (moved) applyOffset(pressOffX + (float) mdx, pressOffY + (float) mdy);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (resizing && button == 0) {
            resizing = false;
            dragEdge = 0;
            saveLayout();
            return true;
        }
        if (pressing && button == 0) {
            pressing = false;
            if (moved) {
                saveLayout();
            } else if (pressCard >= 0 && mainTab == 0 && pressCard < shownEvents.size()) {
                float k = scale();
                float mx = (float) ((mouseX - left()) / k);
                float my = (float) ((mouseY - top()) / k);
                if (cardAt(mx, my) == pressCard) EventFlow.start(shownEvents.get(pressCard));
            }
            moved = false;
            pressCard = -1;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double amount) {
        scroll -= (int) (amount * 52);
        clampScroll(cardCount());
        return true;
    }

    private void clampScroll(int count) {
        int rows = Math.max(1, (count + 1) / 2);
        int visible = CLIP_BOTTOM - CARDS_Y;
        int max = Math.max(0, rows * ROW_STEP - (ROW_STEP - CARD_H) - visible);
        if (scroll > max) scroll = max;
        if (scroll < 0) scroll = 0;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) {
            MinecraftClient.getInstance().openScreen(parent);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void removed() {
        if (guardFrames >= 0) {
            GuiGuard.end("font");
            guardFrames = -1;
        }
        setCursor(0);
        super.removed();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ------------------------------------------------------------------ края окна и курсор

    private int edgeAt(double mx, double my) {
        float k = scale();
        float l = left(), t = top();
        float r = l + DW * k, b = t + DH * k;
        if (mx < l - GRAB || mx > r + GRAB || my < t - GRAB || my > b + GRAB) return 0;
        int e = 0;
        if (Math.abs(mx - l) <= GRAB) e |= 1;
        if (Math.abs(mx - r) <= GRAB) e |= 2;
        if (Math.abs(my - t) <= GRAB) e |= 4;
        if (Math.abs(my - b) <= GRAB) e |= 8;
        return e;
    }

    private static int cursorFor(int edge) {
        if (edge == 0) return 0;
        boolean h = (edge & 3) != 0;
        boolean v = (edge & 12) != 0;
        if (h && v) return 3;
        return h ? 1 : 2;
    }

    /** 0 обычный, 1 горизонтальный, 2 вертикальный, 3 крест (угол), 4 рука. */
    private void setCursor(int type) {
        if (SpotHelperConfig.INSTANCE.customCursors != 1) return;
        if (type == currentCursor) return;
        currentCursor = type;
        boolean first = !cursorTried;                   // самый первый вызов GLFW охраняем флагом
        if (first) {
            cursorTried = true;
            GuiGuard.begin("cursor");
        }
        long handle = MinecraftClient.getInstance().getWindow().getHandle();
        if (type == 0) {
            GLFW.glfwSetCursor(handle, 0L);
        } else {
            if (CURSORS[type - 1] == 0L) {
                int shape;
                switch (type) {
                    case 1: shape = GLFW.GLFW_HRESIZE_CURSOR; break;
                    case 2: shape = GLFW.GLFW_VRESIZE_CURSOR; break;
                    case 3: shape = GLFW.GLFW_CROSSHAIR_CURSOR; break;
                    default: shape = GLFW.GLFW_HAND_CURSOR; break;
                }
                CURSORS[type - 1] = GLFW.glfwCreateStandardCursor(shape);
            }
            GLFW.glfwSetCursor(handle, CURSORS[type - 1]);
        }
        if (first) GuiGuard.end("cursor");
    }

    // ------------------------------------------------------------------ текст (чёткий, 1 пиксель шрифта = 1 пиксель экрана)

    private float guiFactor() {
        return (float) MinecraftClient.getInstance().getWindow().getScaleFactor();
    }

    private int lvIndex(float needed) {
        int best = 0;
        double bd = Double.MAX_VALUE;
        for (int i = 0; i < LV.length; i++) {
            double d = Math.abs(Math.log(LV[i] / needed));
            if (d < bd) { bd = d; best = i; }
        }
        return best;
    }

    /** Подбирает разрешение шрифта; возвращает итоговый масштаб текста в GUI-пикселях на единицу шрифта. */
    private float snap(float sc, int[] idxOut) {
        float k = scale();
        if (!fancy()) {                                  // безопасный режим: обычный шрифт Minecraft
            idxOut[0] = 0;
            return sc * k * SAFE_TEXT;
        }
        float g = guiFactor();
        float needed = sc * k * g;
        if (needed < 1.0f) {
            idxOut[0] = 0;
            return sc * k;
        }
        int i = lvIndex(needed);
        idxOut[0] = i;
        if (needed > LV[LV.length - 1] * 1.15f) return sc * k;
        return LV[i] / g;
    }

    private Text mk(String s, boolean bold, int idx) {
        if (!fancy()) return new LiteralText(s).setStyle(bold ? Style.EMPTY.withBold(true) : Style.EMPTY);
        return new LiteralText(s).setStyle(Style.EMPTY.withFont(FONTS[bold ? 1 : 0][idx]));
    }

    /** Ширина строки в дизайн-пикселях. */
    private float tw(String s, boolean bold, float sc) {
        int[] li = new int[1];
        float tsc = snap(sc, li);
        return textRenderer.getWidth(mk(s, bold, li[0])) * tsc / scale();
    }

    private void text(MatrixStack m, String s, boolean bold, float x, float centerY, float sc, int color) {
        if (s == null || s.isEmpty()) return;
        int[] li = new int[1];
        float tsc = snap(sc, li);
        Text t = mk(s, bold, li[0]);
        float g = guiFactor();
        float k = scale();

        float ax = left() + x * k;
        float ay = top() + centerY * k - TEXT_CY * tsc;
        ax = Math.round(ax * g) / g;
        ay = Math.round(ay * g) / g;

        m.push();
        m.peek().getModel().loadIdentity();      // рисуем в абсолютных GUI-координатах
        m.translate(ax, ay, 0);
        m.scale(tsc, tsc, 1.0f);

        float sh = 1.0f / (g * tsc);             // тень ровно 1 пиксель экрана
        m.push();
        m.translate(sh, sh, 0);
        textRenderer.draw(m, t, 0, 0, 0x99010614);
        m.pop();
        textRenderer.draw(m, t, 0, 0, color);
        m.pop();
    }

    private void textC(MatrixStack m, String s, boolean bold, float centerX, float centerY, float sc, int color) {
        text(m, s, bold, centerX - tw(s, bold, sc) / 2.0f, centerY, sc, color);
    }

    // ------------------------------------------------------------------ примитивы

    private boolean hit(float mx, float my, int x, int y, int w, int h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    private void rr(MatrixStack m, int x, int y, int w, int h, int r, int c1, int c2) {
        if (w <= 0 || h <= 0) return;
        r = Math.max(0, Math.min(r, Math.min(w / 2, h / 2)));
        for (int row = 0; row < r; row++) {
            int in = inset(r, row);
            fill(m, x + in, y + row, x + w - in, y + row + 1, lerp(c1, c2, (row + 0.5f) / h));
            int rb = h - 1 - row;
            fill(m, x + in, y + rb, x + w - in, y + rb + 1, lerp(c1, c2, (rb + 0.5f) / h));
        }
        int y0 = y + r;
        int y1 = y + h - r;
        if (y1 > y0) {
            if (c1 == c2) fill(m, x, y0, x + w, y1, c1);
            else fillGradient(m, x, y0, x + w, y1, lerp(c1, c2, (float) r / h), lerp(c1, c2, (float) (h - r) / h));
        }
    }

    private static int inset(int r, int row) {
        double dy = r - row - 0.5;
        return (int) Math.ceil(r - Math.sqrt(Math.max(0.0, r * r - dy * dy)));
    }

    private void glow(MatrixStack m, int x, int y, int w, int h, int r, int rgb, int maxAlpha, int layers) {
        for (int i = layers; i >= 1; i--) {
            int a = maxAlpha * (layers - i + 1) / layers;
            int c = (a << 24) | rgb;
            rr(m, x - i * 3, y - i * 3, w + i * 6, h + i * 6, r + i * 3, c, c);
        }
    }

    private static int lerp(int a, int b, float t) {
        if (a == b) return a;
        int aa = (int) (((a >>> 24) & 0xFF) + ((((b >>> 24) & 0xFF) - ((a >>> 24) & 0xFF)) * t));
        int ar = (int) (((a >> 16) & 0xFF) + ((((b >> 16) & 0xFF) - ((a >> 16) & 0xFF)) * t));
        int ag = (int) (((a >> 8) & 0xFF) + ((((b >> 8) & 0xFF) - ((a >> 8) & 0xFF)) * t));
        int ab = (int) ((a & 0xFF) + (((b & 0xFF) - (a & 0xFF)) * t));
        return (aa << 24) | (ar << 16) | (ag << 8) | ab;
    }

    private void scissorOn(float x, float y, float w, float h) {
        Window win = MinecraftClient.getInstance().getWindow();
        double sf = win.getScaleFactor();
        float k = scale();
        int sx = (int) Math.round((left() + x * k) * sf);
        int sw = (int) Math.round(w * k * sf);
        int sh = (int) Math.round(h * k * sf);
        int sy = (int) Math.round(win.getFramebufferHeight() - (top() + (y + h) * k) * sf);
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(sx, sy, Math.max(0, sw), Math.max(0, sh));
    }

    private void scissorOff() {
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
    }

    private static String trim(String s, int max) {
        if (s == null) return "";
        if (s.length() <= max) return s;
        return s.substring(0, Math.max(0, max - 1)) + "…";
    }

    private static String worldLabel(String world) {
        if ("overworld".equalsIgnoreCase(world)) return "Обычный мир";
        if ("nether".equalsIgnoreCase(world)) return "Ад";
        if ("end".equalsIgnoreCase(world)) return "Энд";
        return world == null ? "—" : world;
    }
}
