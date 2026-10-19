package com.spothelper.sellprotect;

import com.spothelper.config.SpotHelperConfig;
import com.spothelper.gui.ui.UiDraw;
import com.spothelper.gui.ui.UiScreen;
import com.spothelper.gui.ui.UiTheme;
import com.spothelper.gui.ui.UiTheme.Role;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryWrapper;

import java.util.ArrayList;
import java.util.List;

/** Добавление предмета в защиту из инвентаря. ЛКМ — ID + NBT, ПКМ — только ID. */
public class SellProtectInvScreen extends UiScreen {

    private static final int SLOT = 32;
    private static final int GAP = 4;
    private static final int COLS = 9;
    private static final int ROWS = 4;

    private final SpotHelperConfig cfg = SpotHelperConfig.INSTANCE;
    /** Слоты текущего кадра: {x, y, индекс в инвентаре}. */
    private final List<int[]> slots = new ArrayList<>();
    private ItemStack hovered = ItemStack.EMPTY;

    public SellProtectInvScreen(Screen parent) {
        super(parent, "Из инвентаря");
    }

    @Override
    protected int maxWidth() {
        return COLS * (SLOT + GAP) + 40;
    }

    @Override
    protected void onBack() {
        cfg.save();
        close();
    }

    @Override
    protected int drawBody(DrawContext ctx, int x, int top, int w) {
        TextRenderer tr = textRenderer;
        MinecraftClient mc = MinecraftClient.getInstance();
        RegistryWrapper.WrapperLookup registries = mc.world != null ? mc.world.getRegistryManager() : null;
        slots.clear();
        hovered = ItemStack.EMPTY;

        int y = top;
        UiDraw.text(ctx, tr, UiDraw.fit(tr, "ЛКМ — ID + NBT (точно), ПКМ — только ID (любой NBT).", w),
                x, y, c(Role.TEXT_DIM));
        y += 18;

        int cols = Math.max(1, Math.min(COLS, (w + GAP) / (SLOT + GAP)));
        int step = SLOT + GAP;
        int total = ROWS * COLS;
        for (int i = 0; i < total; i++) {
            int sx = x + (i % cols) * step;
            int sy = y + (i / cols) * step;
            slots.add(new int[]{sx, sy, i});

            boolean over = in.mouseOver(sx, sy, SLOT, SLOT);
            UiDraw.box(ctx, sx, sy, SLOT, SLOT, c(over ? Role.HIGHLIGHT : Role.CARD_BORDER), c(Role.CARD_BG));
            if (mc.player == null) continue;
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.isEmpty()) continue;

            ctx.drawItem(stack, sx + (SLOT - 16) / 2, sy + (SLOT - 16) / 2);

            String id = Registries.ITEM.getId(stack.getItem()).toString();
            String fullKey = registries != null ? SpotHelperConfig.protectKey(stack, registries) : "";
            if (!fullKey.isEmpty() && cfg.sellProtectItems.contains(fullKey)) {
                UiDraw.textRight(ctx, tr, "✓✓", sx + SLOT - 3, sy + 3, c(Role.OK));
            } else if (cfg.sellProtectItems.contains(id)) {
                UiDraw.textRight(ctx, tr, "✓", sx + SLOT - 3, sy + 3, c(Role.OK));
            }
            if (over) {
                UiDraw.rect(ctx, sx + 1, sy + 1, SLOT - 2, SLOT - 2,
                        UiTheme.withAlpha(UiTheme.raw(Role.HIGHLIGHT), 0x40));
                hovered = stack;
            }
            final int idx = i;
            in.addClick(sx, sy, SLOT, SLOT, () -> add(idx, false));
        }
        int rows = (total + cols - 1) / cols;
        return y + rows * step - top;
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (click.button() == 1 && click.y() >= viewT && click.y() < viewB) {
            for (int[] s : slots) {
                if (UiDraw.inside(click.x(), click.y(), s[0], s[1], SLOT, SLOT)) {
                    add(s[2], true);
                    return true;
                }
            }
        }
        return super.mouseClicked(click, doubled);
    }

    private void add(int slot, boolean idOnly) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;
        ItemStack stack = mc.player.getInventory().getStack(slot);
        if (stack.isEmpty()) return;

        if (idOnly) {
            cfg.addSellProtectedId(stack);
            toast("Добавлено по ID: " + Registries.ITEM.getId(stack.getItem()), true);
            return;
        }
        RegistryWrapper.WrapperLookup registries = mc.world != null ? mc.world.getRegistryManager() : null;
        if (registries == null) {
            toast("Мир ещё не загружен", false);
            return;
        }
        if (cfg.addSellProtected(stack, registries)) {
            toast("Добавлено (ID + NBT): " + stack.getName().getString(), true);
        } else {
            toast("Не удалось сохранить предмет", false);
        }
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        if (!hovered.isEmpty()) ctx.drawItemTooltip(textRenderer, hovered, mouseX, mouseY);
    }
}
