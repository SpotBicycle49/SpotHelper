package com.spothelper.gui;

import com.spothelper.config.SpotHelperConfig;

import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.Arrays;

public class SpotHelperConfigScreen {

    public static Screen create(Screen parent) {
        SpotHelperConfig cfg = SpotHelperConfig.INSTANCE;

        ConfigBuilder builder = ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(Text.literal("SpotHelper"))
                .setSavingRunnable(cfg::save);

        ConfigEntryBuilder entry = builder.entryBuilder();


        // === Миникарта ===
        ConfigCategory mm = builder.getOrCreateCategory(Text.literal("Миникарта"));
        mm.addEntry(entry.startTextDescription(Text.literal(
                        "Расширенные настройки миникарты — через клавишу Y в игре."))
                .build());
        mm.addEntry(entry.startBooleanToggle(Text.literal("Миникарта включена"), cfg.isMinimapEnabled())
                .setDefaultValue(true).setSaveConsumer(v -> cfg.minimapEnabled = v ? 1 : 0).build());

        // === HolyWorld Control ===
        ConfigCategory holy = builder.getOrCreateCategory(Text.literal("HolyWorld Control"));
        holy.addEntry(entry.startStrField(Text.literal("Адрес Bridge"), cfg.holyWorldHost)
                .setDefaultValue("127.0.0.1").setSaveConsumer(v -> cfg.holyWorldHost = v).build());
        holy.addEntry(entry.startIntField(Text.literal("Порт Bridge"), cfg.holyWorldPort)
                .setDefaultValue(48765).setMin(1).setMax(65535).setSaveConsumer(v -> cfg.holyWorldPort = v).build());
        holy.addEntry(entry.startBooleanToggle(Text.literal("Автообновление ивентов"), cfg.isHolyWorldEventsAutoRefresh())
                .setDefaultValue(true).setSaveConsumer(v -> cfg.holyWorldEventsAutoRefresh = v ? 1 : 0).build());
        holy.addEntry(entry.startIntSlider(Text.literal("Опрос ивентов, сек."), cfg.holyWorldEventsInterval, 5, 60)
                .setDefaultValue(5).setSaveConsumer(v -> cfg.holyWorldEventsInterval = v).build());

        // === Замок шалкера ===
        ConfigCategory lock = builder.getOrCreateCategory(Text.literal("Замок шалкера"));
        lock.addEntry(entry.startTextDescription(Text.literal(
                        "Пока шалкеров в инвентаре больше разрешённого числа, SpotHelper не даёт уйти из игры. Работает только на стороне клиента."))
                .build());
        lock.addEntry(entry.startBooleanToggle(Text.literal("Замок включён"), cfg.shulkerLock)
                .setDefaultValue(true).setSaveConsumer(v -> cfg.shulkerLock = v).build());
        lock.addEntry(entry.startIntSlider(Text.literal("Разрешено шалкеров без блокировки"), cfg.getShulkerLockAllowed(), 0, 37)
                .setDefaultValue(0)
                .setTooltip(Text.literal("0 — блокировать при любом шалкере."))
                .setSaveConsumer(v -> cfg.shulkerLockAllowed = v).build());
        lock.addEntry(entry.startBooleanToggle(Text.literal("Блокировать команды"), cfg.shulkerLockCommands)
                .setDefaultValue(true).setSaveConsumer(v -> cfg.shulkerLockCommands = v).build());
        lock.addEntry(entry.startStrList(Text.literal("Команды (без «/»)"), cfg.shulkerLockCommandList)
                .setDefaultValue(new ArrayList<>(Arrays.asList("hub", "рги", "lobby")))
                .setTooltip(Text.literal("Раскладка не важна: «рги» считается командой «hub»"))
                .setSaveConsumer(v -> cfg.shulkerLockCommandList = new ArrayList<>(v)).build());
        lock.addEntry(entry.startBooleanToggle(Text.literal("Блокировать «Отключиться» в меню паузы"), cfg.shulkerLockMenu)
                .setDefaultValue(true).setSaveConsumer(v -> cfg.shulkerLockMenu = v).build());
        lock.addEntry(entry.startBooleanToggle(Text.literal("Блокировать закрытие окна (крестик, Alt+F4)"), cfg.shulkerLockWindow)
                .setDefaultValue(true).setSaveConsumer(v -> cfg.shulkerLockWindow = v).build());

        return builder.build();
    }
}