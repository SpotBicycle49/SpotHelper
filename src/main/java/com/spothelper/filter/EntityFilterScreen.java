package com.spothelper.filter;

import com.spothelper.minimap.EntityCategories;
import com.spothelper.minimap.EntityCategories.Cat;
import com.spothelper.minimap.EntityIcons;
import com.spothelper.minimap.EntityNamesRu;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;

import java.util.List;
import java.util.function.Consumer;

/**
 * Фильтр сущностей одной категории. В списке только сущности этой категории
 * (EntityCategories), с русскими названиями и иконками с миникарты.
 */
public class EntityFilterScreen extends AbstractFilterScreen {

    private final String categoryName;
    private final Cat cat;

    public EntityFilterScreen(Screen parent, String categoryName, List<String> initial, Consumer<List<String>> onDone) {
        super(parent, "Фильтр: " + categoryName, initial, onDone);
        this.categoryName = categoryName;
        this.cat = switch (categoryName) {
            case "Враждебные" -> Cat.HOSTILE;
            case "Мирные" -> Cat.FRIENDLY;
            default -> Cat.OTHER;
        };
        // Старые списки могли содержать сущности из других категорий — убираем их.
        this.selected.retainAll(EntityCategories.idsOf(cat));
    }

    @Override
    protected void collectIds() {
        allIds.addAll(EntityCategories.idsOf(cat));
    }

    @Override
    protected String headerText() {
        String what = switch (cat) {
            case HOSTILE -> "монстры";
            case FRIENDLY -> "животные, водные существа, жители и големы";
            case OTHER -> "лодки, вагонетки, стойки, рамки, снаряды и прочее";
        };
        return categoryName + ": " + what + " (" + allIds.size() + ")";
    }

    @Override
    protected String getDisplayName(String id) {
        return EntityNamesRu.name(id);
    }

    @Override
    protected int drawRowDecoration(DrawContext ctx, String id, int x, int y) {
        return EntityIcons.draw(ctx, textRenderer, id, x, y);
    }
}
