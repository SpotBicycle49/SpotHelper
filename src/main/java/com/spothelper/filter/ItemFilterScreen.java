package com.spothelper.filter;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

public class ItemFilterScreen extends AbstractFilterScreen {

    private static final String[] CATEGORIES = {
            "Броня", "Оружие", "Инструменты", "Еда",
            "Блоки", "Руды", "Ингредиенты", "Зелья",
            "Зачарованные книги", "Транспорт", "Разное"
    };

    private int currentCategory = 0;

    public ItemFilterScreen(Screen parent, List<String> initial, Consumer<List<String>> onDone) {
        super(parent, "Фильтр предметов", initial, onDone);
    }

    @Override
    protected void collectIds() {
        Set<String> ids = new HashSet<>();
        for (String cat : categoryItems(currentCategory)) {
            Identifier ident = Identifier.tryParse(cat);
            if (ident == null) continue;
            Item item = Registries.ITEM.get(ident);
            if (item != Items.AIR) ids.add(cat);
        }
        allIds.addAll(ids);
        allIds.sort(String::compareTo);
    }

    @Override
    protected String headerText() {
        return "Категория: " + CATEGORIES[currentCategory];
    }

    @Override
    protected String getDisplayName(String id) {
        Identifier ident = Identifier.tryParse(id);
        if (ident == null) return id;
        Item item = Registries.ITEM.get(ident);
        if (item == Items.AIR) return id;
        return new ItemStack(item).getName().getString();
    }

    @Override
    protected int drawRowDecoration(DrawContext context, String id, int x, int y) {
        Identifier ident = Identifier.tryParse(id);
        if (ident == null) return 0;
        Item item = Registries.ITEM.get(ident);
        if (item == Items.AIR) return 0;
        ItemStack stack = new ItemStack(item);
        context.drawItem(stack, x, y);
        return 20;
    }

    @Override
    protected int drawExtra(DrawContext ctx, int x, int y, int w) {
        Runnable[] acts = new Runnable[CATEGORIES.length];
        boolean[] act = new boolean[CATEGORIES.length];
        for (int i = 0; i < CATEGORIES.length; i++) {
            final int idx = i;
            acts[i] = () -> {
                if (currentCategory != idx) {
                    currentCategory = idx;
                    reload();
                }
            };
            act[i] = idx == currentCategory;
        }
        return flowChips(ctx, x, y, w, CATEGORIES, acts, act, null);
    }

    private List<String> categoryItems(int category) {
        List<String> list = new ArrayList<>();
        switch (category) {
            case 0:
                for (Item item : Registries.ITEM) {
                    String id = Registries.ITEM.getId(item).toString();
                    if (id.contains("helmet") || id.contains("chestplate") || id.contains("leggings") || id.contains("boots")) list.add(id);
                }
                break;
            case 1:
                list.add("minecraft:wooden_sword");
                list.add("minecraft:stone_sword");
                list.add("minecraft:iron_sword");
                list.add("minecraft:golden_sword");
                list.add("minecraft:diamond_sword");
                list.add("minecraft:netherite_sword");
                list.add("minecraft:bow");
                list.add("minecraft:crossbow");
                list.add("minecraft:trident");
                list.add("minecraft:arrow");
                break;
            case 2:
                for (Item item : Registries.ITEM) {
                    String id = Registries.ITEM.getId(item).toString();
                    if (id.contains("pickaxe") || id.contains("axe") || id.contains("shovel")
                            || id.contains("hoe") || id.contains("shears") || id.contains("fishing_rod")
                            || id.contains("flint_and_steel")) list.add(id);
                }
                break;
            case 3:
                for (Item item : Registries.ITEM) {
                    String id = Registries.ITEM.getId(item).toString();
                    if (id.contains("apple") || id.contains("bread") || id.contains("beef")
                            || id.contains("chicken") || id.contains("porkchop") || id.contains("cooked")
                            || id.contains("carrot") || id.contains("potato") || id.contains("melon")
                            || id.contains("cookie") || id.contains("cake") || id.contains("pie")
                            || id.contains("stew") || id.contains("soup") || id.contains("mutton")
                            || id.contains("rabbit") || id.contains("cod") || id.contains("salmon")
                            || id.contains("tropical_fish") || id.contains("pufferfish") || id.contains("kelp")
                            || id.contains("dried_kelp") || id.contains("honey_bottle")) list.add(id);                }
                break;
            case 4:
                for (Item item : Registries.ITEM) {
                    if (item instanceof net.minecraft.item.BlockItem) {
                        String id = Registries.ITEM.getId(item).toString();
                        if (!id.contains("_ore") && !id.contains("_block")) list.add(id);
                    }
                }
                break;
            case 5:
                for (Item item : Registries.ITEM) {
                    String id = Registries.ITEM.getId(item).toString();
                    if (id.contains("_ore") || id.equals("minecraft:diamond") || id.equals("minecraft:emerald")
                            || id.equals("minecraft:gold_ingot") || id.equals("minecraft:iron_ingot")
                            || id.equals("minecraft:coal") || id.equals("minecraft:redstone")
                            || id.equals("minecraft:lapis_lazuli") || id.equals("minecraft:netherite_ingot")
                            || id.equals("minecraft:netherite_scrap")) list.add(id);
                }
                break;
            case 6:
                for (Item item : Registries.ITEM) {
                    String id = Registries.ITEM.getId(item).toString();
                    if (!(item instanceof net.minecraft.item.BlockItem)
                            && !id.contains("apple") && !id.contains("bread") && !id.contains("beef")
                            && !id.contains("_sword") && !id.contains("_pickaxe")
                            && !id.contains("_axe") && !id.contains("_shovel")
                            && !id.contains("_hoe") && !id.contains("helmet")
                            && !id.contains("chestplate") && !id.contains("leggings")
                            && !id.contains("boots") && !id.contains("potion")
                            && !id.contains("_boat") && !id.contains("_minecart")
                            && !id.contains("enchanted_book")) list.add(id);
                }
                break;
            case 7:
                for (Item item : Registries.ITEM) {
                    String id = Registries.ITEM.getId(item).toString();
                    if (id.contains("potion") || id.contains("brewing_stand") || id.contains("blaze_powder")
                            || id.contains("nether_wart") || id.contains("glistering_melon")) list.add(id);
                }
                break;
            case 8:
                list.add("minecraft:enchanted_book");
                break;
            case 9:
                for (Item item : Registries.ITEM) {
                    String id = Registries.ITEM.getId(item).toString();
                    if (id.contains("_boat") || id.contains("_minecart")
                            || id.equals("minecraft:minecart") || id.equals("minecraft:saddle")) list.add(id);
                }
                break;
            case 10:
            default:
                Set<String> covered = new HashSet<>();
                for (int c = 0; c < 10; c++) covered.addAll(categoryItems(c));
                for (Item item : Registries.ITEM) {
                    String id = Registries.ITEM.getId(item).toString();
                    if (!covered.contains(id)) list.add(id);
                }
                break;
        }
        return list;
    }
}