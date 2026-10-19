package com.spothelper.gui;

import com.spothelper.commandbinds.CommandBind;
import com.spothelper.commandbinds.CommandBindManager;
import com.spothelper.gui.ui.UiCard;
import com.spothelper.gui.ui.UiChip;
import com.spothelper.gui.ui.UiDraw;
import com.spothelper.gui.ui.UiField;
import com.spothelper.gui.ui.UiScreen;
import com.spothelper.gui.ui.UiTheme.Role;
import com.spothelper.gui.ui.UiToggle;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/** Бинды команд. Оформление — как в главном меню. */
public class CommandBindScreen extends UiScreen {

    private final UiField cmd = new UiField("например: /spawn", 128);
    private final UiField interval = new UiField("сек", 5).numeric();

    private int newKeyCode = 0;
    private boolean newCyclic = false;
    private boolean waitingKey = false;

    public CommandBindScreen(Screen parent) {
        super(parent, "Бинды команд");
        field(cmd);
        field(interval);
        interval.setText("5");
        cmd.onEnter = this::doAdd;
    }

    private int parseInterval() {
        try {
            int v = Integer.parseInt(interval.text().trim());
            return Math.max(1, Math.min(99999, v));
        } catch (Exception e) {
            return 5;
        }
    }

    private void doAdd() {
        String text = cmd.text().trim();
        if (text.isEmpty()) {
            toast("Введите команду", false);
            return;
        }
        if (!text.startsWith("/")) text = "/" + text;

        CommandBindManager.add(new CommandBind(text, newKeyCode, newCyclic, parseInterval()));

        cmd.setText("");
        interval.setText("5");
        newKeyCode = 0;
        newCyclic = false;
        waitingKey = false;
        toast("Бинд добавлен", true);
    }

    @Override
    protected boolean onKey(int key) {
        if (!waitingKey) return false;
        if (key != ESC && key != GLFW.GLFW_KEY_UNKNOWN) newKeyCode = key;
        waitingKey = false;
        return true;
    }

    @Override
    protected int drawBody(DrawContext ctx, int x, int top, int w) {
        TextRenderer tr = textRenderer;
        int y = top;

        y += label(ctx, x, y, "Новый бинд");
        cmd.draw(ctx, in, x, y, w, 20, true);
        y += 26;

        // клавиша + тумблер «Цикл»
        String keyLabel = waitingKey ? "Нажмите клавишу…" : "Клавиша: " + keyName(newKeyCode);
        int kw = Math.min(Math.max(150, UiChip.width(tr, keyLabel)), w);
        UiChip.draw(ctx, tr, in, x, y, kw, 20, keyLabel, waitingKey, true, () -> waitingKey = !waitingKey);
        int tx = x + kw + 12;
        if (tx + UiToggle.W + 40 <= x + w) {
            UiToggle.draw(ctx, in, tx, y + 3, newCyclic, true, () -> newCyclic = !newCyclic);
            UiDraw.text(ctx, tr, "Цикл", tx + UiToggle.W + 6, y + 6, c(Role.TEXT_MAIN));
        }
        y += 26;

        // интервал + добавить
        String il = "Интервал, с";
        UiDraw.text(ctx, tr, il, x, y + 6, c(newCyclic ? Role.TEXT_MAIN : Role.TEXT_DIM));
        interval.draw(ctx, in, x + tr.getWidth(il) + 8, y, 56, 20, newCyclic);
        String add = "+ Добавить";
        int aw = UiChip.width(tr, add) + 10;
        UiChip.draw(ctx, tr, in, x + w - aw, y, aw, 20, add, true, !cmd.text().isBlank(), this::doAdd);
        y += 32;

        // список
        List<CommandBind> binds = new ArrayList<>(CommandBindManager.getBinds());
        y += label(ctx, x, y, "Список биндов (" + binds.size() + ")");
        if (binds.isEmpty()) {
            UiDraw.text(ctx, tr, "Пока нет биндов. Добавь первый выше.", x, y + 4, c(Role.TEXT_DIM));
            y += 20;
        }
        for (CommandBind bind : binds) {
            UiCard.draw(ctx, tr, in, x, y, w, 38, bind.command,
                    keyName(bind.keyCode) + " · " + (bind.cyclic ? "цикл" : "однократно"), 110, null);

            String st = bind.cyclic ? (bind.active ? "RUN " : "STOP ") + bind.intervalSeconds + " с" : "1×";
            int col = c(bind.cyclic && bind.active ? Role.OK : Role.TEXT_DIM);
            UiDraw.textRight(ctx, tr, st, x + w - 30, y + 8, col);

            int dx = x + w - 24;
            boolean hov = in.mouseOver(dx, y + 4, 20, 18);
            UiDraw.text(ctx, tr, "×", dx + 7, y + 8, c(hov ? Role.ERROR : Role.TEXT_DIM));
            in.addClick(dx, y + 4, 20, 18, () -> CommandBindManager.remove(bind));
            y += 44;
        }
        return y - top;
    }

    private static String keyName(int code) {
        if (code <= 0) return "—";
        String name = GLFW.glfwGetKeyName(code, 0);
        if (name != null && !name.isEmpty()) return name.toUpperCase();
        switch (code) {
            case GLFW.GLFW_KEY_LEFT_SHIFT: return "LSHIFT";
            case GLFW.GLFW_KEY_RIGHT_SHIFT: return "RSHIFT";
            case GLFW.GLFW_KEY_LEFT_CONTROL: return "LCTRL";
            case GLFW.GLFW_KEY_RIGHT_CONTROL: return "RCTRL";
            case GLFW.GLFW_KEY_LEFT_ALT: return "LALT";
            case GLFW.GLFW_KEY_RIGHT_ALT: return "RALT";
            case GLFW.GLFW_KEY_SPACE: return "SPACE";
            case GLFW.GLFW_KEY_ENTER: return "ENTER";
            case GLFW.GLFW_KEY_TAB: return "TAB";
            default: return "K" + code;
        }
    }
}
