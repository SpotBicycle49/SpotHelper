package com.spothelper.client;

import com.spothelper.accounts.AccountSwitcherScreen;
import com.spothelper.camera.F5FreeLookManager;
import com.spothelper.zoom.ZoomManager;
import com.spothelper.auth.AuthManager;
import com.spothelper.auth.AuthScreen;
import com.spothelper.chatreply.ChatAutoReply;
import com.spothelper.calc.Calculator;
import com.spothelper.commandbinds.CommandBindConfig;
import com.spothelper.commandbinds.CommandBindManager;
import com.spothelper.config.SpotHelperConfig;
import com.spothelper.config.SpotHelperConfigToggle;
import com.spothelper.gui.SpotHelperScreen;
import com.spothelper.gui.TitleScreenButton;
import com.spothelper.holyworld.EventFlow;
import com.spothelper.holyworld.HolyWorldApiClient;
import com.spothelper.hud.StatusHud;
import com.spothelper.minimap.Minimap;
import com.spothelper.minimap.MinimapSettingsScreen;
import com.spothelper.sellprotect.SellConfirmScreen;
import com.spothelper.sellprotect.SellProtectScreen;
import com.spothelper.shulkerlock.ShulkerLock;
import com.spothelper.util.NumberFormat;
import com.spothelper.waypoints.Waypoint;
import com.spothelper.waypoints.WaypointManager;
import com.spothelper.waypoints.WaypointScreen;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import com.spothelper.hud.HealthOverlay;
import com.spothelper.waypoints.WaypointLabels;
import com.spothelper.holyworld.AnarchyNavigator;
import com.spothelper.waypoints.WaypointWorldRenderer;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

public class SpotHelperClient implements ClientModInitializer {

    public static HolyWorldApiClient API = new HolyWorldApiClient();

    public static KeyBinding OPEN_MENU;
    public static KeyBinding ADD_WAYPOINT;
    public static KeyBinding OPEN_WAYPOINTS;
    public static KeyBinding TOGGLE_MINIMAP;
    public static KeyBinding MINIMAP_ZOOM_IN;
    public static KeyBinding MINIMAP_ZOOM_OUT;
    public static KeyBinding OPEN_MINIMAP_SETTINGS;
    public static KeyBinding OPEN_ACCOUNT_SWITCHER;
    public static KeyBinding FREE_LOOK_KEY;
    public static KeyBinding TOGGLE_INV_HUD;

    public static Screen pendingScreen = null;

    public static KeyBinding.Category CATEGORY;

    @Override
    public void onInitializeClient() {

        CATEGORY = KeyBinding.Category.create(Identifier.of("spothelper", "general"));

        // ---- Загрузка биндов команд ----
        CommandBindConfig.load();

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {

            // ========== /lt <номер> — переход на Лайт-анархию ==========
            dispatcher.register(
                    ClientCommandManager.literal("lt")
                            .executes(ctx -> {
                                ctx.getSource().sendFeedback(Text.literal("§eSpotHelper: §f/lt <1-36>"));
                                return 1;
                            })
                            .then(ClientCommandManager.argument("n", IntegerArgumentType.integer(1, 36))
                                    .executes(ctx -> {
                                        AnarchyNavigator.start(IntegerArgumentType.getInteger(ctx, "n"));
                                        return 1;
                                    }))
            );

            // ========== /calc ==========
            dispatcher.register(
                    ClientCommandManager.literal("calc")
                            .then(ClientCommandManager.argument("expr", StringArgumentType.greedyString())
                                    .executes(ctx -> {
                                        String expr = StringArgumentType.getString(ctx, "expr");
                                        try {
                                            double value = Calculator.evaluate(expr);
                                            ctx.getSource().sendFeedback(
                                                    Text.literal("§aSpotHelper: §f" + expr + " §7= §e" + Calculator.format(value)));
                                        } catch (Exception e) {
                                            ctx.getSource().sendError(
                                                    Text.literal("§cSpotHelper: " + e.getMessage()));
                                        }
                                        return 1;
                                    }))
            );

            // ========== /sah sell <price> ==========
            dispatcher.register(
                    ClientCommandManager.literal("sah")
                            .then(ClientCommandManager.literal("sell")
                                    .then(ClientCommandManager.argument("price", IntegerArgumentType.integer(1))
                                            .executes(ctx -> {
                                                int unitPrice = IntegerArgumentType.getInteger(ctx, "price");
                                                MinecraftClient mc = MinecraftClient.getInstance();
                                                if (mc.player == null) {
                                                    ctx.getSource().sendError(Text.literal("§cSpotHelper: не в игре"));
                                                    return 0;
                                                }
                                                ItemStack held = mc.player.getMainHandStack();
                                                if (held.isEmpty()) {
                                                    ctx.getSource().sendError(Text.literal("§cSpotHelper: в руке ничего нет"));
                                                    return 0;
                                                }

                                                if (!com.spothelper.slotlock.SlotLockManager.canSell(mc.player.getInventory().getSelectedSlot())) {
                                                    ctx.getSource().sendError(Text.literal("§cSpotHelper: слот заблокирован от продажи"));
                                                    return 0;
                                                }

                                                int count = held.getCount();
                                                int total = unitPrice * count;
                                                String itemName = held.getName().getString();

                                                SpotHelperConfig cfg = SpotHelperConfig.INSTANCE;

                                                if (cfg.isSellProtected(held)
                                                        && !cfg.matchesLastConfirmed(held, unitPrice)) {
                                                    final int fTotal = total;
                                                    final int fUnit = unitPrice;
                                                    final int fCount = count;
                                                    final String fItemName = itemName;

                                                    try {
                                                        pendingScreen = new SellConfirmScreen(
                                                                mc.currentScreen,
                                                                held.copy(),
                                                                fUnit,
                                                                fCount,
                                                                fTotal,
                                                                () -> {
                                                                    cfg.rememberConfirmed(held, fUnit);
                                                                    cfg.save();
                                                                    com.spothelper.sellprotect.SellGuard.sendRaw(mc, "ah sell " + fTotal);
                                                                    mc.player.sendMessage(Text.literal(
                                                                            "§aSpotHelper: §f" + fItemName + " §7× §f" + NumberFormat.format(fCount)
                                                                                    + " шт. × §e" + NumberFormat.format(fUnit) + " §7= §e"
                                                                                    + NumberFormat.format(fTotal)
                                                                                    + " §7(отправлено /ah sell " + fTotal + ")"), false);
                                                                }
                                                        );
                                                    } catch (Throwable t) {
                                                        com.spothelper.util.Log.error("sell command", t);
                                                    }
                                                    return 1;
                                                }

                                                if (!cfg.matchesLastConfirmed(held, unitPrice)) {
                                                    cfg.clearConfirmed();
                                                    cfg.save();
                                                }

                                                com.spothelper.sellprotect.SellGuard.sendRaw(mc, "ah sell " + total);
                                                mc.player.sendMessage(Text.literal(
                                                        "§aSpotHelper: §f" + itemName + " §7× §f" + NumberFormat.format(count)
                                                                + " шт. × §e" + NumberFormat.format(unitPrice) + " §7= §e"
                                                                + NumberFormat.format(total)
                                                                + " §7(отправлено /ah sell " + total + ")"), false);
                                                return 1;
                                            }))
                            )
            );

            // ========== /wp — метки ==========
            dispatcher.register(
                    ClientCommandManager.literal("wp")
                            .executes(ctx -> {
                                MinecraftClient mc = MinecraftClient.getInstance();
                                mc.send(() -> mc.setScreen(new WaypointScreen(null)));
                                return 1;
                            })
                            .then(ClientCommandManager.literal("add")
                                    .then(ClientCommandManager.argument("name", StringArgumentType.string())
                                            .executes(ctx -> {
                                                Waypoint w = WaypointManager.addHere(StringArgumentType.getString(ctx, "name"));
                                                if (w == null) {
                                                    ctx.getSource().sendError(Text.literal("§cSpotHelper: не в игре"));
                                                    return 0;
                                                }
                                                ctx.getSource().sendFeedback(Text.literal("§aSpotHelper: метка §f" + w.name
                                                        + " §7(" + w.x + " " + w.y + " " + w.z + ")"));
                                                return 1;
                                            })
                                            .then(ClientCommandManager.argument("x", IntegerArgumentType.integer())
                                                    .then(ClientCommandManager.argument("y", IntegerArgumentType.integer())
                                                            .then(ClientCommandManager.argument("z", IntegerArgumentType.integer())
                                                                    .executes(ctx -> {
                                                                        Waypoint w = WaypointManager.add(
                                                                                StringArgumentType.getString(ctx, "name"),
                                                                                IntegerArgumentType.getInteger(ctx, "x"),
                                                                                IntegerArgumentType.getInteger(ctx, "y"),
                                                                                IntegerArgumentType.getInteger(ctx, "z"));
                                                                        ctx.getSource().sendFeedback(Text.literal("§aSpotHelper: метка §f" + w.name
                                                                                + " §7(" + w.x + " " + w.y + " " + w.z + ")"));
                                                                        return 1;
                                                                    })))))
                            )
                            .then(ClientCommandManager.literal("del")
                                    .then(ClientCommandManager.argument("name", StringArgumentType.string())
                                            .executes(ctx -> {
                                                String n = StringArgumentType.getString(ctx, "name");
                                                if (WaypointManager.removeByName(n)) {
                                                    ctx.getSource().sendFeedback(Text.literal("§aSpotHelper: метка §f" + n + " §aудалена"));
                                                    return 1;
                                                }
                                                ctx.getSource().sendError(Text.literal("§cSpotHelper: нет метки «" + n + "»"));
                                                return 0;
                                            })))
                            .then(ClientCommandManager.literal("list")
                                    .executes(ctx -> {
                                        var list = WaypointManager.current();
                                        if (list.isEmpty()) {
                                            ctx.getSource().sendFeedback(Text.literal("§7SpotHelper: меток нет"));
                                            return 1;
                                        }
                                        for (Waypoint w : list) {
                                            ctx.getSource().sendFeedback(Text.literal("§e" + w.name
                                                    + " §7— §f" + w.x + " " + w.y + " " + w.z));
                                        }
                                        return 1;
                                    }))
            );

            // ========== ВРЕМЕННО: /spot protect ==========
            dispatcher.register(
                    ClientCommandManager.literal("spot")
                            .then(ClientCommandManager.literal("protect")
                                    .executes(ctx -> {
                                        MinecraftClient mc = MinecraftClient.getInstance();
                                        mc.send(() -> mc.setScreen(new SellProtectScreen(null)));
                                        return 1;
                                    }))
            );
        });

        // ---- Клавиши ----
        OPEN_MENU = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.spothelper.menu",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_RIGHT_SHIFT,
                CATEGORY));



        ADD_WAYPOINT = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.spothelper.add_waypoint",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_B,
                CATEGORY));

        OPEN_WAYPOINTS = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.spothelper.open_waypoints",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_UNKNOWN,
                CATEGORY));

        TOGGLE_MINIMAP = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.spothelper.toggle_minimap",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_M,
                CATEGORY));

        MINIMAP_ZOOM_IN = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.spothelper.minimap_zoom_in",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_EQUAL,
                CATEGORY));

        MINIMAP_ZOOM_OUT = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.spothelper.minimap_zoom_out",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_MINUS,
                CATEGORY));

        OPEN_MINIMAP_SETTINGS = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.spothelper.minimap_settings",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_Y,
                CATEGORY));


        // Удерживать для свободного вращения камеры в F5. Можно переназначить в Настройки → Управление.
        FREE_LOOK_KEY = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.spothelper.free_look",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_LEFT_ALT,
                CATEGORY));

        OPEN_ACCOUNT_SWITCHER = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.spothelper.open_account_switcher",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_UNKNOWN,
                CATEGORY));

        // Inventory HUD: вкл/выкл. По умолчанию не назначена — назначь в Настройки → Управление.
        TOGGLE_INV_HUD = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.spothelper.toggle_inv_hud",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_UNKNOWN,
                CATEGORY));

        // ---- Инициализация модулей ----
        AuthManager.init();
        ShulkerLock.init();
        TitleScreenButton.init();
        com.spothelper.slotlock.SlotLockOverlay.init();
        ClientTickEvents.START_CLIENT_TICK.register(com.spothelper.slotlock.SlotLockManager::tickStart);
        ClientTickEvents.START_CLIENT_TICK.register(com.spothelper.damagetint.DamageTintManager::tick);
        ChatAutoReply.init();
        try {
            com.spothelper.critsounds.AttackSounds.init();
        } catch (Throwable t) {
            com.spothelper.util.Log.error("attack sounds init", t);
        }
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents.CLIENT_STOPPING
                .register(c -> com.spothelper.critsounds.AttackSounds.shutdown());

        // ---- Метки в мире (3D, сквозь стены) ----
        // ---- Метки в мире (3D, сквозь стены) ----
        WaypointWorldRenderer.init();
        AnarchyNavigator.init();
        WorldRenderEvents.AFTER_ENTITIES.register(context -> {
            if (!AuthManager.isAuthorized()) return;
            net.minecraft.client.render.Camera camera = MinecraftClient.getInstance().gameRenderer.getCamera();
            if (AuthManager.hasFeature("waypoints")) {
                WaypointWorldRenderer.render(context.matrices(), camera);
            }
            com.spothelper.hitbox.HitboxManager.render(context.matrices(), camera);
        });

        // ---- HUD ----
        HudRenderCallback.EVENT.register((context, tickCounter) -> {
            if (!AuthManager.isAuthorized()) return;
            if (AuthManager.hasFeature("minimap")) Minimap.render(context, 1.0f);
            StatusHud.render(context);
            com.spothelper.hud.InventoryHud.render(context);
            com.spothelper.tnt.TntTimerManager.render(context, tickCounter.getTickProgress(true));
            if (AuthManager.hasFeature("waypoints")) WaypointLabels.render(context);
            if (AuthManager.hasFeature("hp_overlay")) {
                HealthOverlay.render(context, tickCounter.getTickProgress(true)); // TODO: проверить имя в IDE (getTickProgress)
            }
        });

        // ---- Тик ----
        ClientTickEvents.END_CLIENT_TICK.register(client -> {

            F5FreeLookManager.tick(client);
            ZoomManager.tick(client);
            com.spothelper.hitbox.HitboxManager.tick(client);
            com.spothelper.tnt.TntTimerManager.tick(client);
            com.spothelper.autosprint.AutoSprintManager.tick(client);
            AuthManager.tick(client);

            // Доступ закрыли, пока было открыто главное меню: переводим на экран входа.
            if (client.currentScreen instanceof SpotHelperScreen && !AuthManager.isAuthorized()) {
                client.setScreen(new AuthScreen(null));
            }

            if (pendingScreen != null) {
                client.setScreen(pendingScreen);
                pendingScreen = null;
            }

            while (OPEN_MENU.wasPressed()) {
                if (client.currentScreen == null) {
                    AuthManager.refresh(); // свежая проверка прямо при открытии меню
                    if (AuthManager.isAuthorized()) {
                        client.setScreen(new SpotHelperScreen(null));
                    } else {
                        client.setScreen(new AuthScreen(null));
                    }
                }
            }

            // Смена аккаунта доступна всегда (иначе из заблокированного состояния не выбраться).
            while (OPEN_ACCOUNT_SWITCHER.wasPressed()) {
                if (client.currentScreen == null) {
                    client.setScreen(new AccountSwitcherScreen(null));
                }
            }

            // Эти тики работают всегда: иначе состояние EventFlow может зависнуть.
            EventFlow.tick(client);
            WaypointManager.tick(client);

            if (!AuthManager.isAuthorized()) {
                drainKeys();
                return;
            }

            CommandBindManager.tick(client);

            while (ADD_WAYPOINT.wasPressed()) {
                if (AuthManager.hasFeature("waypoints") && client.currentScreen == null && client.player != null) {
                    Waypoint w = WaypointManager.addHere(null);
                    if (w != null) {
                        client.player.sendMessage(Text.literal("§aSpotHelper: метка §f" + w.name
                                + " §7(" + w.x + " " + w.y + " " + w.z + ")"), true);
                    }
                }
            }
            while (OPEN_WAYPOINTS.wasPressed()) {
                if (AuthManager.hasFeature("waypoints") && client.currentScreen == null) {
                    client.setScreen(new WaypointScreen(null));
                }
            }
            while (TOGGLE_MINIMAP.wasPressed()) {
                if (AuthManager.hasFeature("minimap")) SpotHelperConfigToggle.toggleMinimap();
            }
            while (TOGGLE_INV_HUD.wasPressed()) {
                com.spothelper.hud.InventoryHud.toggle();
            }
            while (MINIMAP_ZOOM_IN.wasPressed()) {
                if (AuthManager.hasFeature("minimap")) Minimap.zoom(1);
            }
            while (MINIMAP_ZOOM_OUT.wasPressed()) {
                if (AuthManager.hasFeature("minimap")) Minimap.zoom(-1);
            }
            while (OPEN_MINIMAP_SETTINGS.wasPressed()) {
                if (AuthManager.hasFeature("minimap") && client.currentScreen == null) {
                    client.setScreen(new MinimapSettingsScreen(null));
                }
            }

        });
    }

    /** Без доступа гасим нажатия, чтобы они не «выстрелили» сразу после авторизации. */
    private static void drainKeys() {
        while (ADD_WAYPOINT.wasPressed()) { }
        while (OPEN_WAYPOINTS.wasPressed()) { }
        while (TOGGLE_MINIMAP.wasPressed()) { }
        while (MINIMAP_ZOOM_IN.wasPressed()) { }
        while (MINIMAP_ZOOM_OUT.wasPressed()) { }
        while (OPEN_MINIMAP_SETTINGS.wasPressed()) { }
        while (TOGGLE_INV_HUD.wasPressed()) { }
    }
}