package com.spothelper.holyworld;

import com.spothelper.config.SpotHelperConfig;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;

/**
 * /lt <номер> — переход на Лайт-анархию через меню компаса (тот же путь, что у бота):
 * компас → «Лайт анархия» (слот 11) → категория (Соло/Дуо/Трио/Клан) → сервер (слот 18 + n - start).
 * Если игрок не в лобби (компаса в 1-м слоте нет) — сначала отправляется команда возврата (/hub).
 */
public final class AnarchyNavigator {
    private AnarchyNavigator() {}

    private record Mode(String name, int start, int end, int categorySlot) {}

    private static final Mode[] MODES = {
            new Mode("Соло", 1, 9, 0),
            new Mode("Дуо", 10, 19, 1),
            new Mode("Трио", 20, 29, 2),
            new Mode("Клан", 30, 36, 3)
    };
    private static final int LITE_SLOT = 11;
    private static final int FIRST_SERVER_SLOT = 18;

    private static final int LOBBY_TIMEOUT = 20 * 12;   // ждём /hub
    private static final int COMPASS_TIMEOUT = 20 * 5;  // ждём меню компаса (с повторными попытками)
    private static final int MENU_TIMEOUT = 20 * 4;     // ждём каждое следующее меню

    private enum Step { IDLE, LOBBY, COMPASS, LITE, CATEGORY, SERVER }

    private static Step step = Step.IDLE;
    private static Mode mode;
    private static int target;
    private static int ticks;
    private static int lastSyncId;
    private static boolean hubSent;
    private static boolean failed;

    public static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(AnarchyNavigator::tick);
    }

    public static boolean active() { return step != Step.IDLE; }

    /** true, если последний переход закончился ошибкой. */
    public static boolean lastFailed() { return failed; }

    /** Игрок в лобби: компас в первом слоте хотбара (тот же признак, что использует сам переход). */
    public static boolean inLobby(MinecraftClient mc) {
        return mc.player != null && mc.player.getInventory().getStack(0).isOf(Items.COMPASS);
    }

    public static boolean supports(int n) {
        for (Mode x : MODES) if (n >= x.start() && n <= x.end()) return true;
        return false;
    }

    /** Запуск перехода. Возвращает false, если номер неверный. */
    public static boolean start(int n) { return start(n, false); }

    /** quiet = не писать в чат «переход на анархию…» (сообщения об ошибках остаются). */
    public static boolean start(int n, boolean quiet) {
        MinecraftClient mc = MinecraftClient.getInstance();
        Mode m = null;
        for (Mode x : MODES) if (n >= x.start() && n <= x.end()) m = x;
        if (m == null || mc.player == null) return false;

        mode = m;
        target = n;
        ticks = 0;
        hubSent = false;
        failed = false;
        lastSyncId = -1;
        step = Step.LOBBY;
        if (!quiet) say("§aSpotHelper: §fпереход на анархию §e" + n + " §7(" + m.name() + ")");
        return true;
    }

    private static void tick(MinecraftClient mc) {
        if (step == Step.IDLE) return;
        ClientPlayerEntity p = mc.player;
        if (p == null || mc.interactionManager == null) {
            // идёт смена мира — просто ждём
            if (++ticks > LOBBY_TIMEOUT) fail("не удалось (нет игрока).");
            return;
        }
        ticks++;

        switch (step) {
            case LOBBY -> {
                if (p.getInventory().getStack(0).isOf(Items.COMPASS)) {
                    goTo(Step.COMPASS);
                    return;
                }
                if (!hubSent && p.networkHandler != null) {
                    hubSent = true;
                    String cmd = lobbyCommand();
                    if (!cmd.isEmpty()) p.networkHandler.sendChatCommand(cmd);
                }
                if (ticks > LOBBY_TIMEOUT) fail("не дождался лобби (проверь lobbyCommand в config/spothelper.json).");
            }
            case COMPASS -> {
                HandledScreen<?> hs = openMenu(mc);
                if (hs == null) {
                    // меню компаса ещё нет — пробуем открыть (каждые 15 тиков)
                    if (ticks == 3 || (ticks > 3 && ticks % 15 == 0)) {
                        p.getInventory().setSelectedSlot(0);
                        mc.interactionManager.interactItem(p, Hand.MAIN_HAND);
                    }
                    if (ticks > COMPASS_TIMEOUT) fail("меню компаса не открылось.");
                    return;
                }
                if (click(mc, hs, LITE_SLOT)) goTo(Step.LITE);
                else if (ticks > COMPASS_TIMEOUT) fail("в меню компаса нет «Лайт анархии».");
            }
            case LITE -> {
                HandledScreen<?> hs = freshMenu(mc);
                if (hs != null && click(mc, hs, mode.categorySlot())) goTo(Step.CATEGORY);
                else if (ticks > MENU_TIMEOUT) fail("не открылся выбор категории.");
            }
            case CATEGORY -> {
                HandledScreen<?> hs = freshMenu(mc);
                int slot = FIRST_SERVER_SLOT + (target - mode.start());
                if (hs != null && click(mc, hs, slot)) {
                    say("§aSpotHelper: §fвхожу на анархию §e" + target);
                    step = Step.IDLE;
                } else if (ticks > MENU_TIMEOUT) fail("не открылся список серверов.");
            }
            default -> step = Step.IDLE;
        }
    }

    /** Открытый контейнер (не инвентарь игрока). */
    private static HandledScreen<?> openMenu(MinecraftClient mc) {
        if (mc.currentScreen instanceof HandledScreen<?> hs && hs.getScreenHandler().syncId != 0) return hs;
        return null;
    }

    /** Новое окно, отличное от предыдущего (по syncId). */
    private static HandledScreen<?> freshMenu(MinecraftClient mc) {
        HandledScreen<?> hs = openMenu(mc);
        return hs != null && hs.getScreenHandler().syncId != lastSyncId ? hs : null;
    }

    /** Клик, как только предмет в слоте появился. */
    private static boolean click(MinecraftClient mc, HandledScreen<?> hs, int slot) {
        ScreenHandler h = hs.getScreenHandler();
        if (slot < 0 || slot >= h.slots.size() || !h.getSlot(slot).hasStack()) return false;
        lastSyncId = h.syncId;
        mc.interactionManager.clickSlot(h.syncId, slot, 0, SlotActionType.PICKUP, mc.player);
        return true;
    }

    private static void goTo(Step s) {
        step = s;
        ticks = 0;
    }

    private static String lobbyCommand() {
        String c = SpotHelperConfig.INSTANCE.lobbyCommand;
        if (c == null) return "";
        c = c.trim();
        return c.startsWith("/") ? c.substring(1) : c;
    }

    private static void fail(String why) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null && mc.currentScreen instanceof HandledScreen<?>) mc.player.closeHandledScreen();
        step = Step.IDLE;
        failed = true;
        say("§cSpotHelper: переход на анархию " + target + " — " + why);
    }

    private static void say(String msg) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null) mc.player.sendMessage(Text.literal(msg), false);
    }
}
