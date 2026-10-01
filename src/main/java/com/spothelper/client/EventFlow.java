package com.spothelper.client;

import com.spothelper.SpotHelperConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.scoreboard.ScoreboardPlayerScore;
import net.minecraft.scoreboard.Team;
import net.minecraft.text.LiteralText;
import net.minecraft.text.MutableText;
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
 *
 * Каждый шаг запускается только по клику на карточку, постоянного цикла нет.
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
    /** Анархию определить не удалось: сначала смотрим /event здесь, переходим только если нужного ивента нет. */
    private static boolean probeSwitch;
    private static ClientWorld worldBefore;
    private static ClientPlayerEntity playerBefore;
    private static final List<String> lines = new ArrayList<String>();

    // что показывать на карточке
    private static HolyWorldData.EventRow badgeRow;
    private static String badgeText = "";
    private static int badgeKind = 0;            // 1 идёт, 2 готово, 3 ошибка
    private static long badgeUntil = 0L;

    // последняя известная анархия (действует, пока не сменился мир)
    private static int knownAnarchy = -1;
    private static ClientWorld knownWorld;

    private static final Pattern TRAILING_NUM = Pattern.compile("(\\d+)\\s*$");
    private static final Pattern P_XYZ = Pattern.compile("(?i)x\\s*[:=]?\\s*(-?\\d+)[\\s,;]+y\\s*[:=]?\\s*(-?\\d+)[\\s,;]+z\\s*[:=]?\\s*(-?\\d+)");
    private static final Pattern P_XZ = Pattern.compile("(?i)x\\s*[:=]?\\s*(-?\\d+)[\\s,;]+z\\s*[:=]?\\s*(-?\\d+)");
    private static final Pattern P_TRIPLE = Pattern.compile("(-?\\d{1,7})[\\s,;]+(-?\\d{1,3})[\\s,;]+(-?\\d{1,7})");
    private static final Pattern P_PAIR = Pattern.compile("(-?\\d{1,7})[\\s,;]+(-?\\d{1,7})");
    private static final String[] EVENT_WORDS = {"куб", "кораб", "груз", "посыл", "полян", "тыпо", "контейнер",
            "шахт", "лихорад", "охот", "бункер", "торгов", "замок", "босс", "ядро"};

    private static Pattern anarchyPattern;
    private static String anarchyPatternSrc = "";

    private EventFlow() {
    }

    // ------------------------------------------------------------------ для GUI

    public static boolean active() {
        return state != State.IDLE;
    }

    public static boolean badgeVisible() {
        return state != State.IDLE || System.currentTimeMillis() < badgeUntil;
    }

    public static HolyWorldData.EventRow badgeRow() { return badgeRow; }
    public static String badgeText() { return badgeText; }
    public static int badgeKind() { return badgeKind; }

    // ------------------------------------------------------------------ запуск

    public static void start(HolyWorldData.EventRow e) {
        if (state != State.IDLE) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        SpotHelperConfig cfg = SpotHelperConfig.INSTANCE;

        // координаты уже пришли из приложения - серверу ничего отправлять не нужно
        if (e.hasCoords) {
            row = e;
            done(e.x, e.y, e.z, e.hasY);
            return;
        }
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
        boolean needSwitch = target > 0 && cur != target;   // cur == -1: анархию определить не удалось

        if (needSwitch) {
            String cmd = switchCommand(cfg);
            boolean canSwitch = cfg.autoSwitchAnarchy == 1 && !cmd.isEmpty();
            if (canSwitch && cur > 0) {
                beginSwitch(mc, cmd);                           // точно знаем, что анархия другая
                return;
            }
            if (canSwitch) {
                probeSwitch = true;                             // не знаем - сначала проверим /event на текущей
                chat(Formatting.GOLD, "не удалось определить текущую анархию, проверяю /event здесь.");
                sendEvent(mc);
                return;
            }
            if (cur > 0) {
                String why = cfg.autoSwitchAnarchy != 1
                        ? "автопереход выключен (autoSwitchAnarchy)."
                        : "команда перехода не задана: впиши её в config/spothelper.json -> switchCommand.";
                fail("Ты на анархии " + cur + ", а ивент на " + target + ", " + why);
                return;
            }
            chat(Formatting.GOLD, "не удалось определить текущую анархию (проверь anarchyRegex), спрашиваю /event на текущей.");
        }
        sendEvent(mc);
    }

    private static String switchCommand(SpotHelperConfig cfg) {
        String c = cfg.switchCommand == null ? "" : cfg.switchCommand.trim();
        if (c.isEmpty()) return "";
        c = c.replace("{n}", String.valueOf(target));
        return c.startsWith("/") ? c : "/" + c;
    }

    private static void beginSwitch(MinecraftClient mc, String cmd) {
        state = State.SWITCHING;
        probeSwitch = false;
        ticks = 0;
        joined = false;
        unknownCommand = false;
        worldBefore = mc.world;
        playerBefore = mc.player;
        setBadge("Переход на " + target + "…", 1);
        chat(Formatting.WHITE, "перехожу на анархию " + target + "…");
        mc.player.sendChatMessage(cmd);
    }

    private static void sendEvent(MinecraftClient mc) {
        if (mc.player == null) {
            fail("Нет соединения с сервером.");
            return;
        }
        SpotHelperConfig cfg = SpotHelperConfig.INSTANCE;
        String cmd = cfg.eventCommand == null || cfg.eventCommand.trim().isEmpty() ? "/event" : cfg.eventCommand.trim();
        if (!cmd.startsWith("/")) cmd = "/" + cmd;
        state = State.WAIT_EVENT;
        ticks = 0;
        lastLineTick = 0;
        lines.clear();
        unknownCommand = false;
        setBadge("Запрос " + cmd + "…", 1);
        mc.player.sendChatMessage(cmd);
    }

    // ------------------------------------------------------------------ тик и чат

    public static void tick(MinecraftClient mc) {
        if (state == State.IDLE) return;
        ticks++;

        if (state == State.SWITCHING) {
            if (unknownCommand) {
                fail("сервер не знает команду перехода. Проверь switchCommand в config/spothelper.json.");
                return;
            }
            if (!joined && mc.world != null && mc.player != null
                    && (mc.world != worldBefore || mc.player != playerBefore)) {
                joined = true;
                joinedTick = ticks;
            }
            if (joined && ticks - joinedTick >= 50) {          // дать миру и скорборду загрузиться
                int cur = currentAnarchy(mc);
                if (cur > 0 && cur != target) {
                    fail("попал на анархию " + cur + " вместо " + target + ".");
                    return;
                }
                if (cur == target) remember(mc, target);
                sendEvent(mc);
                return;
            }
            if (!joined && ticks > 400) {                       // 20 секунд без входа в новый мир
                int cur = currentAnarchy(mc);
                if (cur > 0 && cur != target) {
                    fail("не удалось перейти на анархию " + target + " (сейчас " + cur + ").");
                } else {
                    chat(Formatting.GOLD, "переход не подтверждён, спрашиваю /event на текущей.");
                    sendEvent(mc);
                }
            }
            return;
        }

        if (state == State.WAIT_EVENT) {
            boolean quiet = !lines.isEmpty() && ticks - lastLineTick >= 25;
            if (quiet || ticks >= 140 || unknownCommand) finishEvent(mc);
        }
    }

    /** Вызывается миксином для каждого входящего сообщения чата (главный поток). */
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
        if (probeSwitch) {                                      // здесь нужного ивента нет - переходим и спрашиваем снова
            probeSwitch = false;
            String cmd = switchCommand(SpotHelperConfig.INSTANCE);
            if (!cmd.isEmpty() && SpotHelperConfig.INSTANCE.autoSwitchAnarchy == 1) {
                chat(Formatting.WHITE, "на этой анархии такого ивента нет.");
                beginSwitch(mc, cmd);
                return;
            }
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.size() && i < 3; i++) {
            if (i > 0) sb.append(" | ");
            String l = lines.get(i);
            sb.append(l.length() > 70 ? l.substring(0, 70) + "…" : l);
        }
        System.out.println("[SpotHelper] /event reply lines: " + lines);
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
        mc.keyboard.setClipboard(coords);

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

        MutableText msg = new LiteralText("SpotHelper ").formatted(Formatting.AQUA);
        msg.append(new LiteralText(sb.toString()).formatted(Formatting.WHITE));
        msg.append(new LiteralText(coords).formatted(Formatting.GREEN));
        if (mc.inGameHud != null) mc.inGameHud.getChatHud().addMessage(msg);

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
        mc.inGameHud.getChatHud().addMessage(new LiteralText("SpotHelper ").formatted(Formatting.AQUA)
                .append(new LiteralText(text).formatted(color)));
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

    /** Номер текущей анархии или -1. Ищет в скорборде, затем использует последнее известное значение. */
    static int currentAnarchy(MinecraftClient mc) {
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
                            } catch (NumberFormatException ignored) { }
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
        List<String> out = new ArrayList<String>();
        try {
            Scoreboard sb = mc.world.getScoreboard();
            ScoreboardObjective obj = sb.getObjectiveForSlot(1);        // 1 = боковая панель
            if (obj == null) return out;
            out.add(Formatting.strip(obj.getDisplayName().getString()));
            for (ScoreboardPlayerScore score : sb.getAllPlayerScores(obj)) {
                String name = score.getPlayerName();
                if (name == null) continue;
                Team team = sb.getPlayerTeam(name);
                String line = (team != null ? team.getPrefix().getString() : "") + name
                        + (team != null ? team.getSuffix().getString() : "");
                out.add(Formatting.strip(line));
            }
        } catch (Throwable ignored) {
        }
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

    /** Ключ типа ивента: последнее слово названия без окончания ("Игральный куб" -> "куб", "Посылка" -> "посыл"). */
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

    /** {x, y, z, hasY} для нужного ивента или null. */
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
            if (!anyEvent && ctxMatches) return c;              // координаты на строке после названия ивента
        }
        return coordLines == 1 ? first : null;                  // единственный ивент - берём его
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
