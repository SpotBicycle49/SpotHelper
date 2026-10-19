package com.spothelper.crosshair;

import com.spothelper.config.SpotHelperConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.AttackIndicator;
import net.minecraft.client.option.Perspective;
import net.minecraft.util.hit.HitResult;

/** Свой прицел: цвет зависит от того, на что наведён взгляд. */
public final class CrosshairColorManager {
    private static final int ARM = 7;

    private CrosshairColorManager() {}

    public static boolean enabled() {
        return SpotHelperConfig.INSTANCE.crosshairCustomColor == 1;
    }

    /** Цвет под текущую цель. */
    public static int currentColor(MinecraftClient mc) {
        SpotHelperConfig cfg = SpotHelperConfig.INSTANCE;
        HitResult hit = mc.crosshairTarget;
        if (hit != null) {
            if (hit.getType() == HitResult.Type.ENTITY) return cfg.crosshairTargetColor;
            if (hit.getType() == HitResult.Type.BLOCK) return cfg.crosshairBlockColor;
        }
        return cfg.crosshairDefaultColor;
    }

    /**
     * Рисует прицел вместо ванильного.
     * @return true, если ванильный прицел нужно отменить
     */
    public static boolean render(MinecraftClient mc, DrawContext ctx) {
        if (!enabled() || mc.player == null || mc.options == null) return false;
        if (mc.options.getPerspective() != Perspective.FIRST_PERSON) return false;

        int color = currentColor(mc);
        int cx = (ctx.getScaledWindowWidth() - 15) / 2 + 7;
        int cy = (ctx.getScaledWindowHeight() - 15) / 2 + 7;

        // тёмная обводка для читаемости на светлом фоне
        int shadow = 0x90000000;
        ctx.fill(cx - ARM - 1, cy - 1, cx + ARM + 2, cy + 2, shadow);
        ctx.fill(cx - 1, cy - ARM - 1, cx + 2, cy + ARM + 2, shadow);
        // сам прицел
        ctx.fill(cx - ARM, cy, cx + ARM + 1, cy + 1, color);
        ctx.fill(cx, cy - ARM, cx + 1, cy + ARM + 1, color);

        // индикатор перезарядки атаки (если выбран режим «Прицел»)
        if (mc.options.getAttackIndicator().getValue() == AttackIndicator.CROSSHAIR) {
            float progress = mc.player.getAttackCooldownProgress(0.0f);
            if (progress < 1.0f) {
                int bx = cx - 8;
                int by = cy + 10;
                int filled = (int) (progress * 17.0f);
                ctx.fill(bx, by, bx + 17, by + 3, 0xAA000000);
                ctx.fill(bx + 1, by + 1, bx + 1 + Math.max(0, filled - 1), by + 2, 0xFFFFFFFF);
            }
        }
        return true;
    }
}
