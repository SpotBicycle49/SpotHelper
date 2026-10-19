package com.spothelper.autosprint;

import com.spothelper.config.SpotHelperConfig;
import net.minecraft.client.MinecraftClient;

/**
 * Автоспринт: пока зажата «вперёд», программно «нажимает» клавишу спринта.
 * Остальные условия (голод, использование предмета, приседание) проверяет сама игра,
 * поэтому поведение остаётся ванильным и безопасным для сервера.
 */
public final class AutoSprintManager {
    private static boolean forced;

    private AutoSprintManager() {}

    public static void tick(MinecraftClient mc) {
        if (mc == null || mc.options == null) return;
        boolean on = SpotHelperConfig.INSTANCE.autoSprint == 1
                && mc.player != null && mc.currentScreen == null;

        if (on && mc.options.forwardKey.isPressed() && !mc.player.isSneaking()) {
            mc.options.sprintKey.setPressed(true);
            forced = true;
        } else if (forced) {
            mc.options.sprintKey.setPressed(false);
            forced = false;
        }
    }
}
