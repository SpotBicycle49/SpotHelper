package com.spothelper.sellprotect;

import com.spothelper.config.SpotHelperConfig;
import com.spothelper.gui.ui.UiDraw;
import com.spothelper.gui.ui.UiField;
import com.spothelper.gui.ui.UiScreen;
import com.spothelper.gui.ui.UiTheme.Role;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.util.Identifier;

/** Добавление предмета в защиту по ID. Оформление — как в главном меню. */
public class SellProtectIdScreen extends UiScreen {

    private final SpotHelperConfig cfg = SpotHelperConfig.INSTANCE;
    private final UiField idField = new UiField("minecraft:diamond", 64);

    public SellProtectIdScreen(Screen parent) {
        super(parent, "Добавить по ID");
        field(idField);
        idField.onEnter = this::addCurrent;
    }

    @Override
    protected int maxWidth() {
        return 460;
    }

    @Override
    protected void afterInit() {
        focus(idField);
    }

    @Override
    protected void onBack() {
        cfg.save();
        close();
    }

    @Override
    protected int drawBody(DrawContext ctx, int x, int top, int w) {
        TextRenderer tr = textRenderer;
        int y = top;

        String[] hints = {
                "Введи ID предмета, например minecraft:diamond.",
                "Защита сработает для любого экземпляра с этим ID (без учёта NBT).",
                "Для точного совпадения (ID + NBT) используй «Из инвентаря»."
        };
        for (String h : hints) {
            UiDraw.text(ctx, tr, UiDraw.fit(tr, h, w), x, y, c(Role.TEXT_DIM));
            y += 14;
        }
        y += 6;

        String add = "+ Добавить";
        int aw = com.spothelper.gui.ui.UiChip.width(tr, add) + 10;
        idField.draw(ctx, in, x, y, Math.max(60, w - aw - 8), 22, true);
        com.spothelper.gui.ui.UiChip.draw(ctx, tr, in, x + w - aw, y + 2, aw, 18, add, true,
                !idField.text().isBlank(), this::addCurrent);
        y += 30;
        return y - top;
    }

    private void addCurrent() {
        String id = idField.text().trim().toLowerCase();
        if (id.isEmpty()) return;
        if (!id.contains(":")) id = "minecraft:" + id;
        if (Identifier.tryParse(id) == null) {
            toast("Неверный ID: " + id, false);
            return;
        }
        if (!cfg.sellProtectItems.contains(id)) {
            cfg.sellProtectItems.add(id);
            cfg.save();
            toast("Добавлено (любой NBT): " + id, true);
        } else {
            toast("Уже в списке: " + id, false);
        }
        idField.setText("");
    }
}
