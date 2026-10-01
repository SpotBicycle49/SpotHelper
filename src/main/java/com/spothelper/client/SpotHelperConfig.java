package com.spothelper;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.Block;
import net.minecraft.util.Identifier;
import net.minecraft.util.registry.Registry;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class SpotHelperConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PATH = FabricLoader.getInstance()
            .getConfigDir().resolve("spothelper.json");

    public static SpotHelperConfig INSTANCE = load();

    // --- Автоинструмент ---
    public int autoTool = 1;

    // --- Block ESP ---
    public int espEnabled = 0;              // 0 = выкл, 1 = вкл
    public int espMode = 0;                 // 0 = только контур, 1 = заливка, 2 = контур + заливка
    public int espThroughWalls = 1;         // 0 = нет, 1 = да (сквозь блоки)
    public int espRange = 64;               // радиус поиска блоков

    // Цвет контура RGBA 0–255
    public int outlineR = 255;
    public int outlineG = 50;
    public int outlineB = 50;
    public int outlineA = 255;

    // Цвет заливки RGBA 0–255
    public int fillR = 255;
    public int fillG = 50;
    public int fillB = 50;
    public int fillA = 60;

    // Толщина линий контура
    public float lineWidth = 2.0f;

    // Список блоков: "minecraft:diamond_ore", "minecraft:chest" ...
    public List<String> espBlocks = new ArrayList<>();

    // --- HolyWorld Control ---
    public String holyWorldHost = "127.0.0.1";
    public int holyWorldPort = 48765;
    public int holyWorldEventsInterval = 5;
    public int holyWorldEventsAutoRefresh = 1;

    // --- Переход на анархию и /event ---
    public int autoSwitchAnarchy = 1;       // 1 = при клике на ивент сам переходить на нужную анархию
    public String eventCommand = "/event";  // команда, показывающая координаты ивентов на текущей анархии
    // Команда перехода на анархию, {n} = номер. ПУСТО = не задана (переход выключен).
    // "/ln {n}" - быстрый переход на Лайт-анархию, её добавляет мод HubSwap (на самом сервере такой команды нет).
    // Если HubSwap не стоит, впиши свою команду или оставь пустым.
    public String switchCommand = "/ln {n}";
    // Как найти номер текущей анархии в скорборде. Берётся первая непустая группа с числом.
    public String anarchyRegex = "(?iu)(?:соло|дуо|трио|клан)?\\s*лайт\\s*[#№:\\-]?\\s*(\\d{1,3})|анархи[яи]\\s*[#№:\\-]?\\s*(\\d{1,3})";

    // --- GUI ---
    public int fancyText = 1;               // 1 = свой шрифт (DejaVu), 0 = обычный шрифт Minecraft (безопасный режим)
    public int customCursors = 1;           // 1 = курсоры "рука/стрелки" у краёв окна, 0 = не менять курсор
    public float guiScale = 0f;             // 0 = авто, иначе размер окна SpotHelper (тянется мышкой за край)
    public float guiOffsetX = 0f;           // смещение окна от центра экрана (переносится мышкой)
    public float guiOffsetY = 0f;

    private transient Set<Block> espBlockCache = null;

    private SpotHelperConfig() {}

    public boolean isAutoToolEnabled() { return autoTool == 1; }
    public boolean isEspEnabled() { return espEnabled == 1; }
    public boolean isEspThroughWalls() { return espThroughWalls == 1; }
    public boolean isHolyWorldEventsAutoRefresh() { return holyWorldEventsAutoRefresh == 1; }

    public Set<Block> getEspBlocks() {
        if (espBlockCache == null) rebuildCache();
        return espBlockCache;
    }

    public void rebuildCache() {
        espBlockCache = new HashSet<>();
        for (String id : espBlocks) {
            try {
                Block b = Registry.BLOCK.get(new Identifier(id));
                if (b != null) espBlockCache.add(b);
            } catch (Exception ignored) {}
        }
    }

    public void addEspBlock(Block block) {
        Identifier id = Registry.BLOCK.getId(block);
        if (id == null) return;
        String s = id.toString();
        if (!espBlocks.contains(s)) {
            espBlocks.add(s);
            rebuildCache();
            save();
        }
    }

    public void removeEspBlock(Block block) {
        Identifier id = Registry.BLOCK.getId(block);
        if (id == null) return;
        espBlocks.remove(id.toString());
        rebuildCache();
        save();
    }

    public void toggleEspBlock(Block block) {
        Identifier id = Registry.BLOCK.getId(block);
        if (id == null) return;
        String s = id.toString();
        if (espBlocks.contains(s)) {
            espBlocks.remove(s);
        } else {
            espBlocks.add(s);
        }
        rebuildCache();
        save();
    }

    public String getHolyWorldBaseUrl() {
        String host = holyWorldHost == null || holyWorldHost.trim().isEmpty() ? "127.0.0.1" : holyWorldHost.trim();
        return "http://" + host + ":" + Math.max(1, Math.min(65535, holyWorldPort));
    }

    public void save() {
        try (Writer w = Files.newBufferedWriter(PATH)) {
            GSON.toJson(this, w);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static SpotHelperConfig load() {
        if (Files.exists(PATH)) {
            try (Reader r = Files.newBufferedReader(PATH)) {
                SpotHelperConfig cfg = GSON.fromJson(r, SpotHelperConfig.class);
                if (cfg != null) {
                    if (cfg.espBlocks == null) cfg.espBlocks = new ArrayList<>();
                    cfg.rebuildCache();
                    return cfg;
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return new SpotHelperConfig();
    }
}