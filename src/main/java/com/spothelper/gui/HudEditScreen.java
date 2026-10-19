package com.spothelper.gui;

import com.spothelper.config.SpotHelperConfig;
import com.spothelper.gui.ui.UiDraw;
import com.spothelper.gui.ui.UiScreen;
import com.spothelper.gui.ui.UiTheme.Role;
import com.spothelper.hud.StatusHud;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;

/** Настройки HUD: что показывать и где. Оформление — как в главном меню. */
public class HudEditScreen extends UiScreen {

    public HudEditScreen(Screen parent) {
        super(parent, "HUD");
    }

    @Override
    protected int drawBody(DrawContext ctx, int x, int top, int w) {
        SpotHelperConfig cfg = SpotHelperConfig.INSTANCE;
        int y = top;

        toggleCard(ctx, x, y, w, 40, "Статус HUD", "Общий переключатель элементов ниже",
                cfg.statusHud == 1, () -> flip(() -> cfg.statusHud ^= 1));
        y += 48;
        toggleCard(ctx, x, y, w, 40, "Броня", "Прочность надетой брони",
                cfg.statusHudArmor == 1, () -> flip(() -> cfg.statusHudArmor ^= 1));
        y += 48;
        toggleCard(ctx, x, y, w, 40, "Эффекты", "Активные эффекты и время",
                cfg.statusHudEffects == 1, () -> flip(() -> cfg.statusHudEffects ^= 1));
        y += 48;
        toggleCard(ctx, x, y, w, 40, "Курс коина", "Курс по последней сделке обмена монет",
                cfg.statusHudCoin == 1, () -> flip(() -> cfg.statusHudCoin ^= 1));
        y += 48;
        toggleCard(ctx, x, y, w, 40, "Таймеры ТНТ", "Список всех активных ТНТ до взрыва (нужен «Таймер ТНТ» в Функциях)",
                cfg.tntTimerInHud == 1, () -> flip(() -> cfg.tntTimerInHud ^= 1));
        y += 52;

        UiDraw.text(ctx, textRenderer, "Вид эффектов", x, y + 2, c(Role.TEXT_DIM));
        y += 14;
        final int mode = cfg.statusHudEffectsMode;
        y = flowChips(ctx, x, y, w,
                new String[]{"Названия", "Иконки", "Иконки + названия"},
                new Runnable[]{() -> flip(() -> cfg.statusHudEffectsMode = 0),
                        () -> flip(() -> cfg.statusHudEffectsMode = 1),
                        () -> flip(() -> cfg.statusHudEffectsMode = 2)},
                new boolean[]{mode == 0, mode == 1, mode == 2}, null);
        y += 4;

        y = flowChips(ctx, x, y, w,
                new String[]{"Расположение: перетащить мышью", "Сбросить положение"},
                new Runnable[]{
                        () -> MinecraftClient.getInstance().setScreen(new HudPositionScreen(self())),
                        () -> {
                            StatusHud.resetPositions();
                            toast("Положение сброшено", true);
                        }},
                new boolean[]{true, false}, null);

        UiDraw.text(ctx, textRenderer, "Каждый элемент HUD двигается мышью независимо от остальных.",
                x, y + 2, c(Role.TEXT_DIM));
        y += 16;
        return y - top;
    }

    private static void flip(Runnable r) {
        r.run();
        SpotHelperConfig.INSTANCE.save();
    }
}
