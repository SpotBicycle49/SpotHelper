package com.spothelper.util;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.util.Formatting;

import java.text.Normalizer;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

/** Определение «цветных» тотемов HolyWorld (Этернити / Инфинити / Стингер) по названию или цвету названия. */
public final class TotemTint {
    private TotemTint() {}

    /** Для диагностики: сколько раз миксин видел тотем / подменил модель. */

    private static final String SMALL_CAPS = "ᴀʙᴄᴅᴇꜰɢʜɪᴊᴋʟᴍɴᴏᴘǫʀꜱᴛᴜᴠᴡxʏᴢ";
    private static final String LATIN = "abcdefghijklmnopqrstuvwxyz";

    /** id модели spothelper:<id> или null, если тотем обычный. */
    public static String modelFor(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !stack.isOf(Items.TOTEM_OF_UNDYING)) return null;

        Text name = stack.get(DataComponentTypes.CUSTOM_NAME);
        if (name == null) name = stack.get(DataComponentTypes.ITEM_NAME);

        // 1) Название (у своих предметов оно есть и однозначно)
        if (name != null) {
            String plain = normalize(name.getString());
            if (plain.contains("эпич") || plain.contains("epic")) return null; // «Эпический талисман» — обычный
            String byName = byName(name.getString());
            if (byName != null) return byName;
        }

        // 2) Серверные данные (custom_data): приходят и для предметов в руках других игроков,
        //    даже когда название скрыто. Если «личность» есть, но это не наш талисман — красить не надо.
        NbtCompound data = customData(stack);
        if (data != null) {
            NbtCompound sphere = data.getCompoundOrEmpty("sphereEffect");
            // на HolyWorld itemServiceId — это объект {name:"сфера_eternity"}, но на всякий случай читаем и как строку
            NbtCompound serviceObj = data.getCompoundOrEmpty("itemServiceId");
            String service = data.getString("itemServiceId", sphere.getString("itemServiceId", ""));
            String serviceName = serviceObj.getString("name", "");
            String nm = sphere.getString("name", "");
            String rank = sphere.getString("rank", "");
            boolean hasIdentity = !service.isEmpty() || !serviceName.isEmpty() || !nm.isEmpty() || !rank.isEmpty()
                    || sphere.getSize() > 0 || serviceObj.getSize() > 0;
            if (hasIdentity) {
                String m = byData(nm, rank, service, serviceName);
                // последний шанс: ровно одно ключевое слово где-то в данных предмета
                return m != null ? m : single(data.toString());
            }
        }

        // 3) Запасной путь: по цвету названия
        return name == null ? null : byColor(name);
    }

    private static NbtCompound customData(ItemStack stack) {
        NbtComponent c = stack.get(DataComponentTypes.CUSTOM_DATA);
        return c == null ? null : c.copyNbt();
    }

    /** Поля, которые HolyWorld кладёт в custom_data: sphereEffect.name / sphereEffect.rank / itemServiceId. */
    private static String byData(String name, String rank, String service, String serviceName) {
        String m = single(serviceName);
        if (m == null) m = single(name);
        if (m == null) m = single(service);
        if (m == null) m = single(rank);
        return m;
    }

    /** Модель, если строка содержит РОВНО одно из ключевых слов (иначе null — не гадаем). */
    private static String single(String raw) {
        String s = raw == null ? "" : raw.toLowerCase(Locale.ROOT);
        boolean e = s.contains("eternity"), i = s.contains("infinity"), t = s.contains("stinger");
        int n = (e ? 1 : 0) + (i ? 1 : 0) + (t ? 1 : 0);
        if (n != 1) return null;
        return e ? "totem_eternity" : i ? "totem_infinity" : "totem_stinger";
    }

    public static String byName(String raw) {
        String s = normalize(raw);
        if (s.contains("этерн") || s.contains("etern")) return "totem_eternity";
        if (s.contains("инфин") || s.contains("infin")) return "totem_infinity";
        if (s.contains("стинг") || s.contains("sting")) return "totem_stinger";
        return null;
    }

    /** Запасной вариант: оттенок цвета названия (фиолетовый / салатовый / красный). */
    public static String byColor(Text name) {
        int[] rgb = {-1};
        name.visit((style, str) -> {
            if (rgb[0] == -1 && !str.isBlank() && style.getColor() != null) {
                TextColor c = style.getColor();
                rgb[0] = c.getRgb();
            }
            return java.util.Optional.empty();
        }, net.minecraft.text.Style.EMPTY);
        if (rgb[0] == -1) {
            TextColor c = name.getStyle().getColor();
            if (c != null) rgb[0] = c.getRgb();
        }
        if (rgb[0] == -1) return null;

        float r = ((rgb[0] >> 16) & 255) / 255f, g = ((rgb[0] >> 8) & 255) / 255f, b = (rgb[0] & 255) / 255f;
        float max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b));
        float d = max - min;
        if (max < 0.35f || d / max < 0.4f) return null; // серый/тёмный
        float h;
        if (max == r) h = ((g - b) / d) % 6f;
        else if (max == g) h = (b - r) / d + 2f;
        else h = (r - g) / d + 4f;
        h *= 60f;
        if (h < 0) h += 360f;

        if (h >= 250f && h <= 335f) return "totem_eternity";
        if (h >= 75f && h <= 160f) return "totem_infinity";
        if (h <= 20f || h >= 340f) return "totem_stinger";
        return null;
    }

    private static String normalize(String raw) {
        String s = Formatting.strip(raw == null ? "" : raw);
        if (s == null) s = "";
        s = Normalizer.normalize(s, Normalizer.Form.NFKD);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            int sc = SMALL_CAPS.indexOf(ch);
            if (sc >= 0) ch = LATIN.charAt(sc);
            if (Character.isLetter(ch)) sb.append(Character.toLowerCase(ch));
        }
        return sb.toString().toLowerCase(Locale.ROOT);
    }
}
