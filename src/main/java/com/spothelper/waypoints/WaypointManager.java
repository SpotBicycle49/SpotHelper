package com.spothelper.waypoints;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.spothelper.holyworld.EventFlow;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ServerInfo;

import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Хранит метки в config/spothelper_waypoints.json. Метки привязаны к серверу и измерению. */
public final class WaypointManager {

    public static final int[] PALETTE = {
            0xFF5555, 0xFFAA00, 0xFFFF55, 0x55FF55, 0x55FFFF, 0x5599FF, 0xFF55FF, 0xFFFFFF
    };
    public static final int EVENT_COLOR = 0xFFAA00;

    public static final String OVERWORLD = "minecraft:overworld";
    public static final String NETHER = "minecraft:the_nether";
    public static final String END = "minecraft:the_end";

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PATH =
            FabricLoader.getInstance().getConfigDir().resolve("spothelper_waypoints.json");
    private static final Type LIST_TYPE = new TypeToken<List<Waypoint>>() {}.getType();

    private static List<Waypoint> all = new ArrayList<>();
    private static int cachedAnarchy = -1;
    private static int tickCounter = 0;

    static { load(); }

    private WaypointManager() {}

    // ───────────── контекст ─────────────

    public static String serverKey() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.isInSingleplayer()) return "singleplayer";
        ServerInfo si = mc.getCurrentServerEntry();
        if (si != null && si.address != null) return si.address.toLowerCase(Locale.ROOT);
        return "unknown";
    }

    public static String dimId() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) return "";
        return mc.world.getRegistryKey().getValue().toString();
    }

    /** 0 — Верхний мир, 1 — Незер, 2 — Энд, 3 — прочие. */
    public static int kind(String dim) {
        if (OVERWORLD.equals(dim)) return 0;
        if (NETHER.equals(dim)) return 1;
        if (END.equals(dim)) return 2;
        return 3;
    }

    /** Раз в секунду обновляем номер анархии и чистим просроченные ивент-метки. */
    public static void tick(MinecraftClient mc) {
        if (mc.world == null) return;
        if (++tickCounter % 20 != 0) return;
        try { cachedAnarchy = EventFlow.currentAnarchy(mc); } catch (Throwable t) { cachedAnarchy = -1; }
        purgeExpired();
    }

    // ───────────── выборки ─────────────

    /** Метки для карты: текущий сервер + измерение (+ анархия, если она известна и у метки задана). */
    public static List<Waypoint> current() {
        List<Waypoint> out = new ArrayList<>();
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) return out;
        String srv = serverKey();
        String dim = dimId();
        for (Waypoint w : all) {
            if (!srv.equals(w.server) || !dim.equals(w.dim)) continue;
            if (w.anarchy > 0 && cachedAnarchy > 0 && w.anarchy != cachedAnarchy) continue;
            out.add(w);
        }
        return out;
    }

    /** Все метки текущего сервера, во всех измерениях (для окна управления). */
    public static List<Waypoint> forServer() {
        List<Waypoint> out = new ArrayList<>();
        String srv = serverKey();
        for (Waypoint w : all) {
            if (srv.equals(w.server)) out.add(w);
        }
        return out;
    }

    // ───────────── операции ─────────────

    public static Waypoint add(String name, int x, int y, int z) {
        return add(name, x, y, z, dimId());
    }

    public static Waypoint add(String name, int x, int y, int z, String dim) {
        int count = 0;
        String srv = serverKey();
        for (Waypoint w : all) {
            if (srv.equals(w.server) && dim.equals(w.dim)) count++;
        }
        if (name == null || name.isBlank()) name = "Метка " + (count + 1);
        int color = PALETTE[count % PALETTE.length];
        Waypoint w = new Waypoint(name.trim(), x, y, z, color, srv, dim);
        all.add(w);
        save();
        return w;
    }

    public static Waypoint addHere(String name) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return null;
        return add(name,
                (int) Math.floor(mc.player.getX()),
                (int) Math.floor(mc.player.getY()),
                (int) Math.floor(mc.player.getZ()));
    }

    /**
     * Метка от клика по ивенту. Метка с тем же названием, анархией и измерением заменяется.
     * dimOrNull == null → текущее измерение. ttlMs <= 0 → не удалять.
     */
    public static Waypoint setEvent(String name, int x, int y, int z, int anarchy,
                                    long ttlMs, String dimOrNull) {
        purgeExpired();
        String srv = serverKey();
        String dim = dimOrNull != null ? dimOrNull : dimId();
        all.removeIf(w -> w.event && srv.equals(w.server) && dim.equals(w.dim)
                && w.anarchy == anarchy && w.name.equalsIgnoreCase(name));
        Waypoint w = new Waypoint(name, x, y, z, EVENT_COLOR, srv, dim);
        w.event = true;
        w.anarchy = anarchy;
        w.expiresAt = ttlMs > 0 ? System.currentTimeMillis() + ttlMs : 0L;
        all.add(w);
        save();
        return w;
    }

    public static int clearEvents() {
        String srv = serverKey();
        int before = all.size();
        all.removeIf(w -> w.event && srv.equals(w.server));
        int removed = before - all.size();
        if (removed > 0) save();
        return removed;
    }

    public static void purgeExpired() {
        long now = System.currentTimeMillis();
        int before = all.size();
        all.removeIf(w -> w.expiresAt > 0 && now > w.expiresAt);
        if (all.size() != before) save();
    }

    public static void remove(Waypoint w) {
        all.remove(w);
        save();
    }

    public static boolean removeByName(String name) {
        for (Waypoint w : current()) {
            if (w.name.equalsIgnoreCase(name)) {
                all.remove(w);
                save();
                return true;
            }
        }
        return false;
    }

    public static void cycleColor(Waypoint w) {
        int idx = 0;
        for (int i = 0; i < PALETTE.length; i++) {
            if (PALETTE[i] == (w.color & 0xFFFFFF)) { idx = i; break; }
        }
        w.color = PALETTE[(idx + 1) % PALETTE.length];
        save();
    }

    // ───────────── файл ─────────────

    public static void save() {
        try (Writer wr = Files.newBufferedWriter(PATH, StandardCharsets.UTF_8)) {
            GSON.toJson(all, LIST_TYPE, wr);
        } catch (Exception e) {
            com.spothelper.util.Log.error("waypoints save", e);
        }
    }

    private static void load() {
        try {
            if (Files.exists(PATH)) {
                try (Reader r = Files.newBufferedReader(PATH, StandardCharsets.UTF_8)) {
                    List<Waypoint> loaded = GSON.fromJson(r, LIST_TYPE);
                    if (loaded != null) all = loaded;
                }
            }
        } catch (Exception e) {
            com.spothelper.util.Log.error("waypoints load", e);
            all = new ArrayList<>();
        }
    }
}
