package com.spothelper.minimap;

import net.minecraft.util.Identifier;

import java.util.HashMap;
import java.util.Map;

/**
 * Карта: EntityType ID -> наш 16×16 спрайт-мордашка.
 * Спрайты лежат в assets/spothelper/textures/mob/<name>.png.
 * Для игроков используется скин (см. Minimap.drawPlayerIcon).
 */
public final class MobSprites {

    private MobSprites() {}

    private static final Map<String, Identifier> MAP = new HashMap<>();

    private static void r(String name) {
        MAP.put("minecraft:" + name,
                Identifier.of("spothelper", "textures/mob/" + name + ".png"));
    }

    static {
        r("allay");
        r("armadillo");
        r("axolotl");
        r("bat");
        r("bee");
        r("blaze");
        r("bogged");
        r("breeze");
        r("camel");
        r("cat");
        r("cave_spider");
        r("chicken");
        r("cod");
        r("cow");
        r("creeper");
        r("dolphin");
        r("donkey");
        r("drowned");
        r("elder_guardian");
        r("ender_dragon");
        r("enderman");
        r("endermite");
        r("evoker");
        r("fox");
        r("frog");
        r("ghast");
        r("glow_squid");
        r("goat");
        r("guardian");
        r("hoglin");
        r("horse");
        r("husk");
        r("iron_golem");
        r("llama");
        r("magma_cube");
        r("mooshroom");
        r("mule");
        r("ocelot");
        r("panda");
        r("parrot");
        r("phantom");
        r("pig");
        r("piglin");
        r("piglin_brute");
        r("pillager");
        r("polar_bear");
        r("pufferfish");
        r("rabbit");
        r("ravager");
        r("salmon");
        r("sheep");
        r("shulker");
        r("silverfish");
        r("skeleton");
        r("skeleton_horse");
        r("slime");
        r("snow_golem");
        r("spider");
        r("squid");
        r("stray");
        r("strider");
        r("tadpole");
        r("trader_llama");
        r("tropical_fish");
        r("turtle");
        r("vex");
        r("villager");
        r("vindicator");
        r("wandering_trader");
        r("warden");
        r("witch");
        r("wither");
        r("wither_skeleton");
        r("wolf");
        r("zoglin");
        r("zombie");
        r("zombie_horse");
        r("zombie_villager");
        r("zombified_piglin");
    }

    /** null — спрайта нет, рисуем точку. */
    public static Identifier get(String entityId) {
        return MAP.get(entityId);
    }
}
