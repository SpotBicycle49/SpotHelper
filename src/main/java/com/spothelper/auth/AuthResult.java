package com.spothelper.auth;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Ответ сервера. Паролей тут нет; expiresAt — Unix-секунды. */
public final class AuthResult {

    public enum Status {
        /** Доступ есть. */
        AUTHORIZED,
        /** Аккаунт/заявка ждёт подтверждения администратором. */
        PENDING,
        /** Сервер ответил отказом. */
        DENIED,
        /** Сеть, таймаут, 5xx, мусор вместо JSON. */
        UNAVAILABLE
    }

    public final Status status;
    public final boolean authorized;
    public final String username;
    public final String tier;
    public final long expiresAt;
    public final List<String> features;
    public final String reason;
    /** Токен сессии (только в ответе на login). */
    public final String token;
    /** Сервер просит войти заново (токен недействителен). */
    public final boolean relogin;

    private AuthResult(Status status, boolean authorized, String username, String tier, long expiresAt,
                       List<String> features, String reason, String token, boolean relogin) {
        this.status = status;
        this.authorized = authorized;
        this.username = username == null ? "" : username;
        this.tier = tier == null ? "" : tier;
        this.expiresAt = expiresAt;
        this.features = features == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(features));
        this.reason = reason == null ? "" : reason;
        this.token = token == null ? "" : token;
        this.relogin = relogin;
    }

    public static AuthResult denied(String reason) {
        return new AuthResult(Status.DENIED, false, "", "", 0L, null, reason, "", false);
    }

    public static AuthResult unavailable(String reason) {
        return new AuthResult(Status.UNAVAILABLE, false, "", "", 0L, null, reason, "", false);
    }

    /** Восстановление из кеша (токен хранится отдельно, в AuthManager). */
    public static AuthResult fromCache(String login, String tier, long expiresAt, List<String> features) {
        return new AuthResult(Status.AUTHORIZED, true, login, tier, expiresAt, features, "cache", "", false);
    }

    /** Разбор JSON. Не бросает исключений: мусор -> UNAVAILABLE. */
    public static AuthResult fromJson(String json) {
        try {
            JsonElement root = JsonParser.parseString(json);
            if (!root.isJsonObject()) return unavailable("Некорректный ответ API");
            JsonObject o = root.getAsJsonObject();

            boolean authorized = getBool(o, "authorized");
            boolean pending = getBool(o, "pending") || "pending".equalsIgnoreCase(getString(o, "status"));
            boolean okFlag = o.has("ok") && getBool(o, "ok"); // ответ на register
            String login = getString(o, "login");
            String tier = getString(o, "tier");
            String reason = getString(o, "reason");
            String token = getString(o, "token");
            boolean relogin = getBool(o, "relogin");
            long expiresAt = normalizeSeconds(getLong(o, "expiresAt"));

            if (!authorized && (pending || okFlag)) {
                return new AuthResult(Status.PENDING, false, login, "", 0L, null,
                        reason.isEmpty() ? "Ожидает подтверждения администратором" : reason, "", false);
            }
            if (!authorized) {
                return new AuthResult(Status.DENIED, false, login, "", 0L, null,
                        reason.isEmpty() ? "Отказано" : reason, "", relogin);
            }

            List<String> features = new ArrayList<>();
            if (o.has("features") && o.get("features").isJsonArray()) {
                JsonArray arr = o.getAsJsonArray("features");
                for (JsonElement e : arr) {
                    if (e.isJsonPrimitive()) {
                        String f = e.getAsString().trim().toLowerCase(Locale.ROOT);
                        if (!f.isEmpty()) features.add(f);
                    }
                }
            }
            return new AuthResult(Status.AUTHORIZED, true, login, tier, expiresAt, features, reason, token, false);
        } catch (Exception e) {
            return unavailable("Некорректный ответ API");
        }
    }

    // ---------------------------------------------------------------- логика

    public static long nowSeconds() {
        return System.currentTimeMillis() / 1000L;
    }

    /** authorized=true и expiresAt строго в будущем. */
    public boolean isValidNow() {
        return authorized && expiresAt > nowSeconds();
    }

    public long secondsLeft() {
        return expiresAt - nowSeconds();
    }

    /** Невалидный/истёкший результат -> всегда false. "*" = все функции. */
    public boolean hasFeature(String feature) {
        if (!isValidNow() || feature == null) return false;
        String f = feature.trim().toLowerCase(Locale.ROOT);
        return features.contains("*") || features.contains(f);
    }

    // ---------------------------------------------------------------- утилиты

    private static long normalizeSeconds(long v) {
        return v > 100_000_000_000L ? v / 1000L : v;
    }

    private static String getString(JsonObject o, String key) {
        JsonElement e = o.get(key);
        if (e == null || e.isJsonNull() || !e.isJsonPrimitive()) return "";
        return e.getAsString();
    }

    private static boolean getBool(JsonObject o, String key) {
        JsonElement e = o.get(key);
        if (e == null || e.isJsonNull() || !e.isJsonPrimitive()) return false;
        try {
            return e.getAsBoolean();
        } catch (Exception ex) {
            return false;
        }
    }

    private static long getLong(JsonObject o, String key) {
        JsonElement e = o.get(key);
        if (e == null || e.isJsonNull() || !e.isJsonPrimitive()) return 0L;
        try {
            return e.getAsLong();
        } catch (Exception ex) {
            try {
                return (long) e.getAsDouble();
            } catch (Exception ex2) {
                return 0L;
            }
        }
    }
}