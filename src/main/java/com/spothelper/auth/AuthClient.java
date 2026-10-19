package com.spothelper.auth;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

/**
 * HTTP-клиент: register / login / auth (проверка токена). Только JDK + Gson.
 * Future НИКОГДА не завершается исключением. Пароль идёт только в теле POST,
 * нигде не логируется и не сохраняется.
 */
public final class AuthClient {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(8);

    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "SpotHelper-Auth");
        t.setDaemon(true);
        return t;
    });

    private final HttpClient http;
    private final Supplier<String> baseUrl;

    public AuthClient(Supplier<String> baseUrl) {
        this.baseUrl = baseUrl;
        this.http = HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .followRedirects(HttpClient.Redirect.NEVER)
                .executor(EXECUTOR)
                .build();
    }

    public CompletableFuture<AuthResult> login(String login, String password) {
        JsonObject b = new JsonObject();
        b.addProperty("login", login);
        b.addProperty("password", password);
        return post("/login", b);
    }

    public CompletableFuture<AuthResult> register(String login, String password, String mcName, String mcUuid) {
        JsonObject b = new JsonObject();
        b.addProperty("login", login);
        b.addProperty("password", password);
        b.addProperty("minecraft", mcName == null ? "" : mcName);
        b.addProperty("uuid", mcUuid == null ? "" : mcUuid);
        return post("/register", b);
    }

    /** Проверка действующей сессии по токену (без пароля). */
    public CompletableFuture<AuthResult> check(String login, String token) {
        JsonObject b = new JsonObject();
        b.addProperty("login", login);
        b.addProperty("token", token);
        return post("/auth", b);
    }

    /** Настройки сервера из /config (без входа). */
    public static final class ServerConfig {
        public final boolean authRequired;
        /** Новый адрес API, если администратор переехал на другой сервер (иначе пусто). */
        public final String newApi;
        /** Короткое сообщение игрокам (иначе пусто). */
        public final String notice;
        /** Администратор выбрал в панели «API лежит»: доступа нет ни у кого. */
        public final boolean locked;
        /** API не ответил (сеть, таймаут, 5xx): по правилам доступ открыт. */
        public final boolean unreachable;

        ServerConfig(boolean authRequired, String newApi, String notice, boolean locked, boolean unreachable) {
            this.authRequired = authRequired;
            this.newApi = newApi == null ? "" : newApi;
            this.notice = notice == null ? "" : notice;
            this.locked = locked;
            this.unreachable = unreachable;
        }

        static final ServerConfig UNREACHABLE = new ServerConfig(true, "", "", false, true);
    }

    public CompletableFuture<ServerConfig> fetchServerConfig() {
        return fetchServerConfig(baseUrl.get());
    }

    /**
     * Запрос /config у указанного адреса.
     * ServerConfig.UNREACHABLE = API не ответил (сеть, таймаут, 5xx);
     * null = ответил, но ответ непонятный (404, не JSON и т.п.).
     */
    public CompletableFuture<ServerConfig> fetchServerConfig(String base) {
        final URI uri;
        try {
            uri = buildUri(base, "/config");
        } catch (Exception e) {
            return CompletableFuture.completedFuture(null);
        }
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(REQUEST_TIMEOUT)
                .header("Accept", "application/json")
                .header("User-Agent", "SpotHelper")
                .GET()
                .build();
        return http.sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                .thenApply(AuthClient::parseServerConfig)
                .exceptionally(t -> ServerConfig.UNREACHABLE);
    }

    private static ServerConfig parseServerConfig(HttpResponse<String> resp) {
        if (resp.statusCode() >= 500) return ServerConfig.UNREACHABLE;
        if (resp.statusCode() != 200) return null;
        try {
            JsonObject o = JsonParser.parseString(resp.body()).getAsJsonObject();
            // «API лежит» в панели: поле locked (или apiDown)
            boolean hasLock = o.has("locked") || o.has("apiDown");
            if (!o.has("authRequired") && !hasLock) return null;
            boolean locked = flag(o, "locked") || flag(o, "apiDown");
            boolean authRequired = !o.has("authRequired") || flag(o, "authRequired");
            return new ServerConfig(
                    authRequired,
                    o.has("newApi") && o.get("newApi").isJsonPrimitive() ? o.get("newApi").getAsString() : "",
                    o.has("notice") && o.get("notice").isJsonPrimitive() ? o.get("notice").getAsString() : "",
                    locked, false);
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean flag(JsonObject o, String key) {
        try {
            return o.has(key) && o.get(key).isJsonPrimitive() && o.get(key).getAsBoolean();
        } catch (Exception e) {
            return false;
        }
    }

    private CompletableFuture<AuthResult> post(String path, JsonObject body) {
        final URI uri;
        try {
            uri = buildUri(baseUrl.get(), path);
        } catch (Exception e) {
            return CompletableFuture.completedFuture(AuthResult.unavailable("Неверный URL API"));
        }
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(REQUEST_TIMEOUT)
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .header("User-Agent", "SpotHelper")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
                .build();

        return http.sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                .thenApply(AuthClient::toResult)
                .exceptionally(t -> AuthResult.unavailable("API недоступен"));
    }

    private static AuthResult toResult(HttpResponse<String> resp) {
        int code = resp.statusCode();
        String body = resp.body();

        if (code < 200 || code >= 300 && code < 400 || code >= 500) {
            return AuthResult.unavailable("HTTP " + code);
        }
        if (body == null || body.isBlank()) {
            return code < 300 ? AuthResult.unavailable("Пустой ответ API") : AuthResult.denied("HTTP " + code);
        }
        AuthResult r = AuthResult.fromJson(body);
        if (r.status == AuthResult.Status.UNAVAILABLE) {
            return code >= 400 ? AuthResult.denied("HTTP " + code) : r;
        }
        // 4xx не может означать «authorized»: защита от кривого прокси/сервера.
        if (code >= 400 && r.authorized) return AuthResult.denied("HTTP " + code);
        return r;
    }

    // ---------------------------------------------------------------- URL

    /** Убирает хвостовой "/" и старый суффикс "/auth", чтобы работали оба варианта конфига. */
    public static String apiBase(String base) {
        String b = base == null ? "" : base.trim();
        while (b.endsWith("/")) b = b.substring(0, b.length() - 1);
        if (b.endsWith("/auth")) b = b.substring(0, b.length() - 5);
        return b;
    }

    static URI buildUri(String base, String path) {
        String b = apiBase(base);
        if (b.isEmpty()) throw new IllegalArgumentException("empty url");
        URI uri = URI.create(b + path);
        String scheme = uri.getScheme();
        if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
            throw new IllegalArgumentException("bad scheme");
        }
        return uri;
    }

    /** true, если пароль пойдёт открытым текстом по сети (http не на localhost). */
    public static boolean isInsecure(String base) {
        try {
            URI u = URI.create(apiBase(base));
            String h = u.getHost();
            boolean loopback = h == null || h.equals("localhost") || h.equals("127.0.0.1")
                    || h.equals("[::1]") || h.equals("::1");
            return "http".equalsIgnoreCase(u.getScheme()) && !loopback;
        } catch (Exception e) {
            return false;
        }
    }
}