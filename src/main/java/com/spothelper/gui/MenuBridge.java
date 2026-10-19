package com.spothelper.gui;

import com.spothelper.client.SpotHelperClient;
import com.spothelper.commandbinds.CommandBindManager;
import com.spothelper.config.SpotHelperConfig;
import com.spothelper.filter.EntityFilterScreen;
import com.spothelper.filter.ItemFilterScreen;
import com.spothelper.holyworld.EventFlow;
import com.spothelper.holyworld.HolyWorldData;
import com.spothelper.minimap.ColorPickerScreen;
import com.spothelper.minimap.MinimapSettingsScreen;
import com.spothelper.sellprotect.SellProtectScreen;
import com.spothelper.waypoints.WaypointManager;
import com.spothelper.waypoints.WaypointScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

/**
 * Единственное место, где меню обращается к остальному проекту:
 * поля конфига, снимок API, конструкторы других экранов.
 * Всё, что помечено «ПОДКЛЮЧИТЬ», нужно сверить с реальным кодом проекта.
 */
public final class MenuBridge {
    private MenuBridge() {}

    // ---------------------------------------------------------------- данные для отрисовки

    /** trend: 1 — растёт (▲), -1 — падает (▼), 0 — без изменений. */
    public record Status(boolean ok, boolean loading, double coin, int trend) {}

    public record VoteOption(String name, int votes) {}

    public record Vote(String title, String server, String remaining, List<VoteOption> options) {}

    public record EventRow(String title, String detail, HolyWorldData.EventRow src) {}

    /** Последний бинд: text вида «3 с», running — цикл запущен (цвет OK). */
    public record BindInfo(String text, boolean running) {}

    // ---------------------------------------------------------------- конфиг

    private static SpotHelperConfig cfg() {
        return SpotHelperConfig.INSTANCE;
    }

    public static void save() {
        cfg().save();
    }


    public static boolean hudOn() { return cfg().statusHud == 1; }
    public static void setHudOn(boolean v) { cfg().statusHud = v ? 1 : 0; }

    public static boolean freeLookOn() { return cfg().f5FreeLook == 1; }
    public static void setFreeLookOn(boolean v) { cfg().f5FreeLook = v ? 1 : 0; }
    public static boolean zoomOn() { return cfg().zoomEnabled == 1; }
    public static void setZoomOn(boolean v) { cfg().zoomEnabled = v ? 1 : 0; }
    public static boolean f5ZoomOn() { return cfg().f5ZoomEnabled == 1; }
    public static void setF5ZoomOn(boolean v) { cfg().f5ZoomEnabled = v ? 1 : 0; }

    public static boolean crosshairOn() { return cfg().crosshairCustomColor == 1; }
    public static void setCrosshairOn(boolean v) { cfg().crosshairCustomColor = v ? 1 : 0; }
    public static boolean hitboxesOn() { return cfg().alwaysHitboxes == 1; }
    public static void setHitboxesOn(boolean v) { cfg().alwaysHitboxes = v ? 1 : 0; }
    public static boolean tntOn() { return cfg().isTntTimerEnabled(); }
    public static void setTntOn(boolean v) { cfg().tntTimerEnabled = v ? 1 : 0; }
    public static int tntRange() { return cfg().getTntTimerRange(); }
    public static void setTntRange(int v) { cfg().tntTimerRange = Math.max(4, Math.min(64, v)); }
    public static boolean tntInHud() { return cfg().isTntTimerInHud(); }
    public static void setTntInHud(boolean v) { cfg().tntTimerInHud = v ? 1 : 0; }
    public static boolean autoSprintOn() { return cfg().autoSprint == 1; }
    public static void setAutoSprintOn(boolean v) { cfg().autoSprint = v ? 1 : 0; }

    public static boolean armorOn() { return cfg().armorDurability == 1; }
    public static void setArmorOn(boolean v) { cfg().armorDurability = v ? 1 : 0; }

    public static boolean replyOn() { return cfg().chatReply == 1; }
    public static void setReplyOn(boolean v) { cfg().chatReply = v ? 1 : 0; }

    // Визуализация в мире
    public static boolean wpWorldOn() { return cfg().showWaypointsInWorld == 1; }
    public static void setWpWorldOn(boolean v) { cfg().showWaypointsInWorld = v ? 1 : 0; }
    public static boolean totemColorsOn() { return cfg().totemColors == 1; }
    public static void setTotemColorsOn(boolean v) { cfg().totemColors = v ? 1 : 0; }
    public static boolean anarchyOn() { return cfg().autoSwitchAnarchy == 1; }
    public static void setAnarchyOn(boolean v) { cfg().autoSwitchAnarchy = v ? 1 : 0; }
    public static boolean heightOn() { return cfg().showEntityHeight == 1; }
    public static void setHeightOn(boolean v) { cfg().showEntityHeight = v ? 1 : 0; }
    public static int heightMode() { return Math.max(0, Math.min(2, cfg().entityHeightMode)); }
    public static void cycleHeightMode() { cfg().entityHeightMode = (heightMode() + 1) % 3; }
    public static boolean heightPrefix() { return cfg().entityHeightFormat == 1; }
    public static void toggleHeightPrefix() { cfg().entityHeightFormat = cfg().entityHeightFormat == 1 ? 0 : 1; }
    public static boolean heightColored() { return cfg().entityHeightColorMode == 1; }
    public static void toggleHeightColored() { cfg().entityHeightColorMode = cfg().entityHeightColorMode == 1 ? 0 : 1; }
    public static boolean healthOn() { return cfg().showHealth == 1; }
    public static void setHealthOn(boolean v) { cfg().showHealth = v ? 1 : 0; }
    public static boolean healthWorld() { return cfg().showHealthInWorld == 1; }
    public static void setHealthWorld(boolean v) { cfg().showHealthInWorld = v ? 1 : 0; }
    public static boolean healthHud() { return cfg().showHealthInHud == 1; }
    public static void setHealthHud(boolean v) { cfg().showHealthInHud = v ? 1 : 0; }
    public static int healthRange() { return Math.max(8, Math.min(64, cfg().healthOverlayRange)); }
    public static void setHealthRange(int v) { cfg().healthOverlayRange = Math.max(8, Math.min(64, v)); }

    public static void resetHealth() {
        cfg().showHealthInWorld = 1;
        cfg().showHealthInHud = 0;
        cfg().healthOverlayRange = 32;
        save();
    }

    // Блок шалкера (ShulkerLock)
    public static boolean shulkerOn() { return cfg().shulkerLock; }
    public static void setShulkerOn(boolean v) { cfg().shulkerLock = v; }
    public static int shulkerAllowed() { return cfg().getShulkerLockAllowed(); }
    public static void setShulkerAllowed(int v) { cfg().shulkerLockAllowed = Math.max(0, Math.min(37, v)); }
    public static boolean shulkerCommands() { return cfg().shulkerLockCommands; }
    public static void setShulkerCommands(boolean v) { cfg().shulkerLockCommands = v; }
    public static boolean shulkerMenu() { return cfg().shulkerLockMenu; }
    public static void setShulkerMenu(boolean v) { cfg().shulkerLockMenu = v; }
    public static boolean shulkerWindow() { return cfg().shulkerLockWindow; }
    public static void setShulkerWindow(boolean v) { cfg().shulkerLockWindow = v; }
    public static int shulkerCommandCount() {
        return cfg().shulkerLockCommandList == null ? 0 : cfg().shulkerLockCommandList.size();
    }
    public static String shulkerCommandPreview() {
        if (cfg().shulkerLockCommandList == null || cfg().shulkerLockCommandList.isEmpty()) return "список пуст";
        StringBuilder sb = new StringBuilder();
        for (String e : cfg().shulkerLockCommandList) {
            if (e == null) continue;
            if (sb.length() > 0) sb.append(", ");
            sb.append('/').append(e.startsWith("/") ? e.substring(1) : e);
        }
        return sb.toString();
    }
    public static void openShulkerCommands(Screen parent) { open(new ShulkerCommandsScreen(parent)); }

    public static void resetShulker() {
        cfg().shulkerLockAllowed = 0;
        cfg().shulkerLockCommands = true;
        cfg().shulkerLockMenu = true;
        cfg().shulkerLockWindow = true;
        cfg().shulkerLockCommandList = new java.util.ArrayList<>(java.util.Arrays.asList("hub", "рги", "lobby"));
        save();
    }

    // Гамма. gammaEnabled уже есть; gammaLevel (1–16) — ПОДКЛЮЧИТЬ к существующему полю.
    public static boolean gammaOn() { return cfg().gammaEnabled == 1; }
    public static void setGammaOn(boolean v) { cfg().gammaEnabled = v ? 1 : 0; }
    public static int gammaLevel() { return Math.max(1, Math.min(16, Math.round(cfg().gammaValue))); }
    public static void setGammaLevel(int v) { cfg().gammaValue = Math.max(1, Math.min(16, v)); }

    public static void resetGamma() {
        cfg().gammaValue = 8f;
        save();
    }

    // ПОДКЛЮЧИТЬ: счётчики. Пока возвращают 0 — заменить на размеры реальных списков,
    // например cfg().waypoints.size(), cfg().commandBinds.size(), cfg().sellProtect.size().
    public static int waypointCount() { return WaypointManager.current().size(); }
    public static int bindCount() { return CommandBindManager.getBinds().size(); }
    public static int sellProtectCount() { return cfg().sellProtectItems.size(); }

    /** ПОДКЛЮЧИТЬ: строка последнего бинда; null — не показывать. */
    public static BindInfo lastBind() { return null; }

    // ---------------------------------------------------------------- снимок API, события, голосования

    private static HolyWorldData.Snapshot snap() {
        return SpotHelperClient.API.getSnapshot();
    }

    /** Состояние публичного API и последний курс коина (NaN — курса ещё нет). */
    public static Status status() {
        HolyWorldData.Snapshot s = snap();
        boolean loading = !s.publicOk && s.publicError == null;
        return new Status(s.publicOk, loading, s.coinRate, s.coinTrend);
    }

    /** Запустить опрос публичного API (события, курс, голосования). */
    public static void refresh() {
        SpotHelperClient.API.refreshPublic(null);
    }

    /** Период автообновления в миллисекундах; -1 — автообновление выключено. */
    public static long pollIntervalMs() {
        SpotHelperConfig c = cfg();
        return c.isHolyWorldEventsAutoRefresh() ? Math.max(5, c.holyWorldEventsInterval) * 1000L : -1L;
    }

    /** Список событий, ближайшие сверху. */
    /** События: русские названия, порядок — по номеру анархии (от меньшей к большей), затем по времени. */
    public static List<EventRow> events() {
        List<HolyWorldData.EventRow> src = new ArrayList<>(snap().events);
        src.sort(java.util.Comparator
                .comparingInt((HolyWorldData.EventRow e) -> com.spothelper.holyworld.EventTexts.anarchyNumber(e.serverName))
                .thenComparingInt(e -> e.available ? e.liveSeconds() : Integer.MAX_VALUE));
        List<EventRow> out = new ArrayList<>();
        for (HolyWorldData.EventRow e : src) {
            StringBuilder d = new StringBuilder();
            String server = com.spothelper.holyworld.EventTexts.server(e.serverName);
            if (!server.isEmpty()) d.append(server);
            if (e.available) {
                if (d.length() > 0) d.append(" · ");
                d.append(duration(e.liveSeconds()));
            }
            String rare = com.spothelper.holyworld.EventTexts.rare(e.rare);
            if (!rare.isEmpty()) {
                if (d.length() > 0) d.append(" · ");
                d.append(rare);
            }
            out.add(new EventRow(com.spothelper.holyworld.EventTexts.eventName(e.displayName), d.toString(), e));
        }
        return out;
    }

    public static void startEvent(EventRow e) {
        if (e != null && e.src() != null) EventFlow.start(e.src());
    }

    /** Голосования: русские названия, порядок — по номеру анархии от меньшей к большей. */
    public static List<Vote> votes() {
        List<HolyWorldData.VoteRow> src = new ArrayList<>(snap().votes);
        src.sort(java.util.Comparator.comparingInt(
                (HolyWorldData.VoteRow v) -> com.spothelper.holyworld.EventTexts.anarchyNumber(v.serverName)));
        List<Vote> out = new ArrayList<>();
        for (HolyWorldData.VoteRow v : src) {
            List<VoteOption> opts = new ArrayList<>();
            for (HolyWorldData.VoteOptionRow o : v.options) {
                opts.add(new VoteOption(com.spothelper.holyworld.EventTexts.eventName(o.name, true), o.votes));
            }
            out.add(new Vote(v.title, com.spothelper.holyworld.EventTexts.server(v.serverName),
                    v.hasTime ? duration(v.liveSeconds()) : "—", opts));
        }
        return out;
    }

    /** 75 → «1 мин 15 с», 3700 → «1 ч 01 мин». */
    private static String duration(int sec) {
        sec = Math.max(0, sec);
        if (sec >= 3600) return (sec / 3600) + " ч " + String.format("%02d", (sec % 3600) / 60) + " мин";
        if (sec >= 60) return (sec / 60) + " мин " + String.format("%02d", sec % 60) + " с";
        return sec + " с";
    }

    // ---------------------------------------------------------------- переходы на другие экраны
    // ПОДКЛЮЧИТЬ: сверить конструкторы (здесь везде предполагается (Screen parent)).

    private static void open(Screen s) {
        MinecraftClient.getInstance().setScreen(s);
    }

    public static void openMinimap(Screen parent) { open(new MinimapSettingsScreen(parent)); }
    public static void openWaypoints(Screen parent) { open(new WaypointScreen(parent)); }
    public static void openBinds(Screen parent) { open(new CommandBindScreen(parent)); }
    public static void openHudEdit(Screen parent) { open(new HudEditScreen(parent)); }
    public static void openSellProtect(Screen parent) { open(new SellProtectScreen(parent)); }

    // ПОДКЛЮЧИТЬ: способ выбора списка (враждебные / мирные) в EntityFilterScreen.
    public static void openHostileFilter(Screen parent) { open(new EntityFilterScreen(parent, "Враждебные", cfg().filterHostile,
            list -> { cfg().filterHostile = list; save(); })); }
    public static void openPassiveFilter(Screen parent) { open(new EntityFilterScreen(parent, "Мирные", cfg().filterFriendly,
            list -> { cfg().filterFriendly = list; save(); })); }
    public static void openItemFilter(Screen parent) { open(new ItemFilterScreen(parent, cfg().filterItems,
            list -> { cfg().filterItems = list; save(); })); }

    /** ПОДКЛЮЧИТЬ: конструктор ColorPickerScreen. Цвет приходит в колбэк как RGB (старший байт отбрасывается). */
    public static void openColorPicker(Screen parent, int argb, IntConsumer onPicked) {
        open(new ColorPickerScreen(parent, "Цвет", argb, c -> onPicked.accept(c & 0xFFFFFF)));
    }
}
