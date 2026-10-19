package com.spothelper.sellprotect;

import com.spothelper.config.SpotHelperConfig;
import com.spothelper.gui.ui.UiDraw;
import com.spothelper.gui.ui.UiScreen;
import com.spothelper.gui.ui.UiTheme;
import com.spothelper.gui.ui.UiTheme.Role;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

/** Защита продажи: список защищённых предметов. Оформление — как в главном меню. */
public class SellProtectScreen extends UiScreen {

    private static final int SLOT = 32;
    private static final int GAP = 4;

    private final SpotHelperConfig cfg = SpotHelperConfig.INSTANCE;

    private ItemStack hovered = ItemStack.EMPTY;
    private String hoveredEntry;

    public SellProtectScreen(Screen parent) {
        super(parent, "Защита продажи");
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
        hovered = ItemStack.EMPTY;
        hoveredEntry = null;

        int y = top;
        UiDraw.text(ctx, tr, UiDraw.fit(tr, "Клик по слоту — убрать из защиты. Наведи — увидишь предмет.", w),
                x, y, c(Role.TEXT_DIM));
        y += 16;

        y = flowChips(ctx, x, y, w,
                new String[]{"+ Из инвентаря", "+ По ID"},
                new Runnable[]{
                        () -> mc.setScreen(new SellProtectInvScreen(self())),
                        () -> mc.setScreen(new SellProtectIdScreen(self()))},
                new boolean[]{true, false}, null);

        List<String> items = cfg.sellProtectItems;
        y += label(ctx, x, y, "Защищённые (" + items.size() + ")");
        if (items.isEmpty()) {
            UiDraw.text(ctx, tr, "Список пуст.", x, y + 4, c(Role.TEXT_DIM));
            return y + 20 - top;
        }

        int cols = Math.max(1, (w + GAP) / (SLOT + GAP));
        int step = SLOT + GAP;
        List<String> snapshot = new ArrayList<>(items);
        for (int i = 0; i < snapshot.size(); i++) {
            int sx = x + (i % cols) * step;
            int sy = y + (i / cols) * step;
            String entry = snapshot.get(i);
            ItemStack stack = SpotHelperConfig.stackFromEntry(entry, registries);

            boolean over = in.mouseOver(sx, sy, SLOT, SLOT);
            int fill = c(Role.CARD_BG);
            if (over) fill = UiTheme.mix(fill, UiTheme.raw(Role.ERROR), 0.25);
            UiDraw.box(ctx, sx, sy, SLOT, SLOT, c(over ? Role.ERROR : Role.CARD_BORDER), fill);

            if (!stack.isEmpty()) {
                ctx.drawItem(stack, sx + (SLOT - 16) / 2, sy + (SLOT - 16) / 2);
                String mark = entry.startsWith("{") ? "✓✓" : "✓";
                UiDraw.textRight(ctx, tr, mark, sx + SLOT - 3, sy + 3, c(Role.OK));
            }
            if (over) {
                UiDraw.text(ctx, tr, "×", sx + SLOT - 9, sy + SLOT - 11, c(Role.ERROR));
                if (!stack.isEmpty()) {
                    hovered = stack;
                    hoveredEntry = entry;
                }
            }
            final int idx = i;
            in.addClick(sx, sy, SLOT, SLOT, () -> {
                if (idx < cfg.sellProtectItems.size()) {
                    cfg.sellProtectItems.remove(idx);
                    cfg.save();
                }
            });
        }
        int rows = (snapshot.size() + cols - 1) / cols;
        return y + rows * step - top;
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        if (hovered.isEmpty()) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        Item.TooltipContext tooltipCtx = mc.world != null
                ? Item.TooltipContext.create(mc.world)
                : Item.TooltipContext.DEFAULT;
        List<Text> lines = new ArrayList<>(hovered.getTooltip(
                tooltipCtx, mc.player,
                mc.options.advancedItemTooltips ? TooltipType.ADVANCED : TooltipType.BASIC));
        if (hoveredEntry != null && !hoveredEntry.startsWith("{")) {
            lines.add(Text.literal("Запись без NBT: любой экземпляр"));
        }
        ctx.drawTooltip(textRenderer, lines, mouseX, mouseY);
    }
}