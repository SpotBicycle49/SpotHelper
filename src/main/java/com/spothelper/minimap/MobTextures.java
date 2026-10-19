package com.spothelper.minimap;

import net.minecraft.util.Identifier;

import java.util.HashMap;
import java.util.Map;

/**
 * Текстуры и UV-координаты морд мобов для отрисовки на миникарте.
 * Формат UV: {u, v, w, h, texW, texH}.
 * По умолчанию морда 8×8 в текстуре 64×64.
 */
public final class MobTextures {

    private MobTextures() {}

    public static final class Face {
        public final Identifier texture;
        public final int u, v, w, h, texW, texH;

        public Face(String path, int u, int v, int w, int h, int texW, int texH) {
            this.texture = Identifier.of("minecraft", path);
            this.u = u; this.v = v;
            this.w = w; this.h = h;
            this.texW = texW; this.texH = texH;
        }
        public Face(String path, int u, int v, int w, int h) {
            this(path, u, v, w, h, 64, 64);
        }
    }

    private static final Map<String, Face> FACES = new HashMap<>();

    /** Возвращает морду моба или null, если её нет — тогда нарисуем яйцо/точку. */
    public static Face get(String id) {
        return FACES.get(id);
    }

    private static void add(String id, String path, int u, int v) {
        FACES.put(id, new Face(path, u, v, 8, 8, 64, 64));
    }
    private static void add16(String id, String path, int u, int v) {
        FACES.put(id, new Face(path, u, v, 16, 16, 64, 64));
    }
    private static void add(String id, String path, int u, int v, int texW, int texH) {
        FACES.put(id, new Face(path, u, v, 8, 8, texW, texH));
    }
    private static void addFace(String id, String path, int u, int v, int w, int h, int texW, int texH) {
        FACES.put(id, new Face(path, u, v, w, h, texW, texH));
    }

    static {
        // ==================== ВРАЖДЕБНЫЕ ====================

        add("minecraft:zombie", "textures/entity/zombie/zombie.png", 8, 8);
        add("minecraft:husk", "textures/entity/zombie/husk.png", 8, 8);
        add("minecraft:drowned", "textures/entity/zombie/drowned.png", 8, 8);
        add("minecraft:zombie_villager", "textures/entity/zombie_villager/zombie_villager.png", 8, 8);
        add("minecraft:zombified_piglin", "textures/entity/piglin/zombified_piglin.png", 8, 8);

        add("minecraft:skeleton", "textures/entity/skeleton/skeleton.png", 8, 8);
        add("minecraft:stray", "textures/entity/skeleton/stray.png", 8, 8);
        add("minecraft:bogged", "textures/entity/skeleton/bogged.png", 8, 8);
        add("minecraft:wither_skeleton", "textures/entity/skeleton/wither_skeleton.png", 8, 8);

        add("minecraft:creeper", "textures/entity/creeper/creeper.png", 8, 8);
        add("minecraft:spider", "textures/entity/spider/spider.png", 32, 4);
        add("minecraft:cave_spider", "textures/entity/spider/cave_spider.png", 32, 4);

        add("minecraft:enderman", "textures/entity/enderman/enderman.png", 0, 0);
        add("minecraft:endermite", "textures/entity/endermite.png", 0, 0);
        add("minecraft:shulker", "textures/entity/shulker/shulker.png", 0, 0);

        add("minecraft:witch", "textures/entity/witch.png", 8, 8);
        add("minecraft:evoker", "textures/entity/illager/evoker.png", 8, 8);
        add("minecraft:illusioner", "textures/entity/illager/illusioner.png", 8, 8);
        add("minecraft:vindicator", "textures/entity/illager/vindicator.png", 8, 8);
        add("minecraft:pillager", "textures/entity/illager/pillager.png", 8, 8);
        add("minecraft:ravager", "textures/entity/illager/ravager.png", 0, 0);
        add("minecraft:vex", "textures/entity/illager/vex.png", 0, 0);

        add("minecraft:slime", "textures/entity/slime/slime.png", 0, 0);
        add("minecraft:magma_cube", "textures/entity/slime/magmacube.png", 0, 0);

        add("minecraft:blaze", "textures/entity/blaze.png", 0, 0);
        add("minecraft:ghast", "textures/entity/ghast/ghast.png", 0, 0, 64, 32);
        add("minecraft:hoglin", "textures/entity/hoglin/hoglin.png", 0, 0);
        add("minecraft:zoglin", "textures/entity/hoglin/zoglin.png", 0, 0);

        add("minecraft:piglin", "textures/entity/piglin/piglin.png", 8, 8);
        add("minecraft:piglin_brute", "textures/entity/piglin/piglin_brute.png", 8, 8);

        add("minecraft:guardian", "textures/entity/guardian.png", 8, 8);
        add16("minecraft:elder_guardian", "textures/entity/guardian_elder.png", 8, 8);

        add("minecraft:silverfish", "textures/entity/silverfish.png", 0, 0);
        add("minecraft:phantom", "textures/entity/phantom.png", 0, 0);
        add("minecraft:bat", "textures/entity/bat.png", 0, 0);

        add16("minecraft:wither", "textures/entity/wither/wither.png", 0, 0);
        add16("minecraft:ender_dragon", "textures/entity/enderdragon/dragon.png", 0, 0);
        add16("minecraft:warden", "textures/entity/warden/warden.png", 0, 0);

        // ==================== МИРНЫЕ ====================

        add("minecraft:villager", "textures/entity/villager/villager.png", 8, 8);
        add("minecraft:wandering_trader", "textures/entity/wandering_trader.png", 8, 8);
        add("minecraft:iron_golem", "textures/entity/iron_golem/iron_golem.png", 0, 0);
        add("minecraft:snow_golem", "textures/entity/snow_golem.png", 0, 0);

        add("minecraft:cow", "textures/entity/cow/cow.png", 0, 0);
        add("minecraft:mooshroom", "textures/entity/cow/red_mooshroom.png", 0, 0);
        add("minecraft:sheep", "textures/entity/sheep/sheep.png", 0, 0);
        add("minecraft:pig", "textures/entity/pig/pig.png", 0, 0);
        add("minecraft:chicken", "textures/entity/chicken.png", 0, 0);
        add("minecraft:rabbit", "textures/entity/rabbit/brown.png", 0, 0);

        add("minecraft:horse", "textures/entity/horse/horse_brown.png", 0, 0);
        add("minecraft:donkey", "textures/entity/horse/donkey.png", 0, 0);
        add("minecraft:mule", "textures/entity/horse/mule.png", 0, 0);
        add("minecraft:llama", "textures/entity/llama/llama_creamy.png", 0, 0);
        add("minecraft:trader_llama", "textures/entity/llama/llama_creamy.png", 0, 0);
        add("minecraft:camel", "textures/entity/camel/camel.png", 0, 0);

        add("minecraft:wolf", "textures/entity/wolf/wolf.png", 0, 0);
        add("minecraft:cat", "textures/entity/cat/tabby.png", 0, 0);
        add("minecraft:ocelot", "textures/entity/cat/ocelot.png", 0, 0);
        add("minecraft:fox", "textures/entity/fox/fox.png", 0, 0);
        add("minecraft:panda", "textures/entity/panda/panda.png", 0, 0);
        add("minecraft:polar_bear", "textures/entity/bear/polarbear.png", 0, 0);
        add("minecraft:goat", "textures/entity/goat/goat.png", 0, 0);

        add("minecraft:dolphin", "textures/entity/dolphin.png", 0, 0);
        add("minecraft:squid", "textures/entity/squid/squid.png", 0, 0);
        add("minecraft:glow_squid", "textures/entity/squid/glow_squid.png", 0, 0);
        add("minecraft:turtle", "textures/entity/turtle/big_sea_turtle.png", 0, 0);
        add("minecraft:axolotl", "textures/entity/axolotl/axolotl_lucy.png", 0, 0);
        add("minecraft:frog", "textures/entity/frog/frog_temperate.png", 0, 0);
        add("minecraft:tadpole", "textures/entity/tadpole/tadpole.png", 0, 0);

        add("minecraft:parrot", "textures/entity/parrot/parrot_red_blue.png", 0, 0);
        add("minecraft:bee", "textures/entity/bee/bee.png", 0, 0);
        add("minecraft:allay", "textures/entity/allay/allay.png", 0, 0);
        add("minecraft:sniffer", "textures/entity/sniffer/sniffer.png", 0, 0);
        add("minecraft:armadillo", "textures/entity/armadillo.png", 0, 0);

        // ==================== ПРОЧИЕ ====================

        add("minecraft:end_crystal", "textures/entity/end_crystal/end_crystal.png", 0, 0);
        add("minecraft:boat", "textures/entity/boat/oak.png", 0, 0);
    }
}