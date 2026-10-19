package com.spothelper.minimap;

import com.spothelper.gui.ui.UiDraw;
import com.spothelper.gui.ui.UiTheme;
import com.spothelper.gui.ui.UiTheme.Role;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Иконка сущности 16×16 для списков:
 *   1) спрайт с миникарты (MobSprites);
 *   2) если спрайта нет — яйцо призыва из креативного меню (или предмет с тем же ID: вагонетка, рамка…);
 *   3) если нет и его — рамка с «?».
 */
public final class EntityIcons {
    private EntityIcons() {}

    /** Ширина, которую занимает иконка вместе с отступом. */
    public static final int WIDTH = 18;

    private static final Map<String, ItemStack> STACKS = new HashMap<>();
    private static final Set<String> BAD_SPRITES = new HashSet<>();

    /** Рисует иконку с левым верхним углом (x, y); возвращает занятую ширину. */
    public static int draw(DrawContext ctx, TextRenderer tr, String id, int x, int y) {
        if (!BAD_SPRITES.contains(id)) {
            Identifier sprite = MobSprites.get(id);
            if (sprite != null) {
                try {
                    ctx.drawTexture(RenderPipelines.GUI_TEXTURED, sprite,
                            x, y,
                            0f, 0f,
                            16, 16,
                            16, 16,
                            0xFFFFFFFF);
                    return WIDTH;
                } catch (Throwable t) {
                    BAD_SPRITES.add(id);
                }
            }
        }

        ItemStack stack = stackFor(id);
        if (!stack.isEmpty()) {
            try {
                ctx.drawItem(stack, x, y);
                return WIDTH;
            } catch (Throwable ignored) {
                STACKS.put(id, ItemStack.EMPTY);
            }
        }

        UiDraw.box(ctx, x + 1, y + 1, 14, 14, UiTheme.get(Role.CARD_BORDER), UiTheme.get(Role.SLIDER_TRACK));
        UiDraw.text(ctx, tr, "?", x + 6, y + 4, UiTheme.get(Role.TEXT_DIM));
        return WIDTH;
    }

    /** Предмет-иконка для типа сущности, как в креативном меню (яйцо призыва или одноимённый предмет). */
    public static ItemStack stackFor(String id) {
        ItemStack cached = STACKS.get(id);
        if (cached != null) return cached;

        ItemStack result = ItemStack.EMPTY;
        Identifier ident = Identifier.tryParse(id);
        if (ident != null) {
            Item egg = Registries.ITEM.get(Identifier.of(ident.getNamespace(), ident.getPath() + "_spawn_egg"));
            if (egg != Items.AIR) {
                result = new ItemStack(egg);
            } else {
                Item same = Registries.ITEM.get(ident);
                if (same != Items.AIR) result = new ItemStack(same);
            }
        }
        STACKS.put(id, result);
        return result;
    }
}
