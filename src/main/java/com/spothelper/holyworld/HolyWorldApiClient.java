package com.spothelper.holyworld;

import com.spothelper.auth.AuthClient;
import com.spothelper.auth.AuthManager;
import com.spothelper.config.SpotHelperConfig;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * События, курс коина и голосования берутся с публичного API (https://api.holyworld.me, без авторизации).
 * Шахты и статус Telegram, как и раньше, — из локального приложения (адрес и порт в настройках).
 */
public class HolyWorldApiClient {
    public static final String PUBLIC_BASE = "https://api.holyworld.me";

    /** Сколько последних сделок запрашивать для курса коина. */
    private static final int COIN_TRADES_LIMIT = 20;
    /** Как часто обновлять список серверов (имена меняются редко). */
    private static final long SERVERS_TTL_MS = 10 * 60 * 1000L;

    public interface Callback {
        void onSuccess(HolyWorldData.Snapshot snapshot);
        void onError(String message);
    }

    private interface Task {
        HolyWorldData.Snapshot run(HolyWorldData.Snapshot current) throws Exception;
    }

    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "SpotHelper-HolyWorld-HTTP");
        t.setDaemon(true);
        return t;
    });
    private volatile HolyWorldData.Snapshot snapshot = HolyWorldData.Snapshot.empty();
    private volatile Future<?> activeRequest;
    private volatile long serversFetchedAt = 0L;
    private final AtomicInteger pending = new AtomicInteger();

    public HolyWorldData.Snapshot getSnapshot() {
        return snapshot;
    }

    /** Есть ли запросы в очереди или в работе. */
    public boolean isBusy() {
        return pending.get() > 0;
    }

    public void shutdown() {
        executor.shutdownNow();
    }

    // ================================================================== публичный API

    /** События (с подстановкой имён серверов). */
    public void refreshEvents(final Callback callback) {
        submit(current -> fetchEvents(current), callback);
    }

    /** Совместимость: у публичного API нет отдельного «принудительного» обновления. */
    public void refreshEventsNow(final Callback callback) {
        refreshEvents(callback);
    }

    public void refreshCoins(final Callback callback) {
        submit(current -> fetchCoins(current), callback);
    }

    public void refreshVotes(final Callback callback) {
        submit(current -> fetchVotes(current), callback);
    }

    /**
     * Всё сразу: состояние сервиса, серверы, события, курс, голосования.
     * Ошибка одной части не мешает остальным; итоговый статус — «ok», если прошёл хотя бы health.
     */
    public void refreshPublic(final Callback callback) {
        submit(current -> {
            HolyWorldData.Snapshot s = current;
            String firstError = null;
            boolean any = false;

            try {
                requestPublic("/v1/health");
                any = true;
            } catch (Exception e) {
                firstError = messageOf(e);
            }
            if (any) {
                try { s = fetchEvents(s); } catch (Exception e) { if (firstError == null) firstError = messageOf(e); }
                try { s = fetchCoins(s); } catch (Exception e) { if (firstError == null) firstError = messageOf(e); }
                try { s = fetchVotes(s); } catch (Exception e) { if (firstError == null) firstError = messageOf(e); }
            }
            return HolyWorldData.withPublicStatus(s, any, firstError);
        }, callback);
    }

    private HolyWorldData.Snapshot fetchEvents(HolyWorldData.Snapshot current) throws IOException {
        HolyWorldData.Snapshot s = ensureServers(current);
        s = HolyWorldData.parseEvents(requestPublic("/v1/events"), s);
        return HolyWorldData.withPublicStatus(s, true, null);
    }

    private HolyWorldData.Snapshot fetchCoins(HolyWorldData.Snapshot current) throws IOException {
        HolyWorldData.Snapshot s = HolyWorldData.parseCoins(
                requestPublic("/v1/coins-trades?limit=" + COIN_TRADES_LIMIT), current);
        return HolyWorldData.withPublicStatus(s, true, null);
    }

    private HolyWorldData.Snapshot fetchVotes(HolyWorldData.Snapshot current) throws IOException {
        HolyWorldData.Snapshot s = ensureServers(current);
        s = HolyWorldData.parseVotings(requestPublic("/v1/votings"), s);
        return HolyWorldData.withPublicStatus(s, true, null);
    }

    /** Имена серверов нужны, чтобы показывать «Анархия 502» вместо технического id. Сбой не критичен. */
    private HolyWorldData.Snapshot ensureServers(HolyWorldData.Snapshot current) {
        long now = System.currentTimeMillis();
        if (!current.serverNames.isEmpty() && now - serversFetchedAt < SERVERS_TTL_MS) return current;
        try {
            HolyWorldData.Snapshot s = HolyWorldData.parseServers(requestPublic("/v1/servers"), current);
            serversFetchedAt = now;
            return s;
        } catch (Exception ignored) {
            return current;
        }
    }

    // ================================================================== приложение: шахты, статус

    public void refreshMines(final Callback callback) {
        submit(current -> {
            HolyWorldData.Snapshot s = HolyWorldData.parseMines(requestApp("/mines/refresh").getAsJsonObject(), current);
            return fetchStatusSync(s);
        }, callback);
    }

    public void refreshMinesCached(final Callback callback) {
        submit(current -> {
            HolyWorldData.Snapshot s = HolyWorldData.parseMines(requestApp("/mines").getAsJsonObject(), current);
            return fetchStatusSync(s);
        }, callback);
    }

    public void refreshStatus(final Callback callback) {
        submit(current -> HolyWorldData.parseStatus(requestApp("/status").getAsJsonObject(), current), callback);
    }

    private HolyWorldData.Snapshot fetchStatusSync(HolyWorldData.Snapshot current) {
        try {
            return HolyWorldData.parseStatus(requestApp("/status").getAsJsonObject(), current);
        } catch (Exception ignored) {
            return current;
        }
    }

    // ================================================================== внутреннее

    private void submit(final Task task, final Callback callback) {
        pending.incrementAndGet();
        activeRequest = executor.submit(() -> {
            try {
                HolyWorldData.Snapshot data = task.run(snapshot);
                snapshot = data;
                if (callback != null) callback.onSuccess(data);
            } catch (Exception e) {
                if (callback != null) callback.onError(messageOf(e));
            } finally {
                pending.decrementAndGet();
            }
        });
    }

    private static String messageOf(Exception e) {
        String message = e.getMessage();
        if (message == null || message.trim().isEmpty()) message = e.getClass().getSimpleName();
        return message;
    }

    private JsonElement requestPublic(String path) throws IOException {
        return request(PUBLIC_BASE + path, 4000, 8000, false);
    }

    /** Шахты и статус: с нашего сервера (тот же адрес, что и у авторизации), с токеном игрока. */
    private JsonElement requestApp(String path) throws IOException {
        String base = AuthClient.apiBase(SpotHelperConfig.INSTANCE.authApiUrl);
        return request(base + path, 4000, 8000, true);
    }

    private JsonElement request(String address, int connectTimeout, int readTimeout, boolean withAuth) throws IOException {
        URL url = new URL(address);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(connectTimeout);
        connection.setReadTimeout(readTimeout);
        connection.setUseCaches(false);
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("User-Agent", "SpotHelper");
        if (withAuth) {
            connection.setRequestProperty("X-Login", AuthManager.login());
            connection.setRequestProperty("X-Token", AuthManager.token());
        }
        try {
            int code = connection.getResponseCode();
            InputStream input = code >= 200 && code < 400 ? connection.getInputStream() : connection.getErrorStream();
            String body = readAll(input);
            if (code < 200 || code >= 300) {
                throw new IOException("HTTP " + code + (body.isEmpty() ? "" : ": " + body));
            }
            return new JsonParser().parse(body);
        } finally {
            connection.disconnect();
        }
    }

    private static String readAll(InputStream input) throws IOException {
        if (input == null) return "";
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
        }
        return sb.toString();
    }
}
