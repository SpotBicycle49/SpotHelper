package com.spothelper.config;

import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

public final class SpotHelperConfigToggle {
    private SpotHelperConfigToggle() {}


    public static void toggleMinimap() {
        SpotHelperConfig cfg = SpotHelperConfig.INSTANCE;
        cfg.minimapEnabled = cfg.minimapEnabled == 1 ? 0 : 1;
        cfg.save();
        if (MinecraftClient.getInstance().player != null) {
            MinecraftClient.getInstance().player.sendMessage(
                    Text.literal("§aSpotHelper: Миникарта " + (cfg.isMinimapEnabled() ? "§aВКЛ" : "§cВЫКЛ")),
                    true);
        }
    }
}