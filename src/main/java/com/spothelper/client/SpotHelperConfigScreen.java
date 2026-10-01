package com.spothelper.client;

import com.spothelper.SpotHelperConfig;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.LiteralText;

import java.util.ArrayList;
import java.util.List;

public class SpotHelperConfigScreen {

    public static Screen create(Screen parent) {
        SpotHelperConfig cfg = SpotHelperConfig.INSTANCE;

        ConfigBuilder builder = ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(new LiteralText("SpotHelper"))
                .setSavingRunnable(cfg::save);

        ConfigEntryBuilder entry = builder.entryBuilder();

        // ========== Автоинструмент ==========
        ConfigCategory autoTool = builder.getOrCreateCategory(new LiteralText("Умный выбор инструмента"));

        autoTool.addEntry(entry
                .startBooleanToggle(new LiteralText("Включён"), cfg.isAutoToolEnabled())
                .setDefaultValue(true)
                .setTooltip(new LiteralText("Автовыбор лучшего инструмента при добыче"))
                .setSaveConsumer(v -> cfg.autoTool = v ? 1 : 0)
                .build());

        // ========== ESP ==========
        ConfigCategory esp = builder.getOrCreateCategory(new LiteralText("Визуальные метки"));

        esp.addEntry(entry
                .startBooleanToggle(new LiteralText("ESP включён"), cfg.isEspEnabled())
                .setDefaultValue(false)
                .setTooltip(new LiteralText("Подсветка выбранных блоков"))
                .setSaveConsumer(v -> cfg.espEnabled = v ? 1 : 0)
                .build());

        esp.addEntry(entry
                .startIntSlider(new LiteralText("Режим отображения"), cfg.espMode, 0, 2)
                .setDefaultValue(0)
                .setTooltip(
                        new LiteralText("0 = только контур"),
                        new LiteralText("1 = только заливка"),
                        new LiteralText("2 = контур + заливка")
                )
                .setTextGetter(v -> {
                    switch (v) {
                        case 1:  return new LiteralText("Заливка");
                        case 2:  return new LiteralText("Контур + заливка");
                        default: return new LiteralText("Контур");
                    }
                })
                .setSaveConsumer(v -> cfg.espMode = v)
                .build());

        esp.addEntry(entry
                .startBooleanToggle(new LiteralText("Сквозь стены"), cfg.isEspThroughWalls())
                .setDefaultValue(true)
                .setTooltip(new LiteralText("Видеть подсветку за другими блоками"))
                .setSaveConsumer(v -> cfg.espThroughWalls = v ? 1 : 0)
                .build());

        esp.addEntry(entry
                .startIntSlider(new LiteralText("Дальность"), cfg.espRange, 8, 128)
                .setDefaultValue(64)
                .setTooltip(new LiteralText("Радиус поиска блоков (в блоках)"))
                .setSaveConsumer(v -> cfg.espRange = v)
                .build());

        esp.addEntry(entry
                .startFloatField(new LiteralText("Толщина линий"), cfg.lineWidth)
                .setDefaultValue(2.0f)
                .setMin(0.5f)
                .setMax(8.0f)
                .setTooltip(new LiteralText("Толщина контура"))
                .setSaveConsumer(v -> cfg.lineWidth = v)
                .build());

        // ----- Цвет контура -----
        esp.addEntry(entry
                .startIntSlider(new LiteralText("Контур R"), cfg.outlineR, 0, 255)
                .setDefaultValue(255)
                .setSaveConsumer(v -> cfg.outlineR = v)
                .build());

        esp.addEntry(entry
                .startIntSlider(new LiteralText("Контур G"), cfg.outlineG, 0, 255)
                .setDefaultValue(50)
                .setSaveConsumer(v -> cfg.outlineG = v)
                .build());

        esp.addEntry(entry
                .startIntSlider(new LiteralText("Контур B"), cfg.outlineB, 0, 255)
                .setDefaultValue(50)
                .setSaveConsumer(v -> cfg.outlineB = v)
                .build());

        esp.addEntry(entry
                .startIntSlider(new LiteralText("Контур A (прозрачность)"), cfg.outlineA, 0, 255)
                .setDefaultValue(255)
                .setTooltip(new LiteralText("255 = непрозрачный, 0 = невидимый"))
                .setSaveConsumer(v -> cfg.outlineA = v)
                .build());

        // ----- Цвет заливки -----
        esp.addEntry(entry
                .startIntSlider(new LiteralText("Заливка R"), cfg.fillR, 0, 255)
                .setDefaultValue(255)
                .setSaveConsumer(v -> cfg.fillR = v)
                .build());

        esp.addEntry(entry
                .startIntSlider(new LiteralText("Заливка G"), cfg.fillG, 0, 255)
                .setDefaultValue(50)
                .setSaveConsumer(v -> cfg.fillG = v)
                .build());

        esp.addEntry(entry
                .startIntSlider(new LiteralText("Заливка B"), cfg.fillB, 0, 255)
                .setDefaultValue(50)
                .setSaveConsumer(v -> cfg.fillB = v)
                .build());

        esp.addEntry(entry
                .startIntSlider(new LiteralText("Заливка A (прозрачность)"), cfg.fillA, 0, 255)
                .setDefaultValue(60)
                .setTooltip(new LiteralText("Рекомендуется 30–100, чтобы не перекрывать всё"))
                .setSaveConsumer(v -> cfg.fillA = v)
                .build());

        // ----- Список блоков -----
        List<String> blocksCopy = new ArrayList<>(cfg.espBlocks);

        esp.addEntry(entry
                .startStrList(new LiteralText("Блоки ESP"), blocksCopy)
                .setDefaultValue(new ArrayList<>())
                .setTooltip(
                        new LiteralText("ID блоков, например:"),
                        new LiteralText("minecraft:diamond_ore"),
                        new LiteralText("minecraft:chest"),
                        new LiteralText("Также можно добавлять клавишей J, смотря на блок")
                )
                .setSaveConsumer(list -> {
                    cfg.espBlocks = new ArrayList<>(list);
                    cfg.rebuildCache();
                })
                .build());

        // ========== HolyWorld Control ==========
        ConfigCategory holy = builder.getOrCreateCategory(new LiteralText("HolyWorld Control"));

        holy.addEntry(entry
                .startStrField(new LiteralText("Адрес Bridge"), cfg.holyWorldHost)
                .setDefaultValue("127.0.0.1")
                .setTooltip(new LiteralText("Обычно 127.0.0.1"))
                .setSaveConsumer(v -> cfg.holyWorldHost = v)
                .build());

        holy.addEntry(entry
                .startIntField(new LiteralText("Порт Bridge"), cfg.holyWorldPort)
                .setDefaultValue(48765)
                .setMin(1)
                .setMax(65535)
                .setSaveConsumer(v -> cfg.holyWorldPort = v)
                .build());

        holy.addEntry(entry
                .startBooleanToggle(new LiteralText("Автообновление ивентов"), cfg.isHolyWorldEventsAutoRefresh())
                .setDefaultValue(true)
                .setTooltip(new LiteralText("Мод читает кеш ивентов из HolyWorld Control автоматически"))
                .setSaveConsumer(v -> cfg.holyWorldEventsAutoRefresh = v ? 1 : 0)
                .build());

        holy.addEntry(entry
                .startIntSlider(new LiteralText("Опрос ивентов, сек."), cfg.holyWorldEventsInterval, 5, 60)
                .setDefaultValue(5)
                .setSaveConsumer(v -> cfg.holyWorldEventsInterval = v)
                .build());

        return builder.build();
    }
}