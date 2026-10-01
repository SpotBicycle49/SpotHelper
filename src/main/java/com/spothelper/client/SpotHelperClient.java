package com.spothelper.client;

import com.spothelper.SpotHelperConfig;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.LiteralText;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.registry.Registry;
import org.lwjgl.glfw.GLFW;

public class SpotHelperClient implements ClientModInitializer {

    public static final HolyWorldApiClient API = new HolyWorldApiClient();
    private static long nextHolyWorldPoll = 0L;
    private static long nextHolyWorldStatusPoll = 0L;

    public static KeyBinding OPEN_MENU;
    public static KeyBinding TOGGLE_AUTO_TOOL;
    public static KeyBinding TOGGLE_ESP;
    public static KeyBinding ADD_ESP_BLOCK;

    @Override
    public void onInitializeClient() {
        // прошлый запуск умер при открытии GUI? - отключаем виновника автоматически
        boolean trippedFont = GuiGuard.takeTripped("font");
        boolean trippedCursor = GuiGuard.takeTripped("cursor");
        if (trippedFont || trippedCursor) {
            if (trippedFont) SpotHelperConfig.INSTANCE.fancyText = 0;
            if (trippedCursor) SpotHelperConfig.INSTANCE.customCursors = 0;
            SpotHelperConfig.INSTANCE.save();
            System.out.println("[SpotHelper] Прошлый запуск оборвался при открытии GUI (этап: "
                    + (trippedFont ? "шрифт " : "") + (trippedCursor ? "курсор" : "")
                    + "). Включён безопасный режим: fancyText=" + SpotHelperConfig.INSTANCE.fancyText
                    + ", customCursors=" + SpotHelperConfig.INSTANCE.customCursors
                    + ". Вернуть можно в config/spothelper.json.");
        }

        OPEN_MENU = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.spothelper.menu",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_RIGHT_SHIFT,
                "category.spothelper"
        ));

        TOGGLE_AUTO_TOOL = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.spothelper.auto_tool",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_G,
                "category.spothelper"
        ));

        TOGGLE_ESP = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.spothelper.esp",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_H,
                "category.spothelper"
        ));

        ADD_ESP_BLOCK = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.spothelper.esp_add",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_J,
                "category.spothelper"
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (OPEN_MENU.wasPressed()) {
                if (client.currentScreen == null) {
                    client.openScreen(new SpotHelperScreen(null));
                }
            }

            while (TOGGLE_AUTO_TOOL.wasPressed()) {
                SpotHelperConfig.INSTANCE.autoTool =
                        SpotHelperConfig.INSTANCE.autoTool == 1 ? 0 : 1;
                SpotHelperConfig.INSTANCE.save();
                if (client.player != null) {
                    client.player.sendMessage(new LiteralText(
                            "§aSpotHelper: автоинструмент " +
                                    (SpotHelperConfig.INSTANCE.isAutoToolEnabled() ? "§aВКЛ" : "§cВЫКЛ")
                    ), true);
                }
            }

            while (TOGGLE_ESP.wasPressed()) {
                SpotHelperConfig.INSTANCE.espEnabled =
                        SpotHelperConfig.INSTANCE.espEnabled == 1 ? 0 : 1;
                SpotHelperConfig.INSTANCE.save();
                if (client.player != null) {
                    client.player.sendMessage(new LiteralText(
                            "§aSpotHelper: ESP " +
                                    (SpotHelperConfig.INSTANCE.isEspEnabled() ? "§aВКЛ" : "§cВЫКЛ")
                    ), true);
                }
            }

            while (ADD_ESP_BLOCK.wasPressed()) {
                if (client.player == null || client.world == null) continue;
                HitResult hit = client.crosshairTarget;
                if (hit == null || hit.getType() != HitResult.Type.BLOCK) {
                    client.player.sendMessage(new LiteralText("§cСмотри на блок"), true);
                    continue;
                }
                BlockState state = client.world.getBlockState(((BlockHitResult) hit).getBlockPos());
                Block block = state.getBlock();
                String id = Registry.BLOCK.getId(block).toString();

                boolean had = SpotHelperConfig.INSTANCE.espBlocks.contains(id);
                SpotHelperConfig.INSTANCE.toggleEspBlock(block);

                client.player.sendMessage(new LiteralText(
                        had
                                ? "§cESP: убран §f" + id
                                : "§aESP: добавлен §f" + id
                ), true);
            }

            AutoToolHandler.tick(client);
            EventFlow.tick(client);

            long now = System.currentTimeMillis();
            if (SpotHelperConfig.INSTANCE.isHolyWorldEventsAutoRefresh() && now >= nextHolyWorldPoll) {
                nextHolyWorldPoll = now + Math.max(5, SpotHelperConfig.INSTANCE.holyWorldEventsInterval) * 1000L;
                API.refreshEvents(new HolyWorldApiClient.Callback() {
                    @Override public void onSuccess(HolyWorldData.Snapshot snapshot) { }
                    @Override public void onError(String message) { }
                });
            }

            if (now >= nextHolyWorldStatusPoll) {
                nextHolyWorldStatusPoll = now + 8000L;
                API.refreshStatus(new HolyWorldApiClient.Callback() {
                    @Override public void onSuccess(HolyWorldData.Snapshot snapshot) { }
                    @Override public void onError(String message) { }
                });
            }
        });

        WorldRenderEvents.AFTER_TRANSLUCENT.register(context -> {
            BlockEspRenderer.render(context.matrixStack(), context.tickDelta());
        });
    }
}