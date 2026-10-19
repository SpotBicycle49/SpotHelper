package com.spothelper.minimap;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Единая классификация сущностей для радара и для экранов фильтров.
 * Радар и список в фильтре берут категорию отсюда, поэтому то, что выбрано в «Мирные»,
 * действительно относится к мирным и именно так фильтруется на карте.
 *
 * Правила:
 *   • SpawnGroup.MONSTER                        -> Враждебные
 *   • животные, водные, летучие мыши, аксолотли -> Мирные
 *   • жители, торговец, големы (группа MISC)    -> Мирные
 *   • всё остальное (лодки, вагонетки, стойки, снаряды, рамки…) -> Прочие
 * Игроки и предметы сюда не входят: у них свои настройки (игроки — без фильтра,
 * предметы — через ItemFilterScreen).
 */
public final class EntityCategories {
    private EntityCategories() {}

    public enum Cat { HOSTILE, FRIENDLY, OTHER }

    private static final Set<String> FRIENDLY_MISC = Set.of(
            "minecraft:villager",
            "minecraft:wandering_trader",
            "minecraft:iron_golem",
            "minecraft:snow_golem",
            "minecraft:copper_golem"
    );

    private static final Map<EntityType<?>, Cat> CAT_CACHE = new HashMap<>();
    private static final Map<EntityType<?>, String> ID_CACHE = new HashMap<>();
    private static final Map<Cat, List<String>> LIST_CACHE = new EnumMap<>(Cat.class);

    /** ID вида minecraft:cow (с кэшем, чтобы не создавать строки каждый кадр). */
    public static String idOf(EntityType<?> type) {
        String id = ID_CACHE.get(type);
        if (id == null) {
            id = Registries.ENTITY_TYPE.getId(type).toString();
            ID_CACHE.put(type, id);
        }
        return id;
    }

    public static Cat of(EntityType<?> type) {
        Cat c = CAT_CACHE.get(type);
        if (c == null) {
            c = compute(type);
            CAT_CACHE.put(type, c);
        }
        return c;
    }

    private static Cat compute(EntityType<?> type) {
        SpawnGroup g = type.getSpawnGroup();
        if (g == SpawnGroup.MONSTER) return Cat.HOSTILE;
        if (g == SpawnGroup.MISC) return FRIENDLY_MISC.contains(idOf(type)) ? Cat.FRIENDLY : Cat.OTHER;
        return Cat.FRIENDLY;
    }

    /** ID всех сущностей категории, отсортированные по русскому названию. */
    public static List<String> idsOf(Cat cat) {
        List<String> cached = LIST_CACHE.get(cat);
        if (cached != null) return cached;

        List<String> ids = new ArrayList<>();
        for (EntityType<?> type : Registries.ENTITY_TYPE) {
            String id = idOf(type);
            if (id.equals("minecraft:player") || id.equals("minecraft:item")) continue;
            if (of(type) == cat) ids.add(id);
        }
        ids.sort((a, b) -> EntityNamesRu.name(a).compareToIgnoreCase(EntityNamesRu.name(b)));
        LIST_CACHE.put(cat, ids);
        return ids;
    }
}
