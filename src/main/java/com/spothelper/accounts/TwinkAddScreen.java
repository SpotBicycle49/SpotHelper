package com.spothelper.accounts;

import com.spothelper.config.SpotHelperConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

public class TwinkAddScreen extends Screen {

    private final Screen parent;
    private final SpotHelperConfig cfg = SpotHelperConfig.INSTANCE;

    private TextFieldWidget prefixField;
    private TextFieldWidget fromField;
    private TextFieldWidget toField;
    private String statusMsg = "";
    private int statusColor = 0xFFFFFFFF;

    public TwinkAddScreen(Screen parent) {
        super(Text.literal("Добавить твинков"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        clearChildren();
        int cx = width / 2;
        int y = 50;
        int w = 260;
        int h = 20;
        int gap = 26;

        prefixField = new TextFieldWidget(textRenderer, cx - w / 2, y, w, h, Text.literal("Префикс"));
        prefixField.setMaxLength(32);
        prefixField.setText("Spot");
        prefixField.setPlaceholder(Text.literal("например, Spot"));
        addDrawableChild(prefixField);

        fromField = new TextFieldWidget(textRenderer, cx - w / 2, y + gap, 120, h, Text.literal("От"));
        fromField.setMaxLength(6);
        fromField.setText("1");
        fromField.setPlaceholder(Text.literal("от"));
        addDrawableChild(fromField);

        toField = new TextFieldWidget(textRenderer, cx + 10, y + gap, 120, h, Text.literal("До"));
        toField.setMaxLength(6);
        toField.setText("10");
        toField.setPlaceholder(Text.literal("до"));
        addDrawableChild(toField);

        // подсказка
        addDrawableChild(ButtonWidget.builder(
                Text.literal("§7Формат: §fSpot§7 + число (например, Spot34)"),
                b -> {}
        ).dimensions(cx - w / 2, y + gap * 2, w, h).build()).active = false;

        addDrawableChild(ButtonWidget.builder(Text.literal("§a+ Сгенерировать"), b -> doGenerate())
                .dimensions(cx - 160, y + gap * 3, 150, 24).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("§cНазад"), b -> {
            cfg.save();
            MinecraftClient.getInstance().setScreen(parent);
        }).dimensions(cx + 10, y + gap * 3, 150, 24).build());
    }

    private void doGenerate() {
        String prefix = prefixField.getText().trim();
        int from, to;
        try {
            from = Integer.parseInt(fromField.getText().trim());
            to = Integer.parseInt(toField.getText().trim());
        } catch (NumberFormatException e) {
            statusMsg = "§cВведи числа в поля «От» и «До»";
            statusColor = 0xFF5555;
            return;
        }

        if (prefix.isEmpty()) {
            statusMsg = "§cВведи префикс (например, Spot)";
            statusColor = 0xFF5555;
            return;
        }
        if (from < 0 || to < 0 || from > to) {
            statusMsg = "§cНеверный диапазон: от " + from + " до " + to;
            statusColor = 0xFF5555;
            return;
        }
        if (to - from > 500) {
            statusMsg = "§cСлишком много (макс. 500). Выбери меньший диапазон";
            statusColor = 0xFF5555;
            return;
        }

        if (cfg.savedTwinks == null) cfg.savedTwinks = new java.util.ArrayList<>();

        int added = 0;
        for (int i = from; i <= to; i++) {
            String nick = prefix + i;
            if (!cfg.savedTwinks.contains(nick)) {
                cfg.savedTwinks.add(nick);
                added++;
            }
        }
        cfg.save();

        statusMsg = "§aДобавлено: §f" + added + " §aтвинков (всего: §f" + cfg.savedTwinks.size() + "§a)";
        statusColor = 0x55FF55;
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (input.key() == GLFW.GLFW_KEY_ESCAPE) {
            cfg.save();
            MinecraftClient.getInstance().setScreen(parent);
            return true;
        }
        return super.keyPressed(input);
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        // blur отключён
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        ctx.fill(0, 0, width, height, 0xE0101020);

        ctx.drawCenteredTextWithShadow(textRenderer, title, width / 2, 14, 0xFFFFFFFF);
        ctx.drawCenteredTextWithShadow(textRenderer,
                Text.literal("§7Генерирует ники по маске: §fSpot34§7, §fSpot35§7, ..., §fSpot250"),
                width / 2, 30, 0xFFAAAAAA);

        if (!statusMsg.isEmpty()) {
            ctx.drawCenteredTextWithShadow(textRenderer,
                    Text.literal(statusMsg), width / 2, height - 60, statusColor);
        }

        super.render(ctx, mouseX, mouseY, delta);
    }

    @Override
    public boolean shouldPause() { return false; }
}