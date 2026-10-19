package com.spothelper.gui.ui;

import com.spothelper.config.SpotHelperConfig;

/**
 * Единственный источник цветов интерфейса.
 * Базовые цвета лежат в SpotHelperConfig.themeColors (ARGB, порядок = порядок Role),
 * производные (наведение, нажатие, неактивное) считаются здесь и нигде не хранятся.
 */
public final class UiTheme {
    private UiTheme() {}

    public enum Role {
        WINDOW_BG("Фон окна"),
        SIDEBAR_BG("Боковая панель"),
        CARD_BG("Карточка"),
        CARD_BORDER("Рамка карточки"),
        ACCENT("Акцент"),
        HIGHLIGHT("Подсветка"),
        TEXT_MAIN("Основной текст"),
        TEXT_DIM("Второй текст"),
        OK("Успех"),
        ERROR("Ошибка"),
        TOGGLE_OFF("Выключено"),
        SLIDER_TRACK("Трек слайдера");

        public final String label;

        Role(String label) {
            this.label = label;
        }
    }

    public static final int ROLE_COUNT = Role.values().length;

    /** Цвет предупреждения о контрасте. Это не роль палитры, а фиксированный семантический цвет. */
    public static final int WARNING = 0xFFFFD23F;

    public static final String[] PRESET_NAMES = {"Синяя", "Тёмная", "Фиолетовая", "Зелёная"};
    public static final String CUSTOM_NAME = "Своя";
    /** Индекс «Своя» = число пресетов. */
    public static final int CUSTOM = PRESET_NAMES.length;

    // Порядок: WINDOW_BG, SIDEBAR_BG, CARD_BG, CARD_BORDER, ACCENT, HIGHLIGHT,
    //          TEXT_MAIN, TEXT_DIM, OK, ERROR, TOGGLE_OFF, SLIDER_TRACK
    private static final int[][] PRESETS = {
            {0x060D2B, 0x08123A, 0x0A1640, 0x1B3A8A, 0x1A7CFF, 0x3FE6FF, 0xE8F0FF, 0x7F93C4, 0x3DFFB0, 0xFF5A6E, 0x26335F, 0x1B2A60},
            {0x0B0C10, 0x111218, 0x16181F, 0x2C303C, 0x5B8CFF, 0x9FC0FF, 0xECEFF6, 0x8A90A2, 0x4ADE80, 0xF87171, 0x2A2D38, 0x232632},
            {0x120A2B, 0x190E3C, 0x1E1148, 0x4B2A9A, 0x8A4DFF, 0xD08CFF, 0xE9E4FF, 0x9B8FC4, 0x3DFFB0, 0xFF5A8A, 0x33295F, 0x2A1F5C},
            {0x06201A, 0x082A21, 0x0A3025, 0x1B7A56, 0x1FCB7A, 0x7CFFC4, 0xE4FFF3, 0x7FC4A6, 0x9CFF3D, 0xFF6A5A, 0x255F4A, 0x1B5A44}
    };

    // ---------------------------------------------------------------- хранилище

    private static SpotHelperConfig cfg() {
        return SpotHelperConfig.INSTANCE;
    }

    private static int[] opaque(int[] rgb) {
        int[] out = new int[ROLE_COUNT];
        for (int i = 0; i < ROLE_COUNT; i++) out[i] = 0xFF000000 | rgb[i];
        return out;
    }

    /** Массив цветов из конфига; если его нет или он короче — достраивается значениями темы «Синяя». */
    /** Цвета удалённой светлой темы: если они остались в конфиге, при загрузке заменяются на «Синюю». */
    private static final int[] LEGACY_LIGHT = {
            0xF2F5FC, 0xE6EBF7, 0xFFFFFF, 0xC3CDE6, 0x2F6BFF, 0x0A4FC4,
            0x14203D, 0x5A6A8C, 0x0E9F6E, 0xD93A4F, 0xC9D2E8, 0xD6DDEF
    };
    private static boolean legacyChecked = false;

    private static boolean isLegacyLight(int[] a) {
        for (int i = 0; i < ROLE_COUNT; i++) {
            if ((a[i] & 0xFFFFFF) != LEGACY_LIGHT[i]) return false;
        }
        return true;
    }

    /** Массив цветов из конфига; если его нет или он короче — достраивается значениями темы «Синяя». */
    private static int[] arr() {
        int[] a = cfg().themeColors;
        if (a == null || a.length < ROLE_COUNT) {
            int[] n = opaque(PRESETS[0]);
            if (a != null) {
                for (int i = 0; i < a.length; i++) n[i] = 0xFF000000 | a[i];
            }
            cfg().themeColors = n;
            return n;
        }
        if (!legacyChecked) {
            legacyChecked = true;
            if (isLegacyLight(a)) {
                a = opaque(PRESETS[0]);
                cfg().themeColors = a;
                cfg().themePreset = 0;
                cfg().save();
            }
        }
        return a;
    }

    private static int detectPreset() {
        int[] a = arr();
        for (int p = 0; p < PRESETS.length; p++) {
            boolean same = true;
            for (int i = 0; i < ROLE_COUNT; i++) {
                if ((a[i] & 0xFFFFFF) != PRESETS[p][i]) {
                    same = false;
                    break;
                }
            }
            if (same) return p;
        }
        return CUSTOM;
    }

    private static void touch() {
        cfg().themePreset = detectPreset();
        cfg().save();
    }

    public static void save() {
        cfg().save();
    }

    // ---------------------------------------------------------------- чтение

    /** Цвет роли, как его надо рисовать (для фона окна и сайдбара применена прозрачность). */
    public static int get(Role r) {
        int c = raw(r);
        if (r == Role.WINDOW_BG || r == Role.SIDEBAR_BG) {
            c = withAlpha(c, opacity() * 255 / 100);
        }
        return c;
    }

    /** Базовый цвет роли, всегда непрозрачный. */
    public static int raw(Role r) {
        return arr()[r.ordinal()] | 0xFF000000;
    }

    public static String hex(Role r) {
        return String.format("#%06X", raw(r) & 0xFFFFFF);
    }

    public static int opacity() {
        int o = cfg().themeOpacity;
        if (o == 0) return 100; // поле отсутствовало в старом конфиге
        return Math.max(40, Math.min(100, o));
    }

    // ---------------------------------------------------------------- запись

    public static void set(Role r, int rgb) {
        arr()[r.ordinal()] = 0xFF000000 | (rgb & 0xFFFFFF);
        touch();
    }

    public static void applyPreset(int p) {
        if (p < 0 || p >= PRESETS.length) return;
        cfg().themeColors = opaque(PRESETS[p]);
        cfg().themePreset = p;
        cfg().save();
    }

    /** Индекс пресета, если цвета точно совпадают с ним, иначе CUSTOM. */
    public static int presetIndex() {
        return detectPreset();
    }

    /** Сброс одной роли к значению темы «Синяя». */
    public static void resetRole(Role r) {
        set(r, PRESETS[0][r.ordinal()]);
    }

    /** Полный сброс: тема «Синяя» и прозрачность 100%. */
    public static void resetAll() {
        cfg().themeOpacity = 100;
        applyPreset(0);
    }

    /** Меняет прозрачность без сохранения (для перетаскивания); сохранить — save(). */
    public static void setOpacity(int percent) {
        cfg().themeOpacity = Math.max(40, Math.min(100, percent));
    }

    // ---------------------------------------------------------------- производные цвета

    public static int withAlpha(int c, int a) {
        return ((a & 0xFF) << 24) | (c & 0xFFFFFF);
    }

    /** Неактивное состояние: ~40% от исходной альфы. */
    public static int dim(int c) {
        return withAlpha(c, (int) ((c >>> 24) * 0.4));
    }

    /** Смесь RGB; альфа берётся от a. */
    public static int mix(int a, int b, double t) {
        int r = lerp((a >> 16) & 255, (b >> 16) & 255, t);
        int g = lerp((a >> 8) & 255, (b >> 8) & 255, t);
        int bl = lerp(a & 255, b & 255, t);
        return (a & 0xFF000000) | (r << 16) | (g << 8) | bl;
    }

    private static int lerp(int x, int y, double t) {
        return (int) Math.round(x + (y - x) * t);
    }

    public static int hover(int c) {
        return mix(c, raw(Role.HIGHLIGHT), 0.14);
    }

    public static int pressed(int c) {
        return mix(c, raw(Role.WINDOW_BG), 0.45);
    }

    /** Фон активного пункта сайдбара: ACCENT с пониженной яркостью. */
    public static int navActive() {
        return mix(raw(Role.SIDEBAR_BG), raw(Role.ACCENT), 0.35);
    }

    /** Затемнение мира за окном. */
    public static int scrim() {
        return withAlpha(raw(Role.WINDOW_BG), 0x90);
    }

    /** Цвет текста на заданном фоне: TEXT_MAIN, а если контраст < 3:1 — WINDOW_BG, если он лучше. */
    public static int onColor(int bg) {
        int main = raw(Role.TEXT_MAIN);
        if (contrast(main, bg) >= 3.0) return main;
        int alt = raw(Role.WINDOW_BG);
        return contrast(alt, bg) > contrast(main, bg) ? alt : main;
    }

    // ---------------------------------------------------------------- контраст

    private static double lin(int v) {
        double s = v / 255.0;
        return s <= 0.03928 ? s / 12.92 : Math.pow((s + 0.055) / 1.055, 2.4);
    }

    public static double luminance(int c) {
        return 0.2126 * lin((c >> 16) & 255) + 0.7152 * lin((c >> 8) & 255) + 0.0722 * lin(c & 255);
    }

    public static double contrast(int a, int b) {
        double l1 = luminance(a);
        double l2 = luminance(b);
        return (Math.max(l1, l2) + 0.05) / (Math.min(l1, l2) + 0.05);
    }

    public static boolean lowContrast() {
        return contrast(raw(Role.TEXT_MAIN), raw(Role.CARD_BG)) < 3.0;
    }

    // ---------------------------------------------------------------- обмен темой

    /** «SH1:060D2B,08123A,…» — 12 значений RRGGBB в порядке ролей. */
    public static String export() {
        StringBuilder sb = new StringBuilder("SH1:");
        for (int i = 0; i < ROLE_COUNT; i++) {
            if (i > 0) sb.append(',');
            sb.append(String.format("%06X", arr()[i] & 0xFFFFFF));
        }
        return sb.toString();
    }

    /** Разбирает строку темы; при любой ошибке формата ничего не меняет и возвращает false. */
    public static boolean importString(String s) {
        if (s == null) return false;
        s = s.trim();
        if (s.length() < 4 || !s.regionMatches(true, 0, "SH1:", 0, 4)) return false;
        String[] parts = s.substring(4).split(",", -1);
        if (parts.length != ROLE_COUNT) return false;
        int[] rgb = new int[ROLE_COUNT];
        for (int i = 0; i < ROLE_COUNT; i++) {
            String p = parts[i].trim();
            if (p.startsWith("#")) p = p.substring(1);
            if (!p.matches("[0-9A-Fa-f]{6}")) return false;
            rgb[i] = Integer.parseInt(p, 16);
        }
        cfg().themeColors = opaque(rgb);
        touch();
        return true;
    }
}
