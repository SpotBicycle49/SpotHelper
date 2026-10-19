package com.spothelper.holyworld;

import com.spothelper.config.SpotHelperConfig;
import com.spothelper.waypoints.WaypointManager;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardDisplaySlot;
import net.minecraft.scoreboard.ScoreboardEntry;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.scoreboard.Team;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Клик по ивенту:
 *  1) определяем, на какой анархии игрок сейчас;
 *  2) если не на той - отправляем команду перехода (config: switchCommand) и ждём входа;
 *  3) отправляем /event и читаем ответ из чата;
 *  4) копируем координаты и пишем сообщение в чат (только у тебя).
 */
public final class EventFlow {

    private enum State { IDLE, SWITCHING, WAIT_EVENT }

    private static State state = State.IDLE;
    private static HolyWorldData.EventRow row;
    private static int target = -1;
    private static int ticks;
    private static int lastLineTick;
    private static boolean joined;
    private static int joinedTick;
    private static boolean unknownCommand;
    private static boolean probeSwitch;
    private static boolean navPending;
    private static ClientWorld worldBefore;
    private static ClientPlayerEntity playerBefore;
    private static final List<String> lines = new ArrayList<>();

    private static HolyWorldData.EventRow badgeRow;
    private static String badgeText = "";
    private static int badgeKind = 0;
    private static long badgeUntil = 0L;

    private static int knownAnarchy = -1;
    private static ClientWorld knownWorld;

    private static final Pattern TRAILING_NUM = Pattern.compile("(\\d+)\\s*$");
    private static final Pattern P_XYZ = Pattern.compile("(?i)x\\s*[:=]?\\s*(-?\\d+)[\\s,;]+y\\s*[:=]?\\s*(-?\\d+)[\\s,;]+z\\s*[:=]?\\s*(-?\\d+)");
    private static final Pattern P_XZ = Pattern.compile("(?i)x\\s*[:=]?\\s*(-?\\d+)[\\s,;]+z\\s*[:=]?\\s*(-?\\d+)");
    private static final Pattern P_TRIPLE = Pattern.compile("(-?\\d{1,7})[\\s,;]+(-?\\d{1,3})[\\s,;]+(-?\\d{1,7})");
    private static final Pattern P_PAIR = Pattern.compile("(-?\\d{1,7})[\\s,;]+(-?\\d{1,7})");
    private static final String[] EVENT_WORDS = {"куб", "кораб", "груз", "посыл", "полян", "тыпо", "контейнер",
            "шахт", "лихорад", "охот", "бункер", "торгов", "замок", "босс", "ядро"};

    private static boolean coordsFromApi = false;
    /** Текущая/целевая анархия не подтверждена: метка ставится без привязки к номеру анархии. */
    private static boolean unverified = false;
    private static ClientWorld eventWorld;

    private static Pattern anarchyPattern;
    private static String anarchyPatternSrc = "";

    private EventFlow() {}

    // ------------------------------------------------------------------ GUI

    public static boolean active() { return state != State.IDLE; }

    public static boolean badgeVisible() {
        return state != State.IDLE || System.currentTimeMillis() < badgeUntil;
    }

    public static HolyWorldData.EventRow badgeRow() { return badgeRow; }
    public static String badgeText() { return badgeText; }
    public static int badgeKind() { return badgeKind; }

    // ------------------------------------------------------------------ запуск

    public static void start(HolyWorldData.EventRow e) {
        MinecraftClient mc = MinecraftClient.getInstance();
        SpotHelperConfig cfg = SpotHelperConfig.INSTANCE;

        // меню мода закрываем сразу, до любых команд и перехода
        closeModMenu(mc);

        if (state != State.IDLE || AnarchyNavigator.active()) {
            chat(Formatting.GOLD, "переход к ивенту уже выполняется, подожди.");
            return;
        }
        coordsFromApi = false;
        unverified = false;
        if (mc.player == null || mc.world == null) {
            row = e;
            fail("Ты не в игре.");
            return;
        }

        row = e;
        lines.clear();
        unknownCommand = false;
        probeSwitch = false;
        target = parseNumber(e.serverName);

        int cur = currentAnarchy(mc);
        boolean lobby = AnarchyNavigator.inLobby(mc);

        if (target > 0 && cur != target) {
            boolean autoOff = cfg.autoSwitchAnarchy != 1;
            boolean canSwitch = !autoOff && AnarchyNavigator.supports(target);
            if (canSwitch && (cur > 0 || lobby)) {
                beginSwitch(mc);
                return;
            }
            if (canSwitch) {
                probeSwitch = true;
                unverified = true;
                chat(Formatting.GOLD, "не удалось определить текущую анархию, проверяю /event здесь.");
                sendEvent(mc);
                return;
            }
            String why = autoOff ? "автопереход выключен (autoSwitchAnarchy)." : "номер анархии вне диапазона 1–36.";
            if (cur > 0 || lobby) {
                fail((cur > 0 ? "Ты на анархии " + cur : "Ты в лобби") + ", а ивент на " + target + ", " + why);
                return;
            }
            unverified = true;
            chat(Formatting.GOLD, "не удалось определить текущую анархию (проверь anarchyRegex), а " + why
                    + " Спрашиваю /event на текущей.");
            sendEvent(mc);
            return;
        }
        if (target <= 0) {
            if (lobby) {
                fail("Ты в лобби, а номер анархии ивента определить не удалось.");
                return;
            }
            unverified = true;
            chat(Formatting.GOLD, "не удалось определить анархию ивента, спрашиваю /event на текущей.");
        }
        sendEvent(mc);
    }

    /** Закрывает меню мода, если оно открыто (иначе оно мешает переходу по меню компаса). */
    private static void closeModMenu(MinecraftClient mc) {
        if (mc.currentScreen instanceof com.spothelper.gui.SpotHelperScreen) mc.setScreen(null);
    }

    private static String switchCommand(SpotHelperConfig cfg) {
        String c = cfg.switchCommand == null ? "" : cfg.switchCommand.trim();
        if (c.isEmpty()) return "";
        c = c.replace("{n}", String.valueOf(target));
        return c.startsWith("/") ? c : "/" + c;
    }

    /** Переход на анархию target по меню компаса (как команда /lt N). */
    private static void beginSwitch(MinecraftClient mc) {
        state = State.SWITCHING;
        probeSwitch = false;
        unverified = false; // после перехода анархия проверяется заново
        ticks = 0;
        joined = false;
        unknownCommand = false;
        worldBefore = mc.world;
        playerBefore = mc.player;
        navPending = true;
        setBadge("Переход на " + target + "…", 1);
        chat(Formatting.WHITE, "перехожу на анархию " + target + "…");
        if (!AnarchyNavigator.start(target, true)) {
            navPending = false;
            fail("неверный номер анархии " + target + ".");
        }
    }

    private static void sendEvent(MinecraftClient mc) {
        if (mc.player == null || mc.player.networkHandler == null) {
            fail("Нет соединения с сервером.");
            return;
        }
        SpotHelperConfig cfg = SpotHelperConfig.INSTANCE;
        String cmd = cfg.eventCommand == null || cfg.eventCommand.trim().isEmpty() ? "/event" : cfg.eventCommand.trim();
        String cmdBody = cmd.startsWith("/") ? cmd.substring(1) : cmd;
        state = State.WAIT_EVENT;
        eventWorld = mc.world;
        ticks = 0;
        lastLineTick = 0;
        lines.clear();
        unknownCommand = false;
        setBadge("Запрос /" + cmdBody + "…", 1);
        mc.player.networkHandler.sendChatCommand(cmdBody);
    }

    // ------------------------------------------------------------------ тик и чат

    public static void tick(MinecraftClient mc) {
        if (state == State.IDLE) return;
        ticks++;

        if (state == State.SWITCHING) {
            closeModMenu(mc); // если меню мода открыли снова, оно мешает кликам по меню компаса
            if (navPending) {
                // идёт навигация по меню (возможно, сначала /hub): смену мира считаем только после клика по серверу
                if (AnarchyNavigator.active()) {
                    ticks = 0;
                    return;
                }
                navPending = false;
                if (AnarchyNavigator.lastFailed()) {
                    fail("не удалось пройти по меню компаса.");
                    return;
                }
                unknownCommand = false;
                worldBefore = mc.world;
                playerBefore = mc.player;
                ticks = 0;
                return;
            }
            if (!joined && mc.world != null && mc.player != null
                    && (mc.world != worldBefore || mc.player != playerBefore)) {
                joined = true;
                joinedTick = ticks;
            }
            if (joined) {
                int waited = ticks - joinedTick;
                int cur = currentAnarchy(mc);
                if (waited >= 50 && cur > 0) {
                    if (cur != target) {
                        fail("попал на анархию " + cur + " вместо " + target + ".");
                        return;
                    }
                    remember(mc, target);
                    sendEvent(mc);
                    return;
                }
                if (waited >= 200) { // табло так и не показало номер анархии
                    if (AnarchyNavigator.inLobby(mc)) {
                        fail("после перехода ты оказался в лобби, а не на анархии " + target + ".");
                        return;
                    }
                    unverified = true;
                    chat(Formatting.GOLD, "не удалось подтвердить номер анархии после входа (проверь anarchyRegex), спрашиваю /event здесь.");
                    sendEvent(mc);
                }
                return;
            }
            if (ticks > 400) {
                int cur = currentAnarchy(mc);
                if (cur == target) {
                    sendEvent(mc);
                } else {
                    fail("вход на анархию " + target + " не подтверждён: мир не сменился"
                            + (cur > 0 ? " (сейчас анархия " + cur + ")." : "."));
                }
            }
            return;
        }

        if (state == State.WAIT_EVENT) {
            if (mc.world == null || mc.player == null) {
                fail("соединение потеряно, пока ждал ответ /event.");
                return;
            }
            if (mc.world != eventWorld) {
                fail("мир сменился, пока ждал ответ /event.");
                return;
            }
            boolean quiet = !lines.isEmpty() && ticks - lastLineTick >= 25;
            if (quiet || ticks >= 140 || unknownCommand) finishEvent(mc);
        }
    }

    public static void onChat(String text) {
        if (state == State.IDLE || text == null) return;
        String clean = Formatting.strip(text);
        if (clean == null || clean.trim().isEmpty()) return;
        for (String part : clean.split("\n")) {
            String t = part.trim();
            if (t.isEmpty()) continue;
            if (isUnknownCommand(t.toLowerCase(Locale.ROOT))) unknownCommand = true;
            if (state == State.WAIT_EVENT) {
                lines.add(t);
                lastLineTick = ticks;
            }
        }
    }

    private static boolean isNoEvent(List<String> reply) {
        for (String line : reply) {
            String low = line.toLowerCase(Locale.ROOT);
            boolean about = low.contains("ивент") || low.contains("событи") || low.contains("event");
            if (about && (low.contains("нет ") || low.contains("не найден") || low.contains("отсутств")
                    || low.contains("no active") || low.contains("no event") || low.contains("not active"))) {
                return true;
            }
        }
        return false;
    }

    private static boolean isUnknownCommand(String low) {
        return low.contains("unknown command") || low.contains("unknown or incomplete")
                || low.contains("неизвестная команда") || low.contains("неизвестной команды")
                || low.contains("такой команды нет") || low.contains("команда не найдена");
    }

    private static void finishEvent(MinecraftClient mc) {
        if (unknownCommand && lines.size() <= 1) {
            fail("сервер не знает команду события. Проверь eventCommand в config/spothelper.json.");
            return;
        }
        int[] c = parseReply(lines, row);
        if (c != null) {
            done(c[0], c[1], c[2], c[3] == 1);
            return;
        }
        if (probeSwitch) {
            probeSwitch = false;
            if (AnarchyNavigator.supports(target) && SpotHelperConfig.INSTANCE.autoSwitchAnarchy == 1) {
                chat(Formatting.WHITE, "на этой анархии такого ивента нет.");
                beginSwitch(mc);
                return;
            }
        }
        if (isNoEvent(lines)) {
            fail("на " + (target > 0 ? "анархии " + target : "этой анархии") + " сейчас нет активного ивента"
                    + " (ответ сервера: «нет ивента»).");
            return;
        }
        if (row != null && row.hasCoords) {
            chat(Formatting.GOLD, "ответ /event не содержит координат, беру их из API ивентов.");
            coordsFromApi = true;
            done(row.x, row.y, row.z, row.hasY);
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.size() && i < 3; i++) {
            if (i > 0) sb.append(" | ");
            String l = lines.get(i);
            sb.append(l.length() > 70 ? l.substring(0, 70) + "…" : l);
        }
        if (lines.isEmpty()) {
            fail("сервер ничего не ответил на /event (или чат не перехвачен).");
        } else {
            fail("не нашёл координаты нужного ивента в ответе. Ответ: " + sb);
        }
    }

    // ------------------------------------------------------------------ результат

    private static void done(int x, int y, int z, boolean hasY) {
        MinecraftClient mc = MinecraftClient.getInstance();
        HolyWorldData.EventRow e = row;
        state = State.IDLE;

        String coords = hasY ? (x + " " + y + " " + z) : (x + " " + z);
        if (mc.keyboard != null) mc.keyboard.setClipboard(coords);

        // ---- метка на миникарте ----
        try {
            SpotHelperConfig wcfg = SpotHelperConfig.INSTANCE;
            if (wcfg.isEventWaypoint() && mc.world != null) {
                int anarchyNum = unverified ? 0 : parseNumber(e.serverName);
                String wpName = cleanName(e.displayName) + (anarchyNum > 0 ? " #" + anarchyNum : "");
                int wy = hasY ? y : (mc.player != null ? (int) Math.floor(mc.player.getY()) : 64);
                long ttl = Math.max(0, wcfg.eventWaypointMinutes) * 60_000L;
                // координаты из API — измерение неизвестно, считаем Верхним миром;
                // координаты из ответа /event — измерение, где мы сейчас
                String wpDim = coordsFromApi ? WaypointManager.OVERWORLD : null;
                WaypointManager.setEvent(wpName, x, wy, z, anarchyNum, ttl, wpDim);
            }
        } catch (Throwable t) {
            com.spothelper.util.Log.error("event waypoint", t);
        }

        String server = e.serverName == null ? "" : e.serverName.trim();
        String num = "";
        String mode;
        Matcher mt = TRAILING_NUM.matcher(server);
        if (mt.find()) {
            num = mt.group(1);
            mode = server.substring(0, mt.start()).replace("#", "").trim();
        } else {
            mode = server;
        }
        mode = mode.replaceAll("(?iu)лайт$", "").trim().toLowerCase(Locale.ROOT);

        StringBuilder sb = new StringBuilder(cleanName(e.displayName));
        if (!num.isEmpty()) sb.append(" на ").append(num);
        if (!mode.isEmpty()) sb.append(" ").append(mode);
        sb.append(num.isEmpty() && mode.isEmpty() ? " на координатах " : " анархии на координатах ");

        MutableText msg = Text.literal("SpotHelper ").formatted(Formatting.AQUA);
        msg.append(Text.literal(sb.toString()).formatted(Formatting.WHITE));
        msg.append(Text.literal(coords).formatted(Formatting.GREEN));
        if (mc.inGameHud != null) mc.inGameHud.getChatHud().addMessage(msg);
        if (unverified) {
            chat(Formatting.GOLD, "анархия не подтверждена: координаты взяты с текущей анархии, метка без привязки к номеру.");
        }

        setBadge("Скопировано", 2);
        row = null;
    }

    private static void fail(String reason) {
        state = State.IDLE;
        chat(Formatting.GOLD, reason);
        setBadge("Ошибка", 3);
        row = null;
    }

    private static void chat(Formatting color, String text) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.inGameHud == null) return;
        mc.inGameHud.getChatHud().addMessage(Text.literal("SpotHelper ").formatted(Formatting.AQUA)
                .append(Text.literal(text).formatted(color)));
    }

    private static void setBadge(String text, int kind) {
        badgeRow = row;
        badgeText = text;
        badgeKind = kind;
        badgeUntil = kind == 1 ? Long.MAX_VALUE : System.currentTimeMillis() + 1800L;
    }

    // ------------------------------------------------------------------ текущая анархия

    private static void remember(MinecraftClient mc, int n) {
        knownAnarchy = n;
        knownWorld = mc.world;
    }

    public static int currentAnarchy(MinecraftClient mc) {
        if (mc.world == null) return -1;
        Pattern p = anarchyPattern();
        if (p != null) {
            for (String s : scoreboardTexts(mc)) {
                Matcher m = p.matcher(s);
                while (m.find()) {
                    for (int g = 1; g <= m.groupCount(); g++) {
                        String v = m.group(g);
                        if (v != null && !v.isEmpty()) {
                            try {
                                int n = Integer.parseInt(v);
                                remember(mc, n);
                                return n;
                            } catch (NumberFormatException ignored) {}
                        }
                    }
                }
            }
        }
        return knownWorld == mc.world ? knownAnarchy : -1;
    }

    private static Pattern anarchyPattern() {
        String src = SpotHelperConfig.INSTANCE.anarchyRegex;
        if (src == null || src.trim().isEmpty()) return null;
        if (!src.equals(anarchyPatternSrc)) {
            anarchyPatternSrc = src;
            try {
                anarchyPattern = Pattern.compile(src);
            } catch (Exception e) {
                anarchyPattern = null;
            }
        }
        return anarchyPattern;
    }

    private static List<String> scoreboardTexts(MinecraftClient mc) {
        List<String> out = new ArrayList<>();
        try {
            Scoreboard sb = mc.world.getScoreboard();
            ScoreboardObjective obj = sb.getObjectiveForSlot(ScoreboardDisplaySlot.SIDEBAR);
            if (obj == null) return out;
            out.add(Formatting.strip(obj.getDisplayName().getString()));
            for (ScoreboardEntry entry : sb.getScoreboardEntries(obj)) {
                String name = entry.owner();
                if (name == null) continue;
                Team team = sb.getScoreHolderTeam(name);
                String prefix = team != null ? team.getPrefix().getString() : "";
                String suffix = team != null ? team.getSuffix().getString() : "";
                out.add(Formatting.strip(prefix + name + suffix));
            }
        } catch (Throwable ignored) {}
        return out;
    }

    // ------------------------------------------------------------------ разбор ответа /event

    static int parseNumber(String serverName) {
        if (serverName == null) return -1;
        Matcher m = TRAILING_NUM.matcher(serverName.trim());
        if (!m.find()) return -1;
        try { return Integer.parseInt(m.group(1)); } catch (NumberFormatException e) { return -1; }
    }

    private static String cleanName(String s) {
        if (s == null) return "Событие";
        String r = s.replaceAll("\\s*#\\d+\\s*$", "").trim();
        return r.isEmpty() ? s : r;
    }

    private static String eventKey(String displayName) {
        String n = cleanName(displayName).toLowerCase(Locale.ROOT).trim();
        String[] words = n.split("\\s+");
        String w = words.length == 0 ? n : words[words.length - 1];
        int keep = Math.max(3, w.length() - 2);
        return w.length() <= keep ? w : w.substring(0, keep);
    }

    private static boolean containsAny(String low, String[] words) {
        for (String w : words) if (low.contains(w)) return true;
        return false;
    }

    static int[] parseReply(List<String> reply, HolyWorldData.EventRow e) {
        String key = eventKey(e.displayName);
        int[] first = null;
        int coordLines = 0;
        boolean ctxMatches = false;

        for (String line : reply) {
            String low = line.toLowerCase(Locale.ROOT);
            boolean anyEvent = containsAny(low, EVENT_WORDS);
            boolean mine = low.contains(key);
            if (anyEvent) ctxMatches = mine;

            int[] c = coordsIn(line);
            if (c == null) continue;
            coordLines++;
            if (first == null) first = c;
            if (mine) return c;
            if (!anyEvent && ctxMatches) return c;
        }
        return coordLines == 1 ? first : null;
    }

    private static int[] coordsIn(String line) {
        Matcher m = P_XYZ.matcher(line);
        if (m.find()) return new int[]{i(m, 1), i(m, 2), i(m, 3), 1};
        m = P_XZ.matcher(line);
        if (m.find()) return new int[]{i(m, 1), 0, i(m, 2), 0};
        m = P_TRIPLE.matcher(line);
        if (m.find()) return new int[]{i(m, 1), i(m, 2), i(m, 3), 1};
        if (line.toLowerCase(Locale.ROOT).contains("коорд")) {
            m = P_PAIR.matcher(line);
            if (m.find()) return new int[]{i(m, 1), 0, i(m, 2), 0};
        }
        return null;
    }

    private static int i(Matcher m, int g) {
        return Integer.parseInt(m.group(g));
    }
}