package com.spothelper.gui;

import com.spothelper.accounts.AccountSwitcherScreen;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableTextContent;

/**
 * Добавляет кнопку «Сменить аккаунт» в главное меню Minecraft.
 * Позиция: ВСЕГДА справа от кнопки «Сетевая игра» (menu.multiplayer).
 * Если места мало — кнопка сжимается по ширине (минимум 60 px), но остаётся справа.
 */
public final class TitleScreenButton {

    private static final int BTN_H = 20;
    private static final int GAP = 8;
    private static final int MIN_W = 60;
    private static final int MAX_W = 140;

    private TitleScreenButton() {}

    public static void init() {
        ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
            if (!(screen instanceof TitleScreen)) return;

            ClickableWidget multiplayer = findButton(screen, "menu.multiplayer");

            int x, y, btnW;
            if (multiplayer != null) {
                int rightX = multiplayer.getX() + multiplayer.getWidth() + GAP;
                y = multiplayer.getY();

                // Сколько места доступно справа до края экрана
                int available = (w - 4) - rightX;
                if (available >= MIN_W) {
                    // Влезает — берём ширину по доступному, ограничивая сверху MAX_W
                    btnW = Math.min(MAX_W, available);
                    x = rightX;
                } else {
                    // Места мало — всё равно ставим справа, но сжимаем до MIN_W
                    // и прижимаем к правому краю
                    btnW = MIN_W;
                    x = (w - 4) - btnW;
                }
            } else {
                // Фолбэк: под колонкой по центру
                btnW = MAX_W;
                x = w / 2 - btnW / 2;
                y = h / 4 + 120;
            }

            ButtonWidget btn = ButtonWidget.builder(
                    Text.literal("Сменить аккаунт"),
                    b -> client.setScreen(new AccountSwitcherScreen(screen))
            ).dimensions(x, y, btnW, BTN_H).build();

            Screens.getButtons(screen).add(btn);
        });
    }

    /** Ищет кнопку по ключу TranslatableTextContent. */
    private static ClickableWidget findButton(Screen screen, String translationKey) {
        for (ClickableWidget w : Screens.getButtons(screen)) {
            Text msg = w.getMessage();
            if (msg == null) continue;
            if (msg.getContent() instanceof TranslatableTextContent ttc
                    && translationKey.equals(ttc.getKey())) {
                return w;
            }
        }
        return null;
    }
}