package com.spothelper.minimap;

import net.minecraft.entity.EntityType;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.util.HashMap;
import java.util.Map;

/** Русские названия сущностей. Для неизвестных (моды, новые версии) берётся перевод клиента. */
public final class EntityNamesRu {
    private EntityNamesRu() {}

    private static final Map<String, String> RU = new HashMap<>();
    private static final Map<String, String> CACHE = new HashMap<>();

    private static void n(String path, String ru) {
        RU.put("minecraft:" + path, ru);
    }

    static {
        // ---- враждебные ----
        n("blaze", "Ифрит");
        n("bogged", "Болотник");
        n("breeze", "Вихрь");
        n("camel_husk", "Верблюд-кадавр");
        n("cave_spider", "Пещерный паук");
        n("creaking", "Скрип");
        n("creeper", "Крипер");
        n("drowned", "Утопленник");
        n("elder_guardian", "Древний страж");
        n("ender_dragon", "Дракон Края");
        n("enderman", "Странник Края");
        n("endermite", "Эндермит");
        n("evoker", "Заклинатель");
        n("ghast", "Гаст");
        n("giant", "Гигант");
        n("guardian", "Страж");
        n("hoglin", "Хоглин");
        n("husk", "Кадавр");
        n("illusioner", "Иллюзионист");
        n("magma_cube", "Магмовый куб");
        n("parched", "Иссушенный скелет");
        n("phantom", "Фантом");
        n("piglin", "Пиглин");
        n("piglin_brute", "Пиглин-громила");
        n("pillager", "Разбойник");
        n("ravager", "Разоритель");
        n("shulker", "Шалкер");
        n("silverfish", "Чешуйница");
        n("skeleton", "Скелет");
        n("slime", "Слизень");
        n("spider", "Паук");
        n("stray", "Зимогор");
        n("vex", "Досаждатель");
        n("vindicator", "Поборник");
        n("warden", "Хранитель");
        n("witch", "Ведьма");
        n("wither", "Иссушитель");
        n("wither_skeleton", "Скелет-иссушитель");
        n("zoglin", "Зоглин");
        n("zombie", "Зомби");
        n("zombie_villager", "Зомби-житель");
        n("zombified_piglin", "Зомбифицированный пиглин");

        // ---- мирные ----
        n("allay", "Тихоня");
        n("armadillo", "Броненосец");
        n("axolotl", "Аксолотль");
        n("bat", "Летучая мышь");
        n("bee", "Пчела");
        n("camel", "Верблюд");
        n("cat", "Кошка");
        n("chicken", "Курица");
        n("cod", "Треска");
        n("copper_golem", "Медный голем");
        n("cow", "Корова");
        n("dolphin", "Дельфин");
        n("donkey", "Осёл");
        n("fox", "Лиса");
        n("frog", "Лягушка");
        n("glow_squid", "Светящийся спрут");
        n("goat", "Коза");
        n("happy_ghast", "Счастливый гаст");
        n("horse", "Лошадь");
        n("iron_golem", "Железный голем");
        n("llama", "Лама");
        n("mooshroom", "Грибная корова");
        n("mule", "Мул");
        n("ocelot", "Оцелот");
        n("panda", "Панда");
        n("parrot", "Попугай");
        n("pig", "Свинья");
        n("polar_bear", "Белый медведь");
        n("pufferfish", "Иглобрюх");
        n("rabbit", "Кролик");
        n("salmon", "Лосось");
        n("sheep", "Овца");
        n("skeleton_horse", "Лошадь-скелет");
        n("sniffer", "Нюхач");
        n("snow_golem", "Снежный голем");
        n("squid", "Спрут");
        n("strider", "Лавомер");
        n("tadpole", "Головастик");
        n("trader_llama", "Лама торговца");
        n("tropical_fish", "Тропическая рыба");
        n("turtle", "Черепаха");
        n("villager", "Житель");
        n("wandering_trader", "Странствующий торговец");
        n("wolf", "Волк");
        n("zombie_horse", "Лошадь-зомби");

        // ---- прочие ----
        n("area_effect_cloud", "Облако эффекта");
        n("armor_stand", "Стойка для брони");
        n("arrow", "Стрела");
        n("block_display", "Дисплей блока");
        n("breeze_wind_charge", "Заряд ветра вихря");
        n("chest_minecart", "Вагонетка с сундуком");
        n("command_block_minecart", "Вагонетка с командным блоком");
        n("dragon_fireball", "Огненный шар дракона");
        n("egg", "Яйцо");
        n("end_crystal", "Кристалл Края");
        n("ender_pearl", "Жемчуг Края");
        n("evoker_fangs", "Клыки заклинателя");
        n("experience_bottle", "Пузырёк опыта");
        n("experience_orb", "Шар опыта");
        n("eye_of_ender", "Око Края");
        n("falling_block", "Падающий блок");
        n("fireball", "Огненный шар");
        n("firework_rocket", "Фейерверк");
        n("fishing_bobber", "Поплавок");
        n("furnace_minecart", "Вагонетка с печью");
        n("glow_item_frame", "Светящаяся рамка");
        n("hopper_minecart", "Вагонетка с воронкой");
        n("interaction", "Взаимодействие");
        n("item_display", "Дисплей предмета");
        n("item_frame", "Рамка");
        n("leash_knot", "Узел поводка");
        n("lightning_bolt", "Молния");
        n("lingering_potion", "Туманное зелье");
        n("llama_spit", "Плевок ламы");
        n("mannequin", "Манекен");
        n("marker", "Маркер");
        n("minecart", "Вагонетка");
        n("ominous_item_spawner", "Зловещий создатель предметов");
        n("painting", "Картина");
        n("potion", "Зелье");
        n("shulker_bullet", "Снаряд шалкера");
        n("small_fireball", "Малый огненный шар");
        n("snowball", "Снежок");
        n("spawner_minecart", "Вагонетка со спавнером");
        n("spectral_arrow", "Спектральная стрела");
        n("splash_potion", "Взрывное зелье");
        n("text_display", "Текстовый дисплей");
        n("tnt", "Динамит");
        n("tnt_minecart", "Вагонетка с динамитом");
        n("trident", "Трезубец");
        n("wind_charge", "Заряд ветра");
        n("wither_skull", "Череп иссушителя");

        // лодки и плоты
        String[][] woods = {
                {"oak", "Дубовая"}, {"spruce", "Еловая"}, {"birch", "Берёзовая"}, {"jungle", "Тропическая"},
                {"acacia", "Акациевая"}, {"dark_oak", "Тёмно-дубовая"}, {"cherry", "Вишнёвая"},
                {"mangrove", "Мангровая"}, {"pale_oak", "Бледно-дубовая"}
        };
        for (String[] w : woods) {
            n(w[0] + "_boat", w[1] + " лодка");
            n(w[0] + "_chest_boat", w[1] + " лодка с сундуком");
        }
        n("bamboo_raft", "Бамбуковый плот");
        n("bamboo_chest_raft", "Бамбуковый плот с сундуком");
    }

    /** Русское название по ID. Результат кэшируется. */
    public static String name(String id) {
        String cached = CACHE.get(id);
        if (cached != null) return cached;

        String ru = RU.get(id);
        if (ru == null) {
            ru = fallback(id);
        }
        CACHE.put(id, ru);
        return ru;
    }

    private static String fallback(String id) {
        try {
            Identifier ident = Identifier.tryParse(id);
            if (ident != null && Registries.ENTITY_TYPE.containsId(ident)) {
                EntityType<?> type = Registries.ENTITY_TYPE.get(ident);
                String s = type.getName().getString();
                if (s != null && !s.isEmpty()) return s;
            }
        } catch (Throwable ignored) {
        }
        return id;
    }
}
