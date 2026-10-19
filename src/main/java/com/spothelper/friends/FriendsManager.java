package com.spothelper.friends;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.entity.player.PlayerEntity;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;

/** Список друзей (ники). Друзей нельзя ударить. Хранится в config/spothelper_friends.json. */
public final class FriendsManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("spothelper_friends.json");
    private static final List<String> FRIENDS = new CopyOnWriteArrayList<>();
    private static volatile boolean loaded;

    private FriendsManager() {}

    private static synchronized void ensureLoaded() {
        if (loaded) return;
        loaded = true;
        if (!Files.exists(PATH)) return;
        try (Reader r = Files.newBufferedReader(PATH)) {
            List<String> parsed = GSON.fromJson(r, new TypeToken<List<String>>() {}.getType());
            if (parsed != null) {
                for (String s : parsed) {
                    String n = clean(s);
                    if (isValidName(n) && indexOf(n) < 0) FRIENDS.add(n);
                }
            }
        } catch (Exception e) {
            com.spothelper.util.Log.error("friends load", e);
        }
    }

    private static synchronized void save() {
        try (Writer w = Files.newBufferedWriter(PATH)) {
            GSON.toJson(new ArrayList<>(FRIENDS), w);
        } catch (IOException e) {
            com.spothelper.util.Log.error("friends save", e);
        }
    }

    private static String clean(String s) {
        return s == null ? "" : s.trim();
    }

    /** Ник Minecraft: 3–16 символов, латиница, цифры и «_». */
    public static boolean isValidName(String name) {
        return name != null && name.matches("[A-Za-z0-9_]{3,16}");
    }

    private static int indexOf(String name) {
        for (int i = 0; i < FRIENDS.size(); i++) {
            if (FRIENDS.get(i).equalsIgnoreCase(name)) return i;
        }
        return -1;
    }

    public static List<String> list() {
        ensureLoaded();
        return new ArrayList<>(FRIENDS);
    }

    public static boolean isFriend(String name) {
        ensureLoaded();
        if (name == null || name.isEmpty()) return false;
        return indexOf(name.trim().toLowerCase(Locale.ROOT)) >= 0;
    }

    public static boolean isFriend(PlayerEntity player) {
        if (player == null || player.getGameProfile() == null) return false;
        return isFriend(player.getGameProfile().name());
    }

    /** 1 — добавлен, 0 — уже есть, -1 — неверный ник. */
    public static synchronized int add(String raw) {
        ensureLoaded();
        String name = clean(raw);
        if (!isValidName(name)) return -1;
        if (indexOf(name) >= 0) return 0;
        FRIENDS.add(name);
        save();
        return 1;
    }

    public static synchronized void remove(String name) {
        ensureLoaded();
        int i = indexOf(clean(name));
        if (i >= 0) {
            FRIENDS.remove(i);
            save();
        }
    }
}