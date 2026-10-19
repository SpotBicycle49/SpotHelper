package com.spothelper.filter;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

public class FilterPlaceholderScreen extends Screen {

    private final Screen parent;
    private final String category;

    public FilterPlaceholderScreen(Screen parent, String category) {
        super(Text.literal("Фильтр: " + category));
        this.parent = parent;
        this.category = category;
    }

    @Override
    protected void init() {
        int cx = width / 2;
        int y = height / 2;

        addDrawableChild(ButtonWidget.builder(Text.literal("Закрыть"), b -> {
            if (client != null) client.setScreen(parent);
        }).dimensions(cx - 100, y, 200, 20).build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, height / 2 - 40, 0xFFFFFFFF);
        context.drawCenteredTextWithShadow(textRenderer, Text.literal("Категория: " + category),
                width / 2, height / 2 - 20, 0xFFAAAAAA);
    }

}