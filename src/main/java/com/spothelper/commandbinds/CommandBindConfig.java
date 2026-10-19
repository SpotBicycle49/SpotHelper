package com.spothelper.commandbinds;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.spothelper.commandbinds.CommandBindManager;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Загрузка и сохранение биндов в JSON-файл.
 * Формат: config/spothelper_binds.json
 */
public final class CommandBindConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PATH = FabricLoader.getInstance()
            .getConfigDir().resolve("spothelper_binds.json");

    private CommandBindConfig() {}

    public static void save() {
        try (Writer w = Files.newBufferedWriter(PATH)) {
            GSON.toJson(CommandBindManager.getBinds(), w);
        } catch (IOException e) {
            com.spothelper.util.Log.error("command binds save", e);
        }
    }

    public static void load() {
        List<com.spothelper.commandbinds.CommandBind> loaded = new ArrayList<>();
        if (Files.exists(PATH)) {
            try (Reader r = Files.newBufferedReader(PATH)) {
                List<com.spothelper.commandbinds.CommandBind> parsed = GSON.fromJson(r,
                        new TypeToken<List<com.spothelper.commandbinds.CommandBind>>() {}.getType());
                if (parsed != null) loaded = parsed;
            } catch (IOException e) {
                com.spothelper.util.Log.error("command binds load", e);
            }
        }
        CommandBindManager.getBinds().clear();
        CommandBindManager.getBinds().addAll(loaded);
    }
}