package com.spothelper.holyworld;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Русские названия событий, серверов и редкости, а также номер анархии для сортировки.
 * Всё, что уже написано по-русски, возвращается без изменений.
 */
public final class EventTexts {
    private EventTexts() {}

    private static final Map<String, String> EVENT = new LinkedHashMap<>();
    private static final Map<String, String> RARE = new LinkedHashMap<>();
    private static final Pattern DIGITS = Pattern.compile("(\\d{1,4})");

    private static void e(String key, String ru) {
        EVENT.put(key, ru);
    }

    private static void r(String key, String ru) {
        RARE.put(key, ru);
    }

    static {
        e("cube", "Куб");
        e("cubes", "Куб");
        e("ship", "Корабль");
        e("boat", "Корабль");
        e("cargo", "Груз");
        e("airdrop", "Груз");
        e("drop", "Груз");
        e("supply", "Груз");
        e("glade", "Поляна");
        e("meadow", "Поляна");
        e("clearing", "Поляна");
        e("polyana", "Поляна");
        e("trader", "Торговец");
        e("merchant", "Торговец");
        e("vendor", "Торговец");
        e("bunker", "Бункер");
        e("boss", "Босс");
        e("arena", "Арена");
        e("meteor", "Метеорит");
        e("meteorite", "Метеорит");
        e("portal", "Портал");
        e("dragon", "Дракон");
        e("chest", "Сундук");
        e("treasure", "Клад");
        e("hunt", "Охота");
        e("siege", "Осада");
        e("mine", "Шахта");
        e("snowquarry", "Снежный карьер");
        e("snow", "Снежный карьер");
        e("quarry", "Карьер");
        e("parcels", "Посылки");
        e("parcel", "Посылки");
        e("jaycob", "Джейкоб");
        e("jacob", "Джейкоб");
        e("tower", "Башня");
        e("castle", "Замок");
        e("camp", "Лагерь");
        e("rift", "Разлом");
        e("fortress", "Крепость");
        e("temple", "Храм");
        e("raid", "Рейд");
        e("pvp", "ПВП");
        e("lottery", "Лотерея");

        r("common", "Обычный");
        r("uncommon", "Необычный");
        r("rare", "Редкий");
        r("epic", "Эпический");
        r("legendary", "Легендарный");
        r("mythic", "Мифический");
        r("mythical", "Мифический");
        r("overworld", "Верхний мир");
        r("world", "Верхний мир");
        r("nether", "Незер");
        r("the end", "Энд");
        r("end", "Энд");
    }

    /** Все известные события для чипов-фильтров (порядок показа). Новые из API добавляются автоматически. */
    public static final java.util.List<String> KNOWN_EVENTS = java.util.List.of(
            "Куб", "Корабль", "Груз", "Поляна", "Шахта", "Снежный карьер", "Посылки", "Джейкоб");

    private static boolean hasCyrillic(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= 'А' && c <= 'я' || c == 'ё' || c == 'Ё') return true;
        }
        return false;
    }

    /**
     * Название события по-русски. strict = true — переводить только слово целиком
     * (для вариантов голосования, где могут быть ники игроков).
     */
    public static String eventName(String raw, boolean strict) {
        if (raw == null) return "";
        String s = raw.trim();
        if (s.isEmpty() || hasCyrillic(s)) return s;

        String[] tokens = s.toLowerCase(Locale.ROOT).split("[^a-z0-9]+");
        for (String t : tokens) {
            String ru = EVENT.get(t);
            if (ru != null) return ru;
        }
        if (!strict) {
            for (String t : tokens) {
                for (Map.Entry<String, String> en : EVENT.entrySet()) {
                    if (en.getKey().length() >= 4 && t.startsWith(en.getKey())) return en.getValue();
                }
            }
        }
        return s;
    }

    public static String eventName(String raw) {
        return eventName(raw, false);
    }

    /** Редкость или мир по-русски. */
    public static String rare(String raw) {
        if (raw == null) return "";
        String s = raw.trim();
        if (s.isEmpty() || hasCyrillic(s)) return s;
        String key = s.toLowerCase(Locale.ROOT).replace('_', ' ').replace('-', ' ').trim();
        String ru = RARE.get(key);
        return ru != null ? ru : s;
    }

    /** «Anarchy 101» → «Анархия 101», «solo lite 12» → «Соло лайт 12». */
    public static String server(String raw) {
        if (raw == null) return "";
        String s = raw.trim();
        if (s.isEmpty() || hasCyrillic(s)) return s;

        String t = s.replaceAll("[_\\-]+", " ");
        t = t.replaceAll("(?i)\\banarchy\\b", "Анархия")
                .replaceAll("(?i)\\bsolo\\b", "Соло")
                .replaceAll("(?i)\\bduo\\b", "Дуо")
                .replaceAll("(?i)\\btrio\\b", "Трио")
                .replaceAll("(?i)\\bclan\\b", "Клан")
                .replaceAll("(?i)\\b(lite|light)\\b", "Лайт")
                .replaceAll("(?i)\\b(hub|lobby)\\b", "Лобби")
                .replaceAll("(?i)\\bclassic\\b", "Классик");
        return t.equals(s.replaceAll("[_\\-]+", " ")) ? s : t.replaceAll("\\s+", " ").trim();
    }

    /** Номер анархии из названия сервера; без числа — в конец списка. */
    public static int anarchyNumber(String serverName) {
        if (serverName == null) return Integer.MAX_VALUE;
        Matcher m = DIGITS.matcher(serverName);
        if (m.find()) {
            try {
                return Integer.parseInt(m.group(1));
            } catch (NumberFormatException ignored) {
            }
        }
        return Integer.MAX_VALUE;
    }
}
