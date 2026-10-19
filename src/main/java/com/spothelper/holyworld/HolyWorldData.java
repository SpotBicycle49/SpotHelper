package com.spothelper.holyworld;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

    /** Вариант ответа в голосовании. */
    public static final class VoteOptionRow {
        public final String name;
        public final int votes;

        public VoteOptionRow(String name, int votes) {
            this.name = name;
            this.votes = votes;
        }
    }

    /** Активное голосование (публичный API /v1/votings). */
    public static final class VoteRow {
        public final String serverId;
        public final String serverName;
        public final String title;
        public final List<VoteOptionRow> options;
        public final int seconds;
        public final boolean hasTime;
        public final long anchorMillis;

        public VoteRow(String serverId, String serverName, String title, List<VoteOptionRow> options,
                       int seconds, boolean hasTime, long anchorMillis) {
            this.serverId = serverId;
            this.serverName = serverName;
            this.title = title;
            this.options = Collections.unmodifiableList(new ArrayList<VoteOptionRow>(options));
            this.seconds = seconds;
            this.hasTime = hasTime;
            this.anchorMillis = anchorMillis;
        }

        public int liveSeconds() {
            if (!hasTime) return 0;
            long delta = System.currentTimeMillis() - anchorMillis;
            return (int) Math.max(0L, (long) seconds - delta / 1000L);
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

        // ---- публичный API (api.holyworld.me) ----
        /** Последний запрос к публичному API прошёл успешно. */
        public final boolean publicOk;
        public final String publicError;
        /** Курс коина по последней сделке; NaN — данных нет. */
        public final double coinRate;
        /** 1 — вырос, -1 — упал, 0 — без изменений относительно прошлого курса. */
        public final int coinTrend;
        public final List<VoteRow> votes;
        /** id сервера -> отображаемое имя (/v1/servers). */
        public final Map<String, String> serverNames;

        public Snapshot(boolean ok, String updatedAt, List<EventRow> events,
                        List<MineRow> overworld, List<MineRow> nether, List<MineRow> end,
                        boolean telegramAuthorized, boolean httpConnected, String error) {
            this(ok, updatedAt, events, overworld, nether, end, telegramAuthorized, httpConnected, error,
                    false, null, Double.NaN, 0, Collections.<VoteRow>emptyList(), Collections.<String, String>emptyMap());
        }

        public Snapshot(boolean ok, String updatedAt, List<EventRow> events,
                        List<MineRow> overworld, List<MineRow> nether, List<MineRow> end,
                        boolean telegramAuthorized, boolean httpConnected, String error,
                        boolean publicOk, String publicError, double coinRate, int coinTrend,
                        List<VoteRow> votes, Map<String, String> serverNames) {
            this.ok = ok;
            this.updatedAt = updatedAt;
            this.events = Collections.unmodifiableList(new ArrayList<EventRow>(events));
            this.overworld = Collections.unmodifiableList(new ArrayList<MineRow>(overworld));
            this.nether = Collections.unmodifiableList(new ArrayList<MineRow>(nether));
            this.end = Collections.unmodifiableList(new ArrayList<MineRow>(end));
            this.telegramAuthorized = telegramAuthorized;
            this.httpConnected = httpConnected;
            this.error = error;
            this.publicOk = publicOk;
            this.publicError = publicError;
            this.coinRate = coinRate;
            this.coinTrend = coinTrend;
            this.votes = Collections.unmodifiableList(new ArrayList<VoteRow>(votes));
            this.serverNames = Collections.unmodifiableMap(new LinkedHashMap<String, String>(serverNames));
        }

        public static Snapshot empty() {
            return new Snapshot(true, null,
                    Collections.<EventRow>emptyList(),
                    Collections.<MineRow>emptyList(),
                    Collections.<MineRow>emptyList(),
                    Collections.<MineRow>emptyList(),
                    false, false, null);
        }

        Builder edit() {
            return new Builder(this);
        }
    }

    /** Копия снимка с изменением отдельных частей (снимок неизменяемый). */
    static final class Builder {
        boolean ok, telegramAuthorized, httpConnected, publicOk;
        String updatedAt, error, publicError;
        List<EventRow> events;
        List<MineRow> overworld, nether, end;
        double coinRate;
        int coinTrend;
        List<VoteRow> votes;
        Map<String, String> serverNames;

        Builder(Snapshot s) {
            ok = s.ok;
            updatedAt = s.updatedAt;
            events = s.events;
            overworld = s.overworld;
            nether = s.nether;
            end = s.end;
            telegramAuthorized = s.telegramAuthorized;
            httpConnected = s.httpConnected;
            error = s.error;
            publicOk = s.publicOk;
            publicError = s.publicError;
            coinRate = s.coinRate;
            coinTrend = s.coinTrend;
            votes = s.votes;
            serverNames = s.serverNames;
        }

        Snapshot build() {
            return new Snapshot(ok, updatedAt, events, overworld, nether, end,
                    telegramAuthorized, httpConnected, error,
                    publicOk, publicError, coinRate, coinTrend, votes, serverNames);
        }
    }

    private HolyWorldData() {
    }

    // ================================================================== публичный API

    private static final String[] EVENT_NAME_KEYS = {"display_name", "displayName", "name", "title", "event", "event_name", "type", "id"};
    private static final String[] EVENT_ID_KEYS = {"id", "event_id", "key", "type", "name"};
    private static final String[] EVENT_RARE_KEYS = {"rare", "rarity", "category", "world", "dimension"};
    private static final String[] SERVER_KEYS = {"server_id", "serverId", "server"};
    private static final String[] SERVER_NAME_KEYS = {"server_name", "serverName", "server_display_name"};
    private static final String[] SEC_KEYS = {"seconds", "starts_in", "start_in", "in_seconds", "eta_seconds", "eta",
            "remaining", "remaining_seconds", "time_left", "seconds_left", "left"};
    private static final String[] TS_KEYS = {"due_at", "starts_at", "start_at", "start_time", "startAt", "startsAt",
            "spawn_at", "time", "timestamp", "datetime", "date", "at", "next",
            "ends_at", "end_at", "expires_at", "end_time", "endAt", "endsAt", "finish_at"};
    private static final String[] VOTE_TITLE_KEYS = {"title", "name", "question", "topic", "display_name", "description", "id"};
    private static final String[] VOTE_OPTION_KEYS = {"candidates", "options", "variants", "choices", "answers", "items", "players"};
    private static final String[] VOTE_NAME_KEYS = {"name", "title", "nick", "nickname", "player", "label", "text", "candidate", "id"};
    private static final String[] VOTE_COUNT_KEYS = {"votes", "count", "votes_count", "voteCount", "score", "value", "total"};
    private static final String[] VOTE_SEC_KEYS = {"remaining", "remaining_seconds", "time_left", "seconds_left", "ends_in", "left", "seconds"};
    private static final String[] VOTE_TS_KEYS = {"ends_at", "end_at", "expires_at", "end_time", "endAt", "endsAt",
            "finish_at", "deadline", "due_at", "until", "end"};

    /** GET /v1/servers: id -> отображаемое имя. Понимает {id: "имя"}, {id: {name}} и [{id, name}]. */
    public static Snapshot parseServers(JsonElement root, Snapshot current) {
        Map<String, String> names = new LinkedHashMap<String, String>();
        collectServers(root, names, 0);
        if (names.isEmpty()) return current;
        Builder b = current.edit();
        b.serverNames = names;
        return b.build();
    }

    private static void collectServers(JsonElement el, Map<String, String> out, int depth) {
        if (el == null || el.isJsonNull() || depth > 3) return;
        if (el.isJsonArray()) {
            for (JsonElement e : el.getAsJsonArray()) {
                if (e.isJsonObject()) {
                    JsonObject o = e.getAsJsonObject();
                    String id = firstString(o, "id", "server_id", "serverId", "key", "slug");
                    String name = firstString(o, "display_name", "displayName", "name", "title", "label");
                    if (id != null) out.put(id, name != null ? name : id);
                } else if (e.isJsonPrimitive()) {
                    String id = e.getAsString();
                    out.put(id, id);
                }
            }
            return;
        }
        if (!el.isJsonObject()) return;
        JsonObject o = el.getAsJsonObject();
        for (String wrap : new String[]{"servers", "data", "items"}) {
            if (o.has(wrap) && !o.get(wrap).isJsonPrimitive()) {
                collectServers(o.get(wrap), out, depth + 1);
                if (!out.isEmpty()) return;
            }
        }
        for (Map.Entry<String, JsonElement> e : o.entrySet()) {
            JsonElement v = e.getValue();
            if (v == null || v.isJsonNull()) continue;
            if (v.isJsonPrimitive()) {
                out.put(e.getKey(), v.getAsString());
            } else if (v.isJsonObject()) {
                String name = firstString(v.getAsJsonObject(), "display_name", "displayName", "name", "title", "label");
                out.put(e.getKey(), name != null ? name : e.getKey());
            }
        }
    }

    /** GET /v1/events: массив (с ?server=) либо объект {serverId: [события]}. */
    public static Snapshot parseEvents(JsonElement root, Snapshot current) {
        List<EventRow> rows = new ArrayList<EventRow>();
        forEachPerServer(root, current.serverNames, (serverId, o) -> {
            long anchor = System.currentTimeMillis();
            int[] time = findTime(o, SEC_KEYS, TS_KEYS, anchor);

            String sid = serverId != null ? serverId : firstString(o, SERVER_KEYS);
            String sname = firstString(o, SERVER_NAME_KEYS);
            if (sname == null && sid != null) sname = current.serverNames.get(sid);
            if (sname == null) sname = sid == null ? "" : sid;

            String name = firstString(o, EVENT_NAME_KEYS);
            String id = firstString(o, EVENT_ID_KEYS);
            int[] coords = findCoords(o, 0, false);
            rows.add(new EventRow(
                    sid == null ? "" : sid, sname,
                    name != null ? name : "Событие",
                    firstString(o, EVENT_RARE_KEYS) == null ? "" : firstString(o, EVENT_RARE_KEYS),
                    id == null ? "" : id,
                    time == null ? 0 : time[0], time != null, anchor,
                    coords != null, coords != null && coords[3] == 1,
                    coords == null ? 0 : coords[0], coords == null ? 0 : coords[1], coords == null ? 0 : coords[2],
                    keysOf(o)));
        });
        Builder b = current.edit();
        b.events = rows;
        return b.build();
    }

    /** GET /v1/votings: массив либо объект {serverId: [голосования]}. */
    public static Snapshot parseVotings(JsonElement root, Snapshot current) {
        List<VoteRow> rows = new ArrayList<VoteRow>();
        forEachPerServer(root, current.serverNames, (serverId, o) -> {
            long anchor = System.currentTimeMillis();
            int[] time = findTime(o, VOTE_SEC_KEYS, VOTE_TS_KEYS, anchor);

            String sid = serverId != null ? serverId : firstString(o, SERVER_KEYS);
            String sname = firstString(o, SERVER_NAME_KEYS);
            if (sname == null && sid != null) sname = current.serverNames.get(sid);
            if (sname == null) sname = sid == null ? "—" : sid;

            String title = firstString(o, VOTE_TITLE_KEYS);
            rows.add(new VoteRow(sid == null ? "" : sid, sname, title != null ? title : "Голосование",
                    parseOptions(o), time == null ? 0 : time[0], time != null, anchor));
        });
        Builder b = current.edit();
        b.votes = rows;
        return b.build();
    }

    private static List<VoteOptionRow> parseOptions(JsonObject o) {
        List<VoteOptionRow> out = new ArrayList<VoteOptionRow>();
        for (String key : VOTE_OPTION_KEYS) {
            if (!o.has(key) || o.get(key).isJsonNull()) continue;
            JsonElement el = o.get(key);
            if (el.isJsonArray()) {
                for (JsonElement e : el.getAsJsonArray()) {
                    if (e.isJsonObject()) {
                        JsonObject c = e.getAsJsonObject();
                        String name = firstString(c, VOTE_NAME_KEYS);
                        Integer votes = firstInt(c, VOTE_COUNT_KEYS);
                        out.add(new VoteOptionRow(name != null ? name : "—", votes == null ? 0 : Math.max(0, votes)));
                    } else if (e.isJsonPrimitive()) {
                        out.add(new VoteOptionRow(e.getAsString(), 0));
                    }
                }
            } else if (el.isJsonObject()) {
                for (Map.Entry<String, JsonElement> e : el.getAsJsonObject().entrySet()) {
                    JsonElement v = e.getValue();
                    int votes = 0;
                    if (v.isJsonPrimitive()) {
                        try { votes = (int) Math.round(v.getAsDouble()); } catch (Exception ignored) { }
                    } else if (v.isJsonObject()) {
                        Integer n = firstInt(v.getAsJsonObject(), VOTE_COUNT_KEYS);
                        votes = n == null ? 0 : n;
                    }
                    out.add(new VoteOptionRow(e.getKey(), Math.max(0, votes)));
                }
            }
            if (!out.isEmpty()) break;
        }
        return out;
    }

    /** GET /v1/coins-trades: [{rate, datetime}]. Берёт последнюю сделку и сравнивает с предыдущим курсом. */
    public static Snapshot parseCoins(JsonElement root, Snapshot current) {
        if (root == null || !root.isJsonArray()) return current;
        List<double[]> trades = new ArrayList<double[]>();
        for (JsonElement e : root.getAsJsonArray()) {
            if (!e.isJsonObject()) continue;
            JsonObject o = e.getAsJsonObject();
            try {
                double rate = o.get("rate").getAsDouble();
                double t = o.has("datetime") && !o.get("datetime").isJsonNull() ? o.get("datetime").getAsDouble() : 0;
                trades.add(new double[]{t, rate});
            } catch (Exception ignored) { }
        }
        if (trades.isEmpty()) return current;
        trades.sort((x, y) -> Double.compare(x[0], y[0]));
        double last = trades.get(trades.size() - 1)[1];
        int trend = current.coinTrend;
        for (int i = trades.size() - 2; i >= 0; i--) {
            double prev = trades.get(i)[1];
            if (Math.abs(prev - last) > 1e-9) {
                trend = last > prev ? 1 : -1;
                break;
            }
        }
        // в самой выборке одинаковый курс — сравниваем с тем, что было в прошлом снимке
        if (trend == current.coinTrend && !Double.isNaN(current.coinRate)
                && Math.abs(current.coinRate - last) > 1e-9) {
            trend = last > current.coinRate ? 1 : -1;
        }
        Builder b = current.edit();
        b.coinRate = last;
        b.coinTrend = trend;
        return b.build();
    }

    /** Результат опроса публичного API. */
    public static Snapshot withPublicStatus(Snapshot current, boolean ok, String error) {
        Builder b = current.edit();
        b.publicOk = ok;
        b.publicError = ok ? null : error;
        return b.build();
    }

    // ---- общие помощники разбора

    private interface EntryHandler {
        void accept(String serverId, JsonObject o);
    }

    /**
     * Обходит ответ вида [объекты] или {serverId: [объекты]} (а также {serverId: {events:[..]}}).
     */
    private static void forEachPerServer(JsonElement root, Map<String, String> names, EntryHandler h) {
        if (root == null || root.isJsonNull()) return;
        if (root.isJsonArray()) {
            for (JsonElement e : root.getAsJsonArray()) {
                if (e.isJsonObject()) h.accept(null, e.getAsJsonObject());
            }
            return;
        }
        if (!root.isJsonObject()) return;
        JsonObject o = root.getAsJsonObject();
        JsonArray wrapped = firstArray(o);
        if (wrapped != null) {
            for (JsonElement item : wrapped) {
                if (item.isJsonObject()) h.accept(null, item.getAsJsonObject());
            }
            return;
        }
        for (Map.Entry<String, JsonElement> e : o.entrySet()) {
            JsonElement v = e.getValue();
            if (v == null) continue;
            if (v.isJsonObject() && !hasArray(v.getAsJsonObject())) {
                continue;
            }
            JsonArray arr = null;
            if (v.isJsonArray()) {
                arr = v.getAsJsonArray();
            } else if (v.isJsonObject()) {
                arr = firstArray(v.getAsJsonObject());
            }
            if (arr == null) continue;
            for (JsonElement item : arr) {
                if (item.isJsonObject()) h.accept(e.getKey(), item.getAsJsonObject());
            }
        }
    }

    private static boolean hasArray(JsonObject o) {
        return firstArray(o) != null;
    }

    private static JsonArray firstArray(JsonObject o) {
        for (String key : new String[]{"events", "votings", "items", "data", "list"}) {
            if (o.has(key) && o.get(key).isJsonArray()) return o.getAsJsonArray(key);
        }
        return null;
    }

    private static String firstString(JsonObject o, String... keys) {
        for (String key : keys) {
            if (o.has(key) && !o.get(key).isJsonNull() && o.get(key).isJsonPrimitive()) {
                try {
                    String v = o.get(key).getAsString();
                    if (v != null && !v.trim().isEmpty()) return v.trim();
                } catch (Exception ignored) { }
            }
        }
        return null;
    }

    private static Integer firstInt(JsonObject o, String... keys) {
        for (String key : keys) {
            if (o.has(key) && !o.get(key).isJsonNull() && o.get(key).isJsonPrimitive()) {
                try { return (int) Math.round(o.get(key).getAsDouble()); } catch (Exception ignored) { }
            }
        }
        return null;
    }

    /** {секунд до события} из прямых полей-секунд либо из метки времени. null — времени нет. */
    private static int[] findTime(JsonObject o, String[] secKeys, String[] tsKeys, long now) {
        for (String key : secKeys) {
            if (o.has(key) && !o.get(key).isJsonNull() && o.get(key).isJsonPrimitive()) {
                try { return new int[]{(int) Math.max(0, Math.round(o.get(key).getAsDouble()))}; } catch (Exception ignored) { }
            }
        }
        for (String key : tsKeys) {
            if (!o.has(key) || o.get(key).isJsonNull() || !o.get(key).isJsonPrimitive()) continue;
            long ts = parseMillis(o.get(key));
            if (ts > 0) return new int[]{(int) Math.max(0L, (ts - now) / 1000L)};
        }
        return null;
    }

    /** Метка времени: число (секунды или миллисекунды) либо ISO-8601. 0 — не удалось. */
    static long parseMillis(JsonElement el) {
        try {
            if (el.getAsJsonPrimitive().isNumber()) {
                double d = el.getAsDouble();
                return d > 1e11 ? (long) d : (long) (d * 1000.0);
            }
            String s = el.getAsString().trim();
            if (s.matches("\\d{9,}")) {
                double d = Double.parseDouble(s);
                return d > 1e11 ? (long) d : (long) (d * 1000.0);
            }
            try { return java.time.Instant.parse(s).toEpochMilli(); } catch (Exception ignored) { }
            return java.time.OffsetDateTime.parse(s).toInstant().toEpochMilli();
        } catch (Exception e) {
            return 0L;
        }
    }

    // ================================================================== приложение (шахты, статус)

    public static Snapshot parseMines(JsonObject root, Snapshot current) {
        if (root == null) return current;
        Builder b = current.edit();
        b.ok = root.has("ok") ? root.get("ok").getAsBoolean() : current.ok;
        b.updatedAt = getString(root, "updated_at", current.updatedAt);
        b.overworld = parseWorld(root, "overworld");
        b.nether = parseWorld(root, "nether");
        b.end = parseWorld(root, "end");
        b.httpConnected = true;
        b.error = getString(root, "error", null);
        return b.build();
    }

    public static Snapshot parseStatus(JsonObject root, Snapshot current) {
        if (root == null) return current;
        boolean authorized = false;
        if (root.has("telegram") && root.get("telegram").isJsonObject()) {
            JsonObject tg = root.getAsJsonObject("telegram");
            authorized = tg.has("authorized") && tg.get("authorized").getAsBoolean();
        }
        Builder b = current.edit();
        b.ok = !root.has("ok") || root.get("ok").getAsBoolean();
        b.telegramAuthorized = authorized;
        b.httpConnected = true;
        b.error = getString(root, "error", null);
        return b.build();
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
