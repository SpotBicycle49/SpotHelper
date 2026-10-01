package com.spothelper.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class HolyWorldData {
    public static final class EventRow {
        public final String serverId;
        public final String serverName;
        public final String displayName;
        public final String rare;
        public final String id;
        /** Секунд до события на момент anchorMillis (если приложение отдаёт таймер). */
        public final int seconds;
        public final boolean available;
        public final long anchorMillis;
        /** Координаты события, если приложение их отдаёт. */
        public final boolean hasCoords;
        public final boolean hasY;
        public final int x, y, z;
        /** Список полей JSON события - для подсказки, если координат в данных нет. */
        public final String keys;

        public EventRow(String serverId, String serverName, String displayName, String rare, String id) {
            this(serverId, serverName, displayName, rare, id, 0, false, 0L);
        }

        public EventRow(String serverId, String serverName, String displayName, String rare, String id,
                        int seconds, boolean available, long anchorMillis) {
            this(serverId, serverName, displayName, rare, id, seconds, available, anchorMillis,
                    false, false, 0, 0, 0, "");
        }

        public EventRow(String serverId, String serverName, String displayName, String rare, String id,
                        int seconds, boolean available, long anchorMillis,
                        boolean hasCoords, boolean hasY, int x, int y, int z, String keys) {
            this.hasCoords = hasCoords;
            this.hasY = hasY;
            this.x = x;
            this.y = y;
            this.z = z;
            this.keys = keys == null ? "" : keys;
            this.serverId = serverId;
            this.serverName = serverName;
            this.displayName = displayName;
            this.rare = rare;
            this.id = id;
            this.seconds = seconds;
            this.available = available;
            this.anchorMillis = anchorMillis;
        }

        public int liveSeconds() {
            if (!available) return 0;
            long delta = System.currentTimeMillis() - anchorMillis;
            return (int) Math.max(0L, (long) seconds - delta / 1000L);
        }
    }

    public static final class MineSide {
        public final String mine;
        public final int seconds;
        public final boolean available;
        public final long anchorMillis;

        public MineSide(String mine, int seconds, boolean available, long anchorMillis) {
            this.mine = mine;
            this.seconds = seconds;
            this.available = available;
            this.anchorMillis = anchorMillis;
        }

        public static MineSide empty() {
            return new MineSide("—", 0, false, 0L);
        }

        public int liveSeconds(boolean last) {
            if (!available) return 0;
            long delta = System.currentTimeMillis() - anchorMillis;
            long value = last ? ((long) seconds + delta / 1000L) : ((long) seconds - delta / 1000L);
            return (int) Math.max(0L, value);
        }
    }

    public static final class MineRow {
        public final String world;
        public final String server;
        public final MineSide last;
        public final MineSide next;

        public MineRow(String world, String server, MineSide last, MineSide next) {
            this.world = world;
            this.server = server;
            this.last = last;
            this.next = next;
        }
    }

    public static final class Snapshot {
        public final boolean ok;
        public final String updatedAt;
        public final List<EventRow> events;
        public final List<MineRow> overworld;
        public final List<MineRow> nether;
        public final List<MineRow> end;
        public final boolean telegramAuthorized;
        public final boolean httpConnected;
        public final String error;

        public Snapshot(boolean ok, String updatedAt, List<EventRow> events,
                        List<MineRow> overworld, List<MineRow> nether, List<MineRow> end,
                        boolean telegramAuthorized, boolean httpConnected, String error) {
            this.ok = ok;
            this.updatedAt = updatedAt;
            this.events = Collections.unmodifiableList(new ArrayList<EventRow>(events));
            this.overworld = Collections.unmodifiableList(new ArrayList<MineRow>(overworld));
            this.nether = Collections.unmodifiableList(new ArrayList<MineRow>(nether));
            this.end = Collections.unmodifiableList(new ArrayList<MineRow>(end));
            this.telegramAuthorized = telegramAuthorized;
            this.httpConnected = httpConnected;
            this.error = error;
        }

        public static Snapshot empty() {
            return new Snapshot(true, null,
                    Collections.<EventRow>emptyList(),
                    Collections.<MineRow>emptyList(),
                    Collections.<MineRow>emptyList(),
                    Collections.<MineRow>emptyList(),
                    false, false, null);
        }
    }

    private HolyWorldData() {
    }

    public static Snapshot parseEvents(JsonObject root, Snapshot current) {
        if (root == null) return current;
        List<EventRow> rows = new ArrayList<EventRow>();
        JsonArray events = root.has("events") && root.get("events").isJsonArray()
                ? root.getAsJsonArray("events") : new JsonArray();
        for (JsonElement e : events) {
            if (!e.isJsonObject()) continue;
            JsonObject o = e.getAsJsonObject();
            int secs = 0;
            boolean hasTime = false;
            String[] secKeys = {"seconds", "starts_in", "in_seconds", "eta_seconds"};
            for (String key : secKeys) {
                if (o.has(key) && !o.get(key).isJsonNull()) {
                    try { secs = Math.max(0, o.get(key).getAsInt()); hasTime = true; break; } catch (Exception ignored) { }
                }
            }
            long anchor = System.currentTimeMillis();
            if (!hasTime && o.has("due_at") && !o.get("due_at").isJsonNull()) {
                try {
                    long due = java.time.Instant.parse(o.get("due_at").getAsString()).toEpochMilli();
                    secs = (int) Math.max(0L, (due - anchor) / 1000L);
                    hasTime = true;
                } catch (Exception ignored) { }
            }
            int[] coords = findCoords(o, 0, false);
            rows.add(new EventRow(
                    getString(o, "server_id", ""),
                    getString(o, "server_name", ""),
                    getString(o, "display_name", getString(o, "id", "Событие")),
                    getString(o, "rare", ""),
                    getString(o, "id", ""),
                    secs, hasTime, anchor,
                    coords != null, coords != null && coords[3] == 1,
                    coords == null ? 0 : coords[0], coords == null ? 0 : coords[1], coords == null ? 0 : coords[2],
                    keysOf(o)
            ));
        }
        return new Snapshot(
                root.has("ok") ? root.get("ok").getAsBoolean() : current.ok,
                getString(o(root), "updated_at", current.updatedAt),
                rows, current.overworld, current.nether, current.end,
                current.telegramAuthorized, true, getString(o(root), "last_error", null)
        );
    }

    public static Snapshot parseMines(JsonObject root, Snapshot current) {
        if (root == null) return current;
        return new Snapshot(
                root.has("ok") ? root.get("ok").getAsBoolean() : current.ok,
                getString(root, "updated_at", current.updatedAt),
                current.events,
                parseWorld(root, "overworld"),
                parseWorld(root, "nether"),
                parseWorld(root, "end"),
                current.telegramAuthorized, true, getString(root, "error", null)
        );
    }

    public static Snapshot parseStatus(JsonObject root, Snapshot current) {
        if (root == null) return current;
        boolean authorized = false;
        if (root.has("telegram") && root.get("telegram").isJsonObject()) {
            JsonObject tg = root.getAsJsonObject("telegram");
            authorized = tg.has("authorized") && tg.get("authorized").getAsBoolean();
        }
        boolean ok = !root.has("ok") || root.get("ok").getAsBoolean();
        return new Snapshot(ok, current.updatedAt, current.events,
                current.overworld, current.nether, current.end,
                authorized, true, getString(root, "error", null));
    }

    private static List<MineRow> parseWorld(JsonObject root, String world) {
        List<MineRow> rows = new ArrayList<MineRow>();
        if (!root.has("worlds") || !root.get("worlds").isJsonObject()) return rows;
        JsonObject worlds = root.getAsJsonObject("worlds");
        if (!worlds.has(world) || !worlds.get(world).isJsonArray()) return rows;
        for (JsonElement e : worlds.getAsJsonArray(world)) {
            if (!e.isJsonObject()) continue;
            JsonObject o = e.getAsJsonObject();
            rows.add(new MineRow(
                    world,
                    getString(o, "server", "—"),
                    parseSide(o, "last", true),
                    parseSide(o, "next", false)
            ));
        }
        return rows;
    }

    private static MineSide parseSide(JsonObject parent, String key, boolean last) {
        if (!parent.has(key) || !parent.get(key).isJsonObject()) return MineSide.empty();
        JsonObject o = parent.getAsJsonObject(key);
        String mine = getString(o, "mine", "—");
        if ("—".equals(mine)) return MineSide.empty();
        String secondsKey = last ? "elapsed_seconds" : "seconds";
        int seconds = o.has(secondsKey) && !o.get(secondsKey).isJsonNull()
                ? Math.max(0, o.get(secondsKey).getAsInt()) : 0;
        long anchor = System.currentTimeMillis();
        String timeKey = last ? "started_at" : "due_at";
        if (o.has(timeKey) && !o.get(timeKey).isJsonNull()) {
            try { anchor = java.time.Instant.parse(o.get(timeKey).getAsString()).toEpochMilli(); } catch (Exception ignored) { }
        }
        return new MineSide(mine, seconds, true, anchor);
    }

    private static JsonObject o(JsonObject root) { return root; }

    private static final String[] COORD_KEYS = {"coords", "coordinates", "coord", "pos", "position", "location", "loc", "xyz"};
    private static final java.util.regex.Pattern NUMBER = java.util.regex.Pattern.compile("-?\\d+(?:\\.\\d+)?");

    private static String keysOf(JsonObject o) {
        StringBuilder sb = new StringBuilder();
        for (java.util.Map.Entry<String, JsonElement> e : o.entrySet()) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(e.getKey());
        }
        return sb.toString();
    }

    /**
     * Ищет координаты в JSON события. Понимает x/y/z, вложенные объекты
     * (coords/pos/location...), массивы [x,y,z] и строки "x y z" в полях-координатах.
     * Возвращает {x, y, z, hasY(0/1)} или null.
     */
    static int[] findCoords(JsonElement el, int depth, boolean allowString) {
        if (el == null || el.isJsonNull() || depth > 3) return null;
        if (el.isJsonObject()) {
            JsonObject o = el.getAsJsonObject();
            Integer x = num(o, "x");
            Integer y = num(o, "y");
            Integer z = num(o, "z");
            if (x != null && z != null) return new int[]{x, y == null ? 0 : y, z, y == null ? 0 : 1};
            for (String key : COORD_KEYS) {
                if (o.has(key)) {
                    int[] r = findCoords(o.get(key), depth + 1, true);
                    if (r != null) return r;
                }
            }
            for (java.util.Map.Entry<String, JsonElement> e : o.entrySet()) {
                JsonElement v = e.getValue();
                if (v != null && (v.isJsonObject() || v.isJsonArray())) {
                    int[] r = findCoords(v, depth + 1, false);
                    if (r != null) return r;
                }
            }
            return null;
        }
        if (el.isJsonArray()) {
            JsonArray a = el.getAsJsonArray();
            try {
                if (a.size() >= 3 && a.get(0).isJsonPrimitive() && a.get(1).isJsonPrimitive() && a.get(2).isJsonPrimitive()) {
                    return new int[]{(int) Math.round(a.get(0).getAsDouble()), (int) Math.round(a.get(1).getAsDouble()),
                            (int) Math.round(a.get(2).getAsDouble()), 1};
                }
                if (a.size() == 2 && a.get(0).isJsonPrimitive() && a.get(1).isJsonPrimitive()) {
                    return new int[]{(int) Math.round(a.get(0).getAsDouble()), 0,
                            (int) Math.round(a.get(1).getAsDouble()), 0};
                }
            } catch (Exception ignored) { }
            return null;
        }
        if (allowString && el.isJsonPrimitive() && el.getAsJsonPrimitive().isString()) {
            java.util.regex.Matcher m = NUMBER.matcher(el.getAsString());
            java.util.List<Integer> nums = new ArrayList<Integer>();
            while (m.find() && nums.size() < 3) nums.add((int) Math.round(Double.parseDouble(m.group())));
            if (nums.size() >= 3) return new int[]{nums.get(0), nums.get(1), nums.get(2), 1};
            if (nums.size() == 2) return new int[]{nums.get(0), 0, nums.get(1), 0};
        }
        return null;
    }

    private static Integer num(JsonObject o, String key) {
        if (!o.has(key) || o.get(key).isJsonNull() || !o.get(key).isJsonPrimitive()) return null;
        try { return (int) Math.round(o.get(key).getAsDouble()); } catch (Exception ignored) { return null; }
    }

    private static String getString(JsonObject o, String key, String fallback) {
        if (o == null || !o.has(key) || o.get(key).isJsonNull()) return fallback;
        try { return o.get(key).getAsString(); } catch (Exception ignored) { return fallback; }
    }
}
