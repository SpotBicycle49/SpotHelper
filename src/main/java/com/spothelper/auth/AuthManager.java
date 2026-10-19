package com.spothelper.auth;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.spothelper.config.SpotHelperConfig;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.session.Session;
import net.minecraft.text.Text;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Менеджер доступа по логину/паролю.
 *  - Пароль уходит только в register/login и нигде не хранится.
 *  - После входа хранится токен сессии (config/spothelper_auth.json).
 *  - Проверка сессии: до истечения > authCacheHours запросов нет; меньше — фоном
 *    раз в authPollSeconds; истёк — функции выключены, пока сервер не продлит.
 * Всё состояние меняется только в клиентском потоке.
 */
public final class AuthManager {

    /**
     * ЗАМОРОЗКА авторизации. true — система полностью выключена, но весь код на месте:
     * доступ открыт всем, функции доступны, меню и окно входа не показываются, к API авторизации
     * нет ни одного запроса, кеш сессии не читается и не пишется, в чат ничего не пишется.
     * Чтобы вернуть авторизацию, поставить false (больше ничего менять не нужно).
     */
    public static final boolean FROZEN = true;

    public enum State { CHECKING, AUTHORIZED, PENDING, DENIED, UNAVAILABLE, LOGGED_OUT }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CACHE_PATH =
            FabricLoader.getInstance().getConfigDir().resolve("spothelper_auth.json");
    private static final AuthClient CLIENT = new AuthClient(() -> SpotHelperConfig.INSTANCE.authApiUrl);

    public static final String MSG_DENIED_CHAT = "§cSpotHelper: доступ запрещён. Обратитесь к администратору.";
    public static final String MSG_LOGIN_CHAT = "§cSpotHelper: нужен вход в аккаунт. Откройте меню SpotHelper.";

    private static volatile AuthResult current = AuthResult.denied("");
    private static volatile State lastFailure = State.CHECKING;
    private static volatile String failReason = "";
    private static volatile boolean inFlight = false;

    /** Сервер выключил авторизацию в панели: доступ открыт всем. */
    private static volatile boolean serverOpen = false;
    /** В панели выбрано «API лежит»: доступа нет ни у кого (сильнее всего остального). */
    private static volatile boolean serverLocked = false;
    /** API не отвечает (сеть, таймаут, 5xx): доступ открыт, пока он не вернётся. */
    private static volatile boolean apiUnreachable = false;
    private static volatile boolean cfgInFlight = false;
    /** Сообщение игрокам от администратора (например, о переезде сервера). */
    private static volatile String notice = "";
    private static long lastCfgMs = 0L;

    private static volatile String login = "";
    private static volatile String token = "";

    private static long lastAttemptMs = 0L;
    private static boolean wasInWorld = false;
    private static boolean notified = false;

    private AuthManager() {}

    /** Файл кеша: логин, токен сессии и срок. Пароля тут нет. */
    private static final class CacheFile {
        String login = "";
        String token = "";
        String tier = "";
        long expiresAt;
        List<String> features = new ArrayList<>();
    }

    // ------------------------------------------------------------------ API

    public static void init() {
        if (FROZEN) return;
        loadCache();
        mirrorToConfig();
    }

    /** Каждый END_CLIENT_TICK (работает и на TitleScreen). */
    public static void tick(MinecraftClient mc) {
        if (FROZEN) return;
        SpotHelperConfig cfg = SpotHelperConfig.INSTANCE;
        if (!cfg.authRequired) return;

        long now = System.currentTimeMillis();
        long pollMs = Math.max(10, cfg.authPollSeconds) * 1000L;

        // Включена ли авторизация на сервере (работает и без входа).
        if (!cfgInFlight && (lastCfgMs == 0L || now - lastCfgMs >= pollMs)) {
            requestConfig();
        }

        // Проверка сессии: всегда раз в authPollSeconds, чтобы отзыв доступа работал сразу.
        if (!token.isEmpty() && !inFlight && (lastAttemptMs == 0L || now - lastAttemptMs >= pollMs)) {
            requestCheck();
        }

        boolean inWorld = mc.player != null;
        if (inWorld && !wasInWorld) notified = false;
        wasInWorld = inWorld;

        if (isAuthorized()) {
            notified = false;
        } else if (inWorld && !notified && state() != State.CHECKING) {
            mc.player.sendMessage(Text.literal(
                    state() == State.LOGGED_OUT ? MSG_LOGIN_CHAT : MSG_DENIED_CHAT), false);
            notified = true;
        }
    }

    /**
     * Правила доступа:
     *  1) authRequired=false в конфиге — режим разработки, всё открыто;
     *  2) в панели «API лежит» — доступа нет ни у кого;
     *  3) API не отвечает или авторизация выключена в панели — доступ открыт;
     *  4) иначе — только действующая сессия.
     */
    public static boolean isAuthorized() {
        if (FROZEN) return true;
        if (!SpotHelperConfig.INSTANCE.authRequired) return true;
        if (serverLocked) return false;
        if (serverOpen || apiUnreachable) return true;
        return current.isValidNow();
    }

    public static boolean hasFeature(String feature) {
        if (FROZEN) return true;
        if (!SpotHelperConfig.INSTANCE.authRequired) return true;
        if (serverLocked) return false;
        if (serverOpen || apiUnreachable) return true;
        return current.hasFeature(feature);
    }

    /** Любой запрос, не получивший ответа от API, означает «API не отвечает». */
    private static void markReachable(AuthResult r) {
        apiUnreachable = r.status == AuthResult.Status.UNAVAILABLE;
    }

    /** Вход по логину и паролю. Пароль нигде не сохраняется. Future завершается в клиентском потоке. */
    public static CompletableFuture<AuthResult> login(String l, String password) {
        return submit(true, l, password);
    }

    /** Заявка на доступ: аккаунт появится в панели, срок выдаёт администратор. */
    public static CompletableFuture<AuthResult> register(String l, String password) {
        return submit(false, l, password);
    }

    /** Выход: токен и кеш удаляются. */
    public static void logout() {
        token = "";
        current = AuthResult.denied("");
        lastFailure = State.CHECKING;
        failReason = "";
        notified = false;
        lastAttemptMs = 0L;
        deleteCache();
        mirrorToConfig();
    }

    public static void invalidate() {
        logout();
    }

    /** Принудительная проверка сессии прямо сейчас (кнопка «Проверить снова»). */
    public static void refresh() {
        if (FROZEN) return;
        requestConfig();
        if (inFlight || token.isEmpty()) return;
        requestCheck();
    }

    /**
     * Спрашивает у сервера настройки: включена ли авторизация, нет ли переезда на новый адрес,
     * есть ли сообщение игрокам. При недоступности сервера ничего не меняется.
     */
    private static void requestConfig() {
        if (FROZEN || cfgInFlight) return;
        cfgInFlight = true;
        lastCfgMs = System.currentTimeMillis();
        final MinecraftClient mc = MinecraftClient.getInstance();
        CLIENT.fetchServerConfig().whenComplete((sc, ex) -> mc.execute(() -> {
            if (ex != null || (sc != null && sc.unreachable)) {
                apiUnreachable = true; // API не ответил — доступ открыт
                cfgInFlight = false;
                return;
            }
            apiUnreachable = false;
            if (sc == null) {
                cfgInFlight = false; // ответил, но ответ непонятный: состояние не меняем, проверка остаётся
                return;
            }
            serverLocked = sc.locked;
            serverOpen = !sc.authRequired;
            notice = sc.notice;
            if (!sc.newApi.isEmpty() && isNewTarget(sc.newApi)) {
                migrateTo(sc.newApi); // cfgInFlight снимется после проверки нового адреса
            } else {
                cfgInFlight = false;
            }
        }));
    }

    /** Новый адрес: только https и отличается от текущего. */
    private static boolean isNewTarget(String target) {
        String t = AuthClient.apiBase(target);
        String cur = AuthClient.apiBase(SpotHelperConfig.INSTANCE.authApiUrl);
        return t.regionMatches(true, 0, "https://", 0, 8) && !t.equalsIgnoreCase(cur);
    }

    /**
     * Переезд сервера: новый адрес должен ответить на /config, только после этого он сохраняется
     * в конфиг. Если не ответил, остаёмся на старом (попробуем снова при следующей проверке).
     */
    private static void migrateTo(String target) {
        final MinecraftClient mc = MinecraftClient.getInstance();
        final String base = AuthClient.apiBase(target);
        CLIENT.fetchServerConfig(base).whenComplete((nc, ex) -> mc.execute(() -> {
            cfgInFlight = false;
            if (ex != null || nc == null || nc.unreachable) return;
            SpotHelperConfig c = SpotHelperConfig.INSTANCE;
            c.authApiUrl = base + "/auth";
            c.save();
            serverLocked = nc.locked;
            serverOpen = !nc.authRequired;
            notice = nc.notice;
            lastCfgMs = 0L;     // сразу перечитать настройки с нового сервера
            lastAttemptMs = 0L; // и проверить сессию на нём
        }));
    }

    // ---------------------------------------------------------------- геттеры для GUI

    public static State state() {
        if (FROZEN) return State.AUTHORIZED;
        if (isAuthorized()) return State.AUTHORIZED;
        if (serverLocked) return State.DENIED;
        if (inFlight) return State.CHECKING;
        if (lastFailure == State.CHECKING && token.isEmpty()) return State.LOGGED_OUT;
        return lastFailure;
    }

    public static String login() { return login; }

    /** Токен сессии (для заголовков запросов за данными: шахты, защита игроков). */
    public static String token() { return token; }

    /** true, если администратор выключил авторизацию на сервере. */
    public static boolean serverOpen() { return serverOpen; }

    /** Сообщение игрокам от администратора или пустая строка. */
    public static String notice() { return notice; }

    /** Логин для подстановки в поле: текущий или последний использованный. */
    public static String savedLogin() {
        if (!login.isEmpty()) return login;
        String s = SpotHelperConfig.INSTANCE.lastAuthUsername;
        return s == null ? "" : s;
    }

    public static String reason() {
        return serverLocked ? "Доступ временно закрыт администратором" : failReason;
    }

    /** true, если API сейчас не отвечает (доступ при этом открыт). */
    public static boolean apiUnreachable() { return apiUnreachable; }

    /** true, если в панели выбрано «API лежит». */
    public static boolean serverLocked() { return serverLocked; }
    public static String tier() { return current.tier; }
    public static long expiresAt() { return current.expiresAt; }

    public static String mcName() {
        try {
            Session s = MinecraftClient.getInstance().getSession();
            return s == null ? "" : s.getUsername();
        } catch (Throwable t) {
            return "";
        }
    }

    /** Пароль уйдёт открытым текстом (http не на localhost)? */
    public static boolean insecure() {
        return AuthClient.isInsecure(SpotHelperConfig.INSTANCE.authApiUrl);
    }

    // ---------------------------------------------------------------- запросы

    private static CompletableFuture<AuthResult> submit(boolean isLogin, String l, String password) {
        CompletableFuture<AuthResult> done = new CompletableFuture<>();
        if (FROZEN) {
            done.complete(AuthResult.unavailable("Авторизация временно отключена"));
            return done;
        }
        if (inFlight) {
            done.complete(AuthResult.unavailable("Запрос уже выполняется"));
            return done;
        }
        MinecraftClient mc = MinecraftClient.getInstance();
        final String lg = l == null ? "" : l.trim();
        final String pw = password == null ? "" : password;
        inFlight = true;

        CompletableFuture<AuthResult> req = isLogin
                ? CLIENT.login(lg, pw)
                : CLIENT.register(lg, pw, mcName(), mcUuid(mc));

        req.whenComplete((r, ex) -> mc.execute(() -> {
            inFlight = false;
            AuthResult res = (ex != null || r == null) ? AuthResult.unavailable("API недоступен") : r;
            if (isLogin) applyLogin(lg, res); else applyRegister(res);
            done.complete(res);
        }));
        return done;
    }

    private static void requestCheck() {
        if (FROZEN || inFlight || token.isEmpty()) return;
        inFlight = true;
        lastAttemptMs = System.currentTimeMillis();
        final MinecraftClient mc = MinecraftClient.getInstance();
        final String l = login;
        final String t = token;

        CLIENT.check(l, t).whenComplete((r, ex) -> mc.execute(() -> {
            inFlight = false;
            AuthResult res = (ex != null || r == null) ? AuthResult.unavailable("API недоступен") : r;
            applyCheck(l, t, res);
        }));
    }

    private static void applyLogin(String lg, AuthResult r) {
        markReachable(r);
        switch (r.status) {
            case AUTHORIZED -> {
                if (r.token.isEmpty()) {
                    lastFailure = State.DENIED;
                    failReason = "Сервер не выдал токен сессии";
                } else if (!r.isValidNow()) {
                    lastFailure = State.DENIED;
                    failReason = "Срок доступа истёк";
                } else {
                    login = r.username.isEmpty() ? lg : r.username;
                    token = r.token;
                    current = r;
                    failReason = "";
                    notified = false;
                    writeCache();
                }
            }
            case PENDING -> {
                lastFailure = State.PENDING;
                failReason = r.reason;
            }
            case DENIED -> {
                lastFailure = State.DENIED;
                failReason = r.reason;
            }
            default -> {
                lastFailure = State.UNAVAILABLE;
                failReason = r.reason;
            }
        }
        mirrorToConfig();
    }

    private static void applyRegister(AuthResult r) {
        markReachable(r);
        switch (r.status) {
            case PENDING -> {
                lastFailure = State.PENDING;
                failReason = r.reason;
            }
            case AUTHORIZED -> { // сервер одобрил сразу: нужен обычный вход
                lastFailure = State.PENDING;
                failReason = "Заявка принята. Выполните вход.";
            }
            case DENIED -> {
                lastFailure = State.DENIED;
                failReason = r.reason;
            }
            default -> {
                lastFailure = State.UNAVAILABLE;
                failReason = r.reason;
            }
        }
    }

    private static void applyCheck(String l, String t, AuthResult r) {
        markReachable(r);
        // Пока шёл запрос, сессию могли сменить — ответ уже не про неё.
        if (!l.equals(login) || !t.equals(token)) return;

        switch (r.status) {
            case AUTHORIZED -> {
                if (!r.isValidNow()) {
                    current = AuthResult.denied("Срок доступа истёк");
                    lastFailure = State.DENIED;
                    failReason = "Срок доступа истёк";
                } else {
                    if (!r.token.isEmpty()) token = r.token;
                    current = r;
                    failReason = "";
                }
                writeCache();
            }
            case PENDING -> {
                current = AuthResult.denied("");
                lastFailure = State.PENDING;
                failReason = r.reason;
                writeCache();
            }
            case DENIED -> {
                current = r;
                lastFailure = State.DENIED;
                failReason = r.reason;
                if (r.relogin) {
                    token = "";
                    deleteCache();
                } else {
                    writeCache(); // токен оставляем: если админ продлит срок, доступ вернётся сам
                }
            }
            default -> {
                // API недоступен: действующую сессию не трогаем, иначе — «недоступно».
                if (!current.isValidNow()) {
                    lastFailure = State.UNAVAILABLE;
                    failReason = r.reason;
                }
            }
        }
        mirrorToConfig();
    }

    private static String mcUuid(MinecraftClient mc) {
        try {
            Session s = mc.getSession();
            UUID u = s == null ? null : s.getUuidOrNull();
            return u == null ? "" : u.toString();
        } catch (Throwable t) {
            return "";
        }
    }

    // ---------------------------------------------------------------- кеш

    private static void loadCache() {
        try {
            if (!Files.exists(CACHE_PATH)) return;
            String json = Files.readString(CACHE_PATH, StandardCharsets.UTF_8);
            CacheFile c = GSON.fromJson(json, CacheFile.class);
            if (c == null || c.login == null || c.token == null || c.login.isEmpty() || c.token.isEmpty()) return;
            login = c.login;
            token = c.token;
            AuthResult r = AuthResult.fromCache(c.login, c.tier, c.expiresAt, c.features);
            if (r.isValidNow()) current = r;
        } catch (Exception ignored) {
            // битый кеш = нет кеша
        }
    }

    private static void writeCache() {
        if (login.isEmpty() || token.isEmpty()) return;
        try {
            AuthResult r = current;
            boolean ok = r.isValidNow();
            CacheFile c = new CacheFile();
            c.login = login;
            c.token = token;
            c.tier = ok ? r.tier : "";
            c.expiresAt = ok ? r.expiresAt : 0L;
            c.features = ok ? new ArrayList<>(r.features) : new ArrayList<>();
            Files.writeString(CACHE_PATH, GSON.toJson(c), StandardCharsets.UTF_8);
        } catch (IOException ignored) {}
    }

    private static void deleteCache() {
        try { Files.deleteIfExists(CACHE_PATH); } catch (IOException ignored) {}
    }

    /** Итог зеркалится в spothelper.json (lastAuth*). Сохраняем только при изменении. */
    private static void mirrorToConfig() {
        SpotHelperConfig cfg = SpotHelperConfig.INSTANCE;
        AuthResult r = current;
        boolean ok = r.isValidNow();
        List<String> feats = ok ? new ArrayList<>(r.features) : new ArrayList<>();
        String name = login.isEmpty() ? (cfg.lastAuthUsername == null ? "" : cfg.lastAuthUsername) : login;
        long exp = ok ? r.expiresAt : 0L;
        String tier = ok ? r.tier : "";

        boolean same = ok == cfg.lastAuthResult
                && exp == cfg.lastAuthExpiresAt
                && name.equals(cfg.lastAuthUsername)
                && tier.equals(cfg.lastAuthTier)
                && feats.equals(cfg.lastAuthFeatures);
        if (same) return;

        cfg.lastAuthUsername = name;
        cfg.lastAuthResult = ok;
        cfg.lastAuthExpiresAt = exp;
        cfg.lastAuthTier = tier;
        cfg.lastAuthFeatures = feats;
        cfg.save();
    }
}