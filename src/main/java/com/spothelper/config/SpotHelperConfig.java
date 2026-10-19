package com.spothelper.config;

import com.spothelper.minimap.MinimapClamp;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.Identifier;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class SpotHelperConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("spothelper.json");
    public static SpotHelperConfig INSTANCE = load();

    public String authApiUrl = "https://c56fc989-ab32-405f-b383-65ce486959b0.tunnel4.com/api/spothelper/auth";
    public int authPollSeconds = 60;
    public int authCacheHours = 1;
    public boolean authRequired = true;
    public String lastAuthUsername = "";
    public String lastAuthUuid = "";
    public boolean lastAuthResult = false;
    public long lastAuthExpiresAt = 0L;
    public String lastAuthTier = "";
    public List<String> lastAuthFeatures = new ArrayList<>();


    public int minimapWaypoints = 1;
    public int eventWaypoint = 1;
    public int eventWaypointMinutes = 30;
    public int armorDurability = 1;
    public int statusHud = 1;
    public int statusHudArmor = 1;
    public int statusHudEffects = 1;
    public int statusHudEffectsMode = 0;   // 0 = названия, 1 = иконки, 2 = иконки + названия
    public int statusHudCoin = 1;
    // положения элементов HUD (координаты в масштабе GUI, левый верхний угол)
    public int hudArmorX = 4, hudArmorY = 40;
    public int hudEffectsX = 4, hudEffectsY = 132;
    public int hudCoinX = 4, hudCoinY = 24;
    public int hudTntX = 4, hudTntY = 180;   // HUD «Таймеры ТНТ»
    public int invHud = 0;                 // Inventory HUD вкл/выкл
    public int invHudMini = 0;             // 0 = обычный, 1 = mini
    public int invHudScale = 100;          // масштаб, %
    public int hudInvX = 4, hudInvY = 220; // положение
    public int chatReply = 0;
    public List<String> chatReplyRules = new ArrayList<>(); // формат: "regex=>ответ"

    public int minimapEnabled = 1;
    public int minimapSizeValue = 500;
    public int minimapRadiusValue = 300;
    public int minimapDetailValue = 500;
    public int minimapUpdateRate = 50;
    public int minimapCorner = 1;
    public int minimapOpacity = 255;
    public int minimapShape = 0;
    public int minimapBorder = 1;
    public int minimapRotate = 0;
    public int minimapCoords = 1;
    public int minimapBiome = 0;
    public int minimapTime = 0;
    public int minimapCaveEnabled = 0;
    public int minimapWaterOpacity = 60;
    public int minimapWaterDepth = 32;

    public int minimapPlayers = 1;
    public int minimapHostile = 1;
    public int minimapFriendly = 0;
    public int minimapItems = 0;
    public int minimapOther = 0;

    public int filterModeHostile = 1;
    public int filterModeFriendly = 1;
    public int filterModeItems = 1;
    public int filterModeOther = 1;

    public List<String> filterHostile = new ArrayList<>();
    public List<String> filterFriendly = new ArrayList<>();
    public List<String> filterItems = new ArrayList<>();
    public List<String> filterOther = new ArrayList<>();

    public int mmColorPlayer   = 0xFFFFFF55;
    public int mmColorHostile  = 0xFFFF3030;
    public int mmColorFriendly = 0xFF55FF55;
    public int mmColorItem     = 0xFFFFFF00;
    public int mmColorOther    = 0xFFFF55FF;

    public int[] radarIconMode  = {0, 1, 1, 1, 1};
    public int[] radarNameMode  = {2, 1, 1, 1, 1};
    public int[] radarSize      = {8, 8, 8, 6, 6};
    public int[] radarNameScale = {100, 100, 100, 100, 100};
    public Map<String, Integer> radarEntitySize = new HashMap<>();
    public boolean radarOutline = false;
    public int radarMaxFaces = 120;

    public String holyWorldHost = "127.0.0.1";
    public int holyWorldPort = 48765;
    public int holyWorldEventsInterval = 5;
    public int holyWorldEventsAutoRefresh = 1;

    public int autoSwitchAnarchy = 1;
    public String eventCommand = "/event";
    public String switchCommand = "/ln {n}";
    public String lobbyCommand = "/hub";   // возврат в лобби перед /lt
    public String anarchyRegex = "(?iu)(?:соло|дуо|трио|клан)?\\s*лайт\\s*[#№:\\-]?\\s*(\\d{1,3})|анархи[яи]\\s*[#№:\\-]?\\s*(\\d{1,3})";

    public boolean shulkerLock = true;
    public boolean shulkerLockCommands = true;
    public boolean shulkerLockMenu = true;
    public boolean shulkerLockWindow = true;
    public int shulkerLockAllowed = 0;
    public List<String> shulkerLockCommandList = new ArrayList<>(Arrays.asList("hub", "рги", "lobby"));

    // --- Визуализация в мире ---
    public int showWaypointsInWorld = 1; // метки в мире: маркер сквозь стены, луч, подпись
    public int totemColors = 1;          // окраска тотемов по названию (Этернити/Инфинити/Стингер)
    public boolean isTotemColorsEnabled() { return totemColors == 1; }
    public int showHealth = 0;           // здоровье сущностей
    public int showHealthInWorld = 1;    //   над сущностями
    public int showHealthInHud = 0;      //   панель цели под прицелом в HUD
    public int healthOverlayRange = 32;  //   радиус, блоков (8–64)

    public String       currentNick = "";
    public List<String> savedNicks  = new ArrayList<>();
    public List<String> savedTwinks = new ArrayList<>();

    // Формат записей: "minecraft:diamond_sword" или "minecraft:diamond_sword|{...}"
    public List<String> sellProtectItems = new ArrayList<>();

    public String lastConfirmedId = "";
    public String lastConfirmedComponents = "";
    public int lastConfirmedPrice = -1;
    public int gammaEnabled = 0;
    public float gammaValue = 4.0f;   // 1.0 = 100%, 16.0 = 1600%
    // ---- 2.1 Высота сущностей на радаре ----
    public int showEntityHeight = 1;          // 0/1
    public int entityHeightMode = 0;          // 0 — всегда, 1 — только у враждебных, 2 — при Tab
    public int entityHeightColorMode = 1;     // 0 — один цвет, 1 — по разнице с игроком
    public int entityHeightFormat = 1;        // 0 — только число, 1 — с префиксом «Y:»

    // ---- 2.3 Пингование ----
    public int pingKey = 80;                  // GLFW_KEY_P
    public int pingOnLowHealth = 0;           // авто-пинг «Пингер здоровья»
    public float pingHealthThreshold = 10.0f; // порог HP
    public int pingRange = 32;                // радиус, блоков

    public int showSlotNumbers = 1;           // буквы клавиш на слотах в окне инвентаря

    // --- Свободная камера F5 ---
    public int f5FreeLook = 0;
    public float f5FreeLookSensitivity = 1.0f;
    public float f5FreeLookRange = 360.0f;
    public int f5FreeLookReturn = 0;

    // --- Обычный зум ---
    public int zoomEnabled = 0;
    public int zoomKey = 90; // GLFW_KEY_Z
    public float zoomDefault = 0.30f;
    public float zoomMin = 0.10f;
    public float zoomMax = 1.00f;
    public float zoomScrollStep = 0.05f;

    // --- Зум дистанции камеры F5 ---
    public int f5ZoomEnabled = 0;
    public float f5ZoomDistance = 4.0f;
    public float f5ZoomMin = 1.0f;
    public float f5ZoomMax = 16.0f;
    public float f5ZoomScrollStep = 0.5f;

    // --- Прицел ---
    public int crosshairCustomColor = 0;
    public int crosshairTargetColor = 0xFFFF3030;
    public int crosshairBlockColor = 0xFFFFFF55;
    public int crosshairDefaultColor = 0xFFFFFFFF;

    // --- Хитбоксы и автоспринт ---
    public int alwaysHitboxes = 0;
    public int autoSprint = 0;

    // --- Отключение негативных визуальных эффектов ---
    public int negEffectsEnabled = 0;
    public int negEffectsMask = 0xFF;                      // биты эффектов, см. NegativeEffects

    // --- Блокировка слотов (SlotLock) ---
    public int slotLockEnabled = 0;
    public int slotLockMode = 7;                           // битовая маска: 1 = выброс, 2 = перемещение, 4 = продажа
    public int[] slotModes = new int[36];                  // маска запретов для каждого слота 0–35 (0 = не заблокирован)
    public List<Integer> lockedSlots = new ArrayList<>();  // устарело: только для миграции старых конфигов
    //  // индексы слотов инвентаря игрока 0–35 (0–8 хотбар, 9–35 основной)

    // --- Кастомные звуки критов (config/spothelper/crits/*.ogg) ---
    public int critSoundsEnabled = 0;
    public String critSoundName = "";                      // имя файла в папке; пусто — стандартный звук
    public float critSoundVolume = 1.0f;                   // 0.0–1.0
    public float critSoundPitch = 1.0f;                    // 0.5–2.0 (не используется)
    public int critSoundRandom = 0;                        // 1 — случайный звук из critSoundPool
    public List<String> critSoundPool = new ArrayList<>(); // файлы для случайного режима
    public int critSoundReplaceVanilla = 1;                // 1 — подавлять ванильный звук крита, пока играет свой
    // --- Кастомные звуки обычного удара (config/SpotHelper/hit_sounds/) ---
    public int hitSoundsEnabled = 0;
    public String hitSoundName = "";
    public float hitSoundVolume = 1.0f;                    // 0.0–1.0
    public int hitSoundRandom = 0;
    public List<String> hitSoundPool = new ArrayList<>();
    public int hitSoundReplaceVanilla = 1;

    /** Настройки остальных типов ударов (ключ — id канала: hit_player, hit_mob, sweep, air, block, spam). */
    public static final class SoundSlot {
        public int enabled = 0;
        public String name = "";
        public float volume = 1.0f;
        public int random = 0;
        public List<String> pool = new ArrayList<>();
        public int replaceVanilla = 1;
    }
    public Map<String, SoundSlot> soundSlots = new LinkedHashMap<>();

    // --- Фоновая музыка (config/spothelper/music/*.ogg) ---
    public int musicEnabled = 0;
    public String musicTrack = "";                         // имя файла в папке
    public float musicVolume = 0.5f;                       // 0.0–1.0
    public int musicLoop = 1;

    // --- Таймер ТНТ ---
    public int tntTimerEnabled = 0;
    public int tntTimerRange = 16;                         // радиус, блоков (4–64)
    public int tntTimerInHud = 1;                          // перемещаемый HUD-список всех активных ТНТ

    // --- Красный тик урона ---
    public int damageTintEnabled = 0;
    public int damageTintColor = 0xFFFF0000;               // ARGB (альфа берётся из damageTintAlpha)
    public float damageTintAlpha = 0.3f;                   // 0.0–1.0
    public int damageTintDurationTicks = 10;               // 1–40

    public boolean isF5FreeLook() { return f5FreeLook == 1; }
    public float getF5FreeLookSensitivity() { return Math.max(0.05f, Math.min(5.0f, f5FreeLookSensitivity)); }
    public float getF5FreeLookRange() { return Math.max(0.0f, Math.min(360.0f, f5FreeLookRange)); }
    public boolean isF5FreeLookReturn() { return f5FreeLookReturn == 1; }
    public boolean isZoomEnabled() { return zoomEnabled == 1; }
    public int getZoomKey() { return zoomKey > 0 ? zoomKey : 90; }
    public float getZoomDefault() { return Math.max(0.1f, Math.min(1.0f, zoomDefault)); }
    public float getZoomMin() { return Math.max(0.05f, Math.min(1.0f, zoomMin)); }
    public float getZoomMax() { return Math.max(getZoomMin(), Math.min(1.0f, zoomMax)); }
    public float getZoomScrollStep() { return Math.max(0.01f, Math.min(0.25f, zoomScrollStep)); }
    public boolean isF5ZoomEnabled() { return f5ZoomEnabled == 1; }
    public float getF5ZoomDistance() { return Math.max(getF5ZoomMin(), Math.min(getF5ZoomMax(), f5ZoomDistance)); }
    public float getF5ZoomMin() { return Math.max(1.0f, Math.min(16.0f, f5ZoomMin)); }
    public float getF5ZoomMax() { return Math.max(getF5ZoomMin(), Math.min(32.0f, f5ZoomMax)); }
    public float getF5ZoomScrollStep() { return Math.max(0.1f, Math.min(4.0f, f5ZoomScrollStep)); }

    public boolean isShowEntityHeight() { return showEntityHeight == 1; }
    public boolean isPingOnLowHealth() { return pingOnLowHealth == 1; }
    public float getPingHealthThreshold() { return Math.max(1f, Math.min(40f, pingHealthThreshold)); }
    public int getPingRange() { return Math.max(4, Math.min(128, pingRange)); }
    public boolean isShowSlotNumbers() { return showSlotNumbers == 1; }


    // --- Геттеры новых систем (безопасные значения для старых/ручных конфигов) ---
    public boolean isSlotLockEnabled() { return slotLockEnabled == 1; }
    public int getSlotLockMode() { return Math.max(0, Math.min(7, slotLockMode)); }
    public boolean isCritSoundsEnabled() { return critSoundsEnabled == 1; }
    public float getCritSoundVolume() { return Math.max(0.0f, Math.min(1.0f, critSoundVolume)); }
    public float getCritSoundPitch() { return Math.max(0.5f, Math.min(2.0f, critSoundPitch)); }
    public boolean isMusicEnabled() { return musicEnabled == 1; }
    public float getMusicVolume() { return Math.max(0.0f, Math.min(1.0f, musicVolume)); }
    public boolean isMusicLoop() { return musicLoop == 1; }
    public boolean isTntTimerEnabled() { return tntTimerEnabled == 1; }
    public int getTntTimerRange() { return Math.max(4, Math.min(64, tntTimerRange)); }
    public boolean isTntTimerInHud() { return tntTimerInHud == 1; }
    public boolean isDamageTintEnabled() { return damageTintEnabled == 1; }
    public float getDamageTintAlpha() { return Math.max(0.0f, Math.min(1.0f, damageTintAlpha)); }
    public int getDamageTintDurationTicks() { return Math.max(1, Math.min(40, damageTintDurationTicks)); }

    public boolean isGammaEnabled() { return gammaEnabled == 1; }
    public float getGammaValue() { return Math.max(0f, Math.min(16f, gammaValue)); }

    public int fancyText = 1;

    // --- Оформление меню (UiTheme) ---
    public int themePreset = 0;        // индекс пресета; 4 = «Своя»
    public int[] themeColors = null;   // 12 значений ARGB в порядке UiTheme.Role
    public int themeOpacity = 100;     // прозрачность окна и сайдбара, 40–100 (%)



    public int customCursors = 1;
    public float guiScale = 0f;
    public float guiOffsetX = 0f;
    public float guiOffsetY = 0f;


    private SpotHelperConfig() {}

    public boolean isArmorDurabilityEnabled() { return armorDurability == 1; }
    public boolean isStatusHudEnabled() { return statusHud == 1; }
    public boolean isChatReplyEnabled() { return chatReply == 1; }
    public boolean isMinimapWaypoints() { return minimapWaypoints == 1; }
    public boolean isEventWaypoint() { return eventWaypoint == 1; }
    public boolean isMinimapEnabled() { return minimapEnabled == 1; }
    public boolean isMinimapPlayers() { return minimapPlayers == 1; }
    public boolean isMinimapHostile() { return minimapHostile == 1; }
    public boolean isMinimapFriendly() { return minimapFriendly == 1; }
    public boolean isMinimapItems() { return minimapItems == 1; }
    public boolean isMinimapOther() { return minimapOther == 1; }
    public boolean isMinimapCoords() { return minimapCoords == 1; }
    public boolean isMinimapBiome() { return minimapBiome == 1; }
    public boolean isMinimapTime() { return minimapTime == 1; }
    public boolean isMinimapRotate() { return minimapRotate == 1; }
    public boolean isMinimapCircle() { return minimapShape == 1; }
    public boolean isHolyWorldEventsAutoRefresh() { return holyWorldEventsAutoRefresh == 1; }

    public boolean isCaveModeEnabled() { return minimapCaveEnabled == 1; }
    public int getMinimapUpdateRate() { return Math.clamp(minimapUpdateRate, 0, 100); }
    public int getMinimapWaterOpacity() { return Math.clamp(minimapWaterOpacity, 0, 100); }
    public int getMinimapWaterDepth() { return Math.clamp(minimapWaterDepth, 2, 256); }

    public int realMinimapSize() { return (int) Math.round(minimapSizeValue / 1000.0 * 256.0); }
    public int realMinimapRadius() {
        int r = (int) Math.round(16 + (minimapRadiusValue / 1000.0) * 80.0);
        return MinimapClamp.clampRadius(r);
    }
    public int realMinimapTexSize() {
        int size = (int) Math.round(64 + (minimapDetailValue / 1000.0) * (256 - 64));
        size = (size / 16) * 16;
        return Math.max(64, Math.min(256, size));
    }

    public static final int RADAR_CATS = 5;

    public int getShulkerLockAllowed() { return Math.clamp(shulkerLockAllowed, 0, 37); }

    private static int radarAt(int[] a, int cat, int def, int min, int max) {
        int v = (a != null && cat >= 0 && cat < a.length) ? a[cat] : def;
        return Math.clamp(v, min, max);
    }
    public int getRadarIconMode(int cat)  { return radarAt(radarIconMode, cat, 1, 0, 2); }
    public int getRadarNameMode(int cat)  { return radarAt(radarNameMode, cat, 1, 0, 2); }
    public int getRadarSize(int cat)      { return radarAt(radarSize, cat, 6, 2, 24); }
    public int getRadarMaxFaces() { return Math.clamp(radarMaxFaces, 0, 300); }
    public int getRadarNameScale(int cat) { return radarAt(radarNameScale, cat, 100, 50, 250); }

    public int getRadarSizeFor(String typeId, int cat) {
        if (typeId != null && radarEntitySize != null) {
            Integer o = radarEntitySize.get(typeId);
            if (o != null) return Math.clamp(o, 2, 24);
        }
        return getRadarSize(cat);
    }

    private static int[] fit(int[] a, int[] def) {
        int[] r = def.clone();
        if (a != null) System.arraycopy(a, 0, r, 0, Math.min(a.length, r.length));
        return r;
    }

    public void fixRadar() {
        radarIconMode = fit(radarIconMode, new int[]{0, 1, 1, 1, 1});
        radarNameMode = fit(radarNameMode, new int[]{2, 1, 1, 1, 1});
        radarSize = fit(radarSize, new int[]{8, 8, 8, 6, 6});
        radarNameScale = fit(radarNameScale, new int[]{100, 100, 100, 100, 100});
        if (radarEntitySize == null) radarEntitySize = new HashMap<>();
    }

    // ============================================================
    //   ЗАЩИТА ПРОДАЖИ (по ID, парсит запись до '|')
    // ============================================================

    public static String protectId(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return "";
        return Registries.ITEM.getId(stack.getItem()).toString();
    }

    /** Извлекает ID из записи вида "minecraft:diamond_sword" или "minecraft:diamond_sword|{...}". */
    public static String entryId(String entry) {
        if (entry == null || entry.isEmpty()) return "";
        int bar = entry.indexOf('|');
        return bar < 0 ? entry : entry.substring(0, bar);
    }

    /** Для совместимости со старыми вызовами (не используется). */
    public static String protectKey(ItemStack stack, RegistryWrapper.WrapperLookup registries) {
        return protectId(stack);
    }

    public static ItemStack stackFromEntry(String entry, RegistryWrapper.WrapperLookup registries) {
        if (entry == null || entry.isEmpty()) return ItemStack.EMPTY;
        String idPart = entryId(entry);
        Identifier id = Identifier.tryParse(idPart);
        if (id == null) return ItemStack.EMPTY;
        var item = Registries.ITEM.get(id);
        if (item == null || item == net.minecraft.item.Items.AIR) return ItemStack.EMPTY;
        return new ItemStack(item);
    }

    /** Защита по ID (одноаргументная — вызывается из SpotHelperClient). */
    public boolean isSellProtected(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        String id = protectId(stack);
        for (String entry : sellProtectItems) {
            if (entry == null) continue;
            if (entryId(entry).equals(id)) return true;
        }
        return false;
    }

    public boolean isSellProtected(ItemStack stack, RegistryWrapper.WrapperLookup registries) {
        return isSellProtected(stack);
    }

    public boolean addSellProtected(ItemStack stack, RegistryWrapper.WrapperLookup registries) {
        String id = protectId(stack);
        if (id.isEmpty()) return false;
        for (String entry : sellProtectItems) {
            if (entryId(entry).equals(id)) return true;
        }
        sellProtectItems.add(id);
        save();
        return true;
    }

    public void addSellProtectedId(ItemStack stack) {
        addSellProtected(stack, null);
    }

    public boolean matchesLastConfirmed(ItemStack stack, int price) {
        if (stack == null || stack.isEmpty()) return false;
        String id = protectId(stack);
        return id.equals(lastConfirmedId) && price == lastConfirmedPrice;
    }

    public void rememberConfirmed(ItemStack stack, int price) {
        if (stack == null || stack.isEmpty()) return;
        lastConfirmedId = protectId(stack);
        lastConfirmedComponents = "";
        lastConfirmedPrice = price;
    }

    public void clearConfirmed() {
        lastConfirmedId = "";
        lastConfirmedComponents = "";
        lastConfirmedPrice = -1;
    }

    // ============================================================

    public boolean passesFilter(String id, int mode, List<String> list) {
        boolean whitelist = mode == 0;
        boolean inList = list.contains(id);
        if (whitelist) return list.isEmpty() || inList;
        else return !inList;
    }


    public String getHolyWorldBaseUrl() {
        String host = holyWorldHost == null || holyWorldHost.trim().isEmpty() ? "127.0.0.1" : holyWorldHost.trim();
        return "http://" + host + ":" + Math.max(1, Math.min(65535, holyWorldPort));
    }

    public void save() {
        try (Writer w = Files.newBufferedWriter(PATH)) { GSON.toJson(this, w); }
        catch (IOException e) { com.spothelper.util.Log.error("config save", e); }
    }

    private static SpotHelperConfig load() {
        if (Files.exists(PATH)) {
            try (Reader r = Files.newBufferedReader(PATH)) {
                SpotHelperConfig cfg = GSON.fromJson(r, SpotHelperConfig.class);
                if (cfg != null) {
                    if (cfg.filterHostile == null) cfg.filterHostile = new ArrayList<>();
                    if (cfg.filterFriendly == null) cfg.filterFriendly = new ArrayList<>();
                    if (cfg.filterItems == null) cfg.filterItems = new ArrayList<>();
                    if (cfg.filterOther == null) cfg.filterOther = new ArrayList<>();
                    if (cfg.sellProtectItems == null) cfg.sellProtectItems = new ArrayList<>();
                    if (cfg.savedNicks == null) cfg.savedNicks = new ArrayList<>();
                    if (cfg.savedTwinks == null) cfg.savedTwinks = new ArrayList<>();
                    if (cfg.chatReplyRules == null) cfg.chatReplyRules = new ArrayList<>();
                    if (cfg.currentNick == null) cfg.currentNick = "";
                    if (cfg.critSoundName == null) cfg.critSoundName = "";
                    if (cfg.critSoundPool == null) cfg.critSoundPool = new ArrayList<>();
                    cfg.critSoundPool.removeIf(v -> v == null || v.isEmpty());
                    cfg.critSoundVolume = Math.max(0f, Math.min(1f, cfg.critSoundVolume));
                    if (cfg.hitSoundName == null) cfg.hitSoundName = "";
                    if (cfg.hitSoundPool == null) cfg.hitSoundPool = new ArrayList<>();
                    cfg.hitSoundPool.removeIf(v -> v == null || v.isEmpty());
                    cfg.hitSoundVolume = Math.max(0f, Math.min(1f, cfg.hitSoundVolume));
                    if (cfg.musicTrack == null) cfg.musicTrack = "";
                    if (cfg.soundSlots == null) cfg.soundSlots = new LinkedHashMap<>();
                    cfg.soundSlots.values().removeIf(Objects::isNull);
                    for (SoundSlot sl : cfg.soundSlots.values()) {
                        if (sl.name == null) sl.name = "";
                        if (sl.pool == null) sl.pool = new ArrayList<>();
                        sl.pool.removeIf(v -> v == null || v.isEmpty());
                        sl.volume = Math.max(0f, Math.min(1f, sl.volume));
                    }
                    if (cfg.lockedSlots == null) cfg.lockedSlots = new ArrayList<>();
                    cfg.lockedSlots.removeIf(v -> v == null || v < 0 || v > 35);
                    if (cfg.slotModes == null || cfg.slotModes.length != 36) {
                        int[] sm = new int[36];
                        if (cfg.slotModes != null) System.arraycopy(cfg.slotModes, 0, sm, 0, Math.min(36, cfg.slotModes.length));
                        cfg.slotModes = sm;
                    }
                    for (int i = 0; i < 36; i++) cfg.slotModes[i] &= 7;
                    if (!cfg.lockedSlots.isEmpty()) { // миграция: старый общий режим -> режим каждого слота
                        int legacy = cfg.getSlotLockMode() == 0 ? 7 : cfg.getSlotLockMode();
                        for (int v : cfg.lockedSlots) if (cfg.slotModes[v] == 0) cfg.slotModes[v] = legacy;
                        cfg.lockedSlots.clear();
                    }
                    cfg.statusHudEffectsMode = Math.max(0, Math.min(2, cfg.statusHudEffectsMode));
                    cfg.damageTintAlpha = Math.max(0f, Math.min(1f, cfg.damageTintAlpha));
                    cfg.damageTintDurationTicks = Math.max(1, Math.min(40, cfg.damageTintDurationTicks));
                    cfg.fixRadar();
                    if (cfg.shulkerLockCommandList == null) {
                        cfg.shulkerLockCommandList = new ArrayList<>(Arrays.asList("hub", "рги", "lobby"));
                    }
                    return cfg;
                }
            } catch (IOException e) { com.spothelper.util.Log.error("config load", e); }
        }
        return new SpotHelperConfig();
    }
}