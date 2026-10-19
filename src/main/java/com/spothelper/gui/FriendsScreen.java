package com.spothelper.gui;

import com.spothelper.friends.FriendsManager;
import com.spothelper.gui.ui.UiCard;
import com.spothelper.gui.ui.UiChip;
import com.spothelper.gui.ui.UiDraw;
import com.spothelper.gui.ui.UiField;
import com.spothelper.gui.ui.UiScreen;
import com.spothelper.gui.ui.UiTheme.Role;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;

import java.util.List;

/** Вкладка «Друзья»: ники, которых нельзя ударить. */
public class FriendsScreen extends UiScreen {

    private final UiField nick = new UiField("ник игрока", 16);

    public FriendsScreen(Screen parent) {
        super(parent, "Друзья");
        field(nick);
        nick.onEnter = this::doAdd;
    }

    private void doAdd() {
        String text = nick.text().trim();
        if (text.isEmpty()) {
            toast("Введите ник", false);
            return;
        }
        int r = FriendsManager.add(text);
        if (r < 0) {
            toast("Неверный ник (3–16: буквы, цифры, _)", false);
        } else if (r == 0) {
            toast("Уже в списке", false);
        } else {
            nick.setText("");
            toast("Друг добавлен", true);
        }
    }

    @Override
    protected int drawBody(DrawContext ctx, int x, int top, int w) {
        TextRenderer tr = textRenderer;
        int y = top;

        y += label(ctx, x, y, "Добавить друга");
        String add = "+ Добавить";
        int aw = UiChip.width(tr, add) + 10;
        nick.draw(ctx, in, x, y, w - aw - 8, 20, true);
        UiChip.draw(ctx, tr, in, x + w - aw, y, aw, 20, add, true, !nick.text().isBlank(), this::doAdd);
        y += 26;
        UiDraw.text(ctx, tr, UiDraw.fit(tr, "Друзей нельзя ударить (ЛКМ по ним не срабатывает)", w), x, y + 2, c(Role.TEXT_DIM));
        y += 22;

        List<String> friends = FriendsManager.list();
        y += label(ctx, x, y, "Список друзей (" + friends.size() + ")");
        if (friends.isEmpty()) {
            UiDraw.text(ctx, tr, "Пока никого. Добавь первого выше.", x, y + 4, c(Role.TEXT_DIM));
            y += 20;
        }
        for (String name : friends) {
            UiCard.draw(ctx, tr, in, x, y, w, 30, name, "", 34, null);
            int dx = x + w - 24;
            boolean hov = in.mouseOver(dx, y + 6, 20, 18);
            UiDraw.text(ctx, tr, "×", dx + 7, y + 11, c(hov ? Role.ERROR : Role.TEXT_DIM));
            in.addClick(dx, y + 6, 20, 18, () -> FriendsManager.remove(name));
            y += 36;
        }
        return y - top;
    }
}
