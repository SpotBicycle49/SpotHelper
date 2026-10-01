package com.spothelper.client;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.spothelper.SpotHelperConfig;

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

public class HolyWorldApiClient {
    public interface Callback {
        void onSuccess(HolyWorldData.Snapshot snapshot);
        void onError(String message);
    }

    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "SpotHelper-HolyWorld-HTTP");
        t.setDaemon(true);
        return t;
    });
    private final Gson gson = new Gson();
    private volatile HolyWorldData.Snapshot snapshot = HolyWorldData.Snapshot.empty();
    private volatile Future<?> activeRequest;

    public HolyWorldData.Snapshot getSnapshot() {
        return snapshot;
    }

    public void shutdown() {
        executor.shutdownNow();
    }

    public void refreshEvents(final Callback callback) {
        getAsync("/events", (json) -> {
            snapshot = HolyWorldData.parseEvents(json, snapshot);
            return snapshot;
        }, callback);
    }

    public void refreshEventsNow(final Callback callback) {
        getAsync("/events/refresh", (json) -> {
            snapshot = HolyWorldData.parseEvents(json, snapshot);
            return snapshot;
        }, callback);
    }

    public void refreshMines(final Callback callback) {
        getAsync("/mines/refresh", (json) -> {
            snapshot = HolyWorldData.parseMines(json, snapshot);
            snapshot = fetchStatusSync(snapshot);
            return snapshot;
        }, callback);
    }

    public void refreshMinesCached(final Callback callback) {
        getAsync("/mines", (json) -> {
            snapshot = HolyWorldData.parseMines(json, snapshot);
            snapshot = fetchStatusSync(snapshot);
            return snapshot;
        }, callback);
    }

    public void refreshStatus(final Callback callback) {
        getAsync("/status", (json) -> {
            snapshot = HolyWorldData.parseStatus(json, snapshot);
            return snapshot;
        }, callback);
    }

    private interface Parser {
        HolyWorldData.Snapshot parse(JsonObject json) throws Exception;
    }

    private void getAsync(final String path, final Parser parser, final Callback callback) {
        activeRequest = executor.submit(() -> {
            try {
                JsonObject json = request(path);
                HolyWorldData.Snapshot data = parser.parse(json);
                snapshot = data;
                if (callback != null) callback.onSuccess(data);
            } catch (Exception e) {
                String message = e.getMessage();
                if (message == null || message.trim().isEmpty()) message = e.getClass().getSimpleName();
                if (callback != null) callback.onError(message);
            }
        });
    }

    private HolyWorldData.Snapshot fetchStatusSync(HolyWorldData.Snapshot current) {
        try {
            return HolyWorldData.parseStatus(request("/status"), current);
        } catch (Exception ignored) {
            return current;
        }
    }

    private JsonObject request(String path) throws IOException {
        String base = SpotHelperConfig.INSTANCE.getHolyWorldBaseUrl();
        URL url = new URL(base + path);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(2500);
        connection.setReadTimeout(8000);
        connection.setUseCaches(false);
        connection.setRequestProperty("Accept", "application/json");
        try {
            int code = connection.getResponseCode();
            InputStream input = code >= 200 && code < 400 ? connection.getInputStream() : connection.getErrorStream();
            String body = readAll(input);
            if (code < 200 || code >= 300) {
                throw new IOException("HTTP " + code + (body.isEmpty() ? "" : ": " + body));
            }
            JsonObject json = new JsonParser().parse(body).getAsJsonObject();
            return json;
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
