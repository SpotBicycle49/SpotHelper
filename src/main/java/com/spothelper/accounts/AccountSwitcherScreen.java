package com.spothelper.accounts;

import com.spothelper.config.SpotHelperConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public class AccountSwitcherScreen extends Screen {

    private static final int ROW = 20;
    private static final int LIST_ROWS = 8;

    private final Screen parent;
    private final SpotHelperConfig cfg = SpotHelperConfig.INSTANCE;

    private TextFieldWidget nameField;
    private String statusMsg = "";
    private int statusColor = 0xFFFFFF;
    private int scrollOffset = 0;
    private int selectedIdx = -1;

    /** 0 — Основные, 1 — Твинки */
    private int mainTab = 0;

    public AccountSwitcherScreen(Screen parent) {
        super(Text.literal("Смена аккаунта"));
        this.parent = parent;
    }

    private List<String> currentList() {
        if (mainTab == 1) {
            if (cfg.savedTwinks == null) cfg.savedTwinks = new ArrayList<>();
            return cfg.savedTwinks;
        }
        if (cfg.savedNicks == null) cfg.savedNicks = new ArrayList<>();
        return cfg.savedNicks;
    }

    @Override
    protected void init() {
        clearChildren();
        int cx = width / 2;
        int y = 38;

        // ---- Табы Основные / Твинки ----
        int tabW = 100;
        int tabH = 20;
        int tabY = 4;
        int tabX0 = cx - tabW - 4;

        addDrawableChild(ButtonWidget.builder(
                Text.literal((mainTab == 0 ? "§e▶ " : "") + "Основные"),
                b -> { mainTab = 0; scrollOffset = 0; selectedIdx = -1; init(); }
        ).dimensions(tabX0, tabY, tabW, tabH).build());

        addDrawableChild(ButtonWidget.builder(
                Text.literal((mainTab == 1 ? "§e▶ " : "") + "Твинки"),
                b -> { mainTab = 1; scrollOffset = 0; selectedIdx = -1; init(); }
        ).dimensions(tabX0 + tabW + 8, tabY, tabW, tabH).build());

        // ---- Поле ввода ----
        nameField = new TextFieldWidget(textRenderer, cx - 154, y, 220, 20, Text.literal("Ник"));
        nameField.setMaxLength(16);
        nameField.setText(cfg.currentNick == null ? "" : cfg.currentNick);
        nameField.setPlaceholder(Text.literal("Введи ник..."));
        addDrawableChild(nameField);
        setInitialFocus(nameField);

        addDrawableChild(ButtonWidget.builder(Text.literal("Переключить"), b -> doSwitch())
                .dimensions(cx + 70, y, 84, 20).build());

        // ---- Нижний ряд кнопок ----
        int by = y + 24;

        if (mainTab == 0) {
            addDrawableChild(ButtonWidget.builder(Text.literal("§aСохранить"), b -> doSave())
                    .dimensions(cx - 154, by, 104, 18).build());
        } else {
            addDrawableChild(ButtonWidget.builder(Text.literal("§a+ Добавить твинков"), b ->
                    MinecraftClient.getInstance().setScreen(new TwinkAddScreen(this))
            ).dimensions(cx - 154, by, 144, 18).build());
        }

        addDrawableChild(ButtonWidget.builder(Text.literal("§cУдалить"), b -> doDelete())
                .dimensions(cx - 6, by, 60, 18).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Закрыть"), b -> closeToParent())
                .dimensions(cx + 58, by, 96, 18).build());
    }

    private void doSwitch() {
        String name = nameField.getText().trim();
        statusMsg = AccountSwitcher.switchTo(name);
        statusColor = statusMsg.startsWith("§a") ? 0x55FF55 : 0xFF5555;
    }

    private void doSave() {
        String name = nameField.getText().trim();
        if (name.isEmpty() || name.length() > 16 || !name.matches("[A-Za-z0-9_]+")) {
            statusMsg = "§cНеверный ник";
            statusColor = 0xFF5555;
            return;
        }
        if (cfg.savedNicks == null) cfg.savedNicks = new ArrayList<>();
        if (!cfg.savedNicks.contains(name)) cfg.savedNicks.add(name);

        String res = AccountSwitcher.switchTo(name);
        statusMsg = res;
        statusColor = res.startsWith("§a") ? 0x55FF55 : 0xFF5555;
        selectedIdx = cfg.savedNicks.indexOf(name);
        cfg.save();
        nameField.setText("");
        init();
    }

    private void doDelete() {
        List<String> list = currentList();
        if (selectedIdx < 0 || selectedIdx >= list.size()) return;
        list.remove(selectedIdx);
        cfg.save();
        if (selectedIdx >= list.size()) selectedIdx = list.size() - 1;
        statusMsg = "§eУдалён";
        statusColor = 0xFFFF55;
        init();
    }

    private void closeToParent() {
        MinecraftClient.getInstance().setScreen(parent);
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (input.key() == GLFW.GLFW_KEY_ENTER || input.key() == GLFW.GLFW_KEY_KP_ENTER) {
            doSwitch();
            return true;
        }
        if (input.key() == GLFW.GLFW_KEY_ESCAPE) {
            closeToParent();
            return true;
        }
        return super.keyPressed(input);
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (super.mouseClicked(click, doubled)) return true;

        double mx = click.x();
        double my = click.y();

        int listX = width / 2 - 154;
        int listY = 110;
        int listW = 308;
        List<String> nicks = currentList();
        if (mx >= listX && mx < listX + listW && my >= listY && my < listY + LIST_ROWS * ROW) {
            int row = (int) ((my - listY) / ROW) + scrollOffset;
            if (row >= 0 && row < nicks.size()) {
                selectedIdx = row;
                String nick = nicks.get(row);
                nameField.setText(nick);
                statusMsg = AccountSwitcher.switchTo(nick);
                statusColor = statusMsg.startsWith("§a") ? 0x55FF55 : 0xFF5555;
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double hAmt, double vAmt) {
        List<String> nicks = currentList();
        int max = Math.max(0, nicks.size() - LIST_ROWS);
        if (max <= 0) return super.mouseScrolled(mx, my, hAmt, vAmt);
        scrollOffset = (int) Math.max(0, Math.min(max, scrollOffset - vAmt));
        return true;
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        // blur отключён
    }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        ctx.fill(0, 0, width, height, 0xE0101020);

        int cx = width / 2;

        ctx.drawCenteredTextWithShadow(textRenderer, title, cx, 30, 0xFFFFFFFF);
        ctx.drawTextWithShadow(textRenderer,
                Text.literal("§7Текущий аккаунт: §e" + AccountSwitcher.currentName()),
                cx - 154, 26, 0xFFFFAA00);

        if (!statusMsg.isEmpty()) {
            String clean = statusMsg.replaceAll("§.", "");
            int nl = clean.indexOf('\n');
            if (nl > 0) clean = clean.substring(0, nl);
            ctx.drawTextWithShadow(textRenderer, Text.literal(clean), cx - 154, 88, statusColor);
        }

        List<String> nicks = currentList();
        int listX = cx - 154;
        int listY = 110;
        int listW = 308;

        ctx.fill(listX - 1, listY - 1, listX + listW + 1, listY + LIST_ROWS * ROW + 1, 0xFF202020);

        if (nicks.isEmpty()) {
            String emptyHint = (mainTab == 1)
                    ? "§7Список твинков пуст — нажми «+ Добавить твинков»"
                    : "§7Список пуст — нажми «Сохранить»";
            ctx.drawTextWithShadow(textRenderer, Text.literal(emptyHint),
                    listX + 4, listY + 6, 0xFFAAAAAA);
        }

        int end = Math.min(nicks.size(), scrollOffset + LIST_ROWS);
        for (int i = scrollOffset; i < end; i++) {
            int ry = listY + (i - scrollOffset) * ROW;
            boolean sel = i == selectedIdx;
            String nick = nicks.get(i);
            boolean active = nick.equals(cfg.currentNick);

            int bg;
            if (sel) bg = 0x8033AA33;
            else if ((i & 1) == 0) bg = 0x30FFFFFF;
            else bg = 0x20FFFFFF;
            ctx.fill(listX, ry, listX + listW, ry + ROW, bg);

            int textColor;
            if (active) textColor = 0xFF55FF55;
            else if (sel) textColor = 0xFFFFFF55;
            else textColor = 0xFFFFFFFF;

            ctx.drawTextWithShadow(textRenderer, Text.literal(nick),
                    listX + 6, ry + 6, textColor);

            if (active) {
                String tag = "● активный";
                int tagW = textRenderer.getWidth(tag);
                ctx.drawTextWithShadow(textRenderer, Text.literal("§a" + tag),
                        listX + listW - 12 - tagW, ry + 6, 0xFF55FF55);
            }
        }

        super.render(ctx, mx, my, delta);
    }

    @Override
    public boolean shouldPause() { return false; }
}