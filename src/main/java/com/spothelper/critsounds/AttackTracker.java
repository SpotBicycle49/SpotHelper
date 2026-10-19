package com.spothelper.critsounds;

import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.function.Predicate;

/**
 * Чистая логика сопоставления действий игрока с событиями сервера (без зависимостей от Minecraft,
 * поэтому проверяется отдельным тестом). Все методы вызываются только из главного потока клиента.
 *
 * Что различается:
 *  - ВЗМАХ (swing) — клиентский факт: Minecraft реально вызвал swingHand внутри обработки ЛКМ.
 *    Тип определяется прицелом в момент клика: воздух / блок / сущность. Попаданием взмах не считается.
 *  - ПОПАДАНИЕ — только подтверждение сервера на конкретную атаку (attackEntity): ванильный звук
 *    attack.strong / attack.weak (обычный удар), attack.sweep (сплеш), attack.crit или анимация крита.
 *    Каждая атака получает ровно один исход; звук attack.nodamage закрывает атаку без звука.
 *  - КРИТ и СПЛЕШ — отдельные исходы того же пакета подтверждений, поэтому не пересекаются с обычным ударом.
 *
 * Сопоставление звуков с атаками идёт по очереди (FIFO): у каждой атаки сервер даёт ровно один итоговый
 * звук, поэтому самая старая неразрешённая атака и есть владелец пришедшего звука. Пакет анимации крита
 * несёт id цели, по нему атака находится точно. Крит подтверждается двумя пакетами (звук и анимация),
 * их объединяет окно CRIT_MERGE_MS, поэтому один крит не даёт два звука даже при рассинхроне очереди.
 */
public final class AttackTracker {
    /** Каналы пользовательских звуков (соответствуют SoundChannel). */
    public enum Sound { CRIT, HIT, HIT_PLAYER, HIT_MOB, SWEEP, AIR, BLOCK, SPAM }

    /** Куда указывал прицел в момент клика. */
    public enum Swing { AIR, BLOCK, ENTITY }

    /** Ванильные звуки подтверждения атаки. */
    public enum Vanilla { CRIT, STRONG_OR_WEAK, SWEEP, NODAMAGE }

    public interface Env {
        long nowMs();

        /** Канал включён в настройках. */
        boolean enabled(Sound s);

        /** Запустить пользовательский звук канала. false — играть нечего (файл не выбран / не загружен). */
        boolean tryPlay(Sound s);

        /** Подавлять ли ванильный звук, когда сыграл пользовательский. */
        boolean replaceVanilla(Sound s);
    }

    static final long WINDOW_MS = 1200;
    static final long CRIT_MERGE_MS = 150;
    static final double MAX_DIST_SQ = 4.0 * 4.0;
    static final int MAX_PENDING = 16;

    private enum Outcome { CRIT, HIT, SWEEP, NODAMAGE }

    private static final class Pending {
        final int target;
        final boolean targetIsPlayer;
        final boolean charged;
        final long at;
        final double x, y, z;
        Outcome out; // null — сервер ещё не ответил
        boolean swingPlayed, critSound, critAnim, customUsed;

        Pending(int target, boolean targetIsPlayer, boolean charged, long at, double x, double y, double z) {
            this.target = target;
            this.targetIsPlayer = targetIsPlayer;
            this.charged = charged;
            this.at = at;
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    private static final class Click {
        final Swing type;
        Pending pending;
        boolean swung;

        Click(Swing type) {
            this.type = type;
        }
    }

    private final Env env;
    private final ArrayDeque<Pending> queue = new ArrayDeque<>();
    private Click click;
    private long lastCritPlayMs = Long.MIN_VALUE / 2;
    private boolean lastCritUsed;

    public AttackTracker(Env env) {
        this.env = env;
    }

    private boolean anyEnabled() {
        for (Sound s : Sound.values()) if (env.enabled(s)) return true;
        return false;
    }

    // ------------------------------------------------------------------ действия игрока

    /** Начало обработки ЛКМ (MinecraftClient.doAttack). */
    public void beginClick(Swing type) {
        click = new Click(type);
    }

    /** Конец обработки ЛКМ. Если Minecraft клик отбросил (нет взмаха), ничего не воспроизводится. */
    public void endClick() {
        click = null;
    }

    /** Minecraft вызвал swingHand внутри клика. */
    public void onSwing() {
        Click c = click;
        if (c == null || c.swung) return;
        c.swung = true;
        switch (c.type) {
            case AIR -> {
                if (env.enabled(Sound.AIR)) env.tryPlay(Sound.AIR);
            }
            case BLOCK -> {
                if (env.enabled(Sound.BLOCK)) env.tryPlay(Sound.BLOCK);
            }
            case ENTITY -> {
                Pending p = c.pending;
                // Шкала не восстановлена: это «заклик». Звук взмаха играет сразу, попаданием он не считается.
                if (p != null && !p.charged && env.enabled(Sound.SPAM) && env.tryPlay(Sound.SPAM)) {
                    p.swingPlayed = true;
                }
            }
        }
    }

    /** attackEntity не отменён: атака ушла на сервер. */
    public void onAttack(int targetId, boolean targetIsPlayer, boolean charged, double x, double y, double z) {
        if (!anyEnabled()) return;
        long now = env.nowMs();
        prune(now);
        Pending p = new Pending(targetId, targetIsPlayer, charged, now, x, y, z);
        queue.addLast(p);
        while (queue.size() > MAX_PENDING) queue.pollFirst();
        if (click != null && click.type == Swing.ENTITY) click.pending = p;
    }

    // ------------------------------------------------------------------ подтверждения сервера

    /** Пакет анимации сущности с id 4 (крит). */
    public void onCritAnimation(int entityId) {
        if (!anyEnabled()) return;
        long now = env.nowMs();
        prune(now);
        Pending p = find(c -> c.out == Outcome.CRIT && c.critSound && !c.critAnim && c.target == entityId);
        if (p != null) {
            p.critAnim = true;
            cleanup(p);
            return;
        }
        p = find(c -> c.out == null && c.target == entityId);
        if (p == null) return;
        p.out = Outcome.CRIT;
        p.critAnim = true;
        playCrit(p, now);
        cleanup(p);
    }

    /**
     * Ванильный звук атаки игрока рядом с нами.
     * @return true — пакет нужно подавить (вместо него сыграл пользовательский звук)
     */
    public boolean onVanillaSound(Vanilla v, double x, double y, double z, double px, double py, double pz) {
        if (!anyEnabled()) return false;
        long now = env.nowMs();
        prune(now);
        Predicate<Pending> near = c -> near(c, x, y, z, px, py, pz);
        switch (v) {
            case CRIT -> {
                Pending p = find(c -> c.out == Outcome.CRIT && c.critAnim && !c.critSound && near.test(c));
                if (p != null) {
                    p.critSound = true;
                    boolean suppress = p.customUsed && env.replaceVanilla(Sound.CRIT);
                    cleanup(p);
                    return suppress;
                }
                p = find(c -> c.out == null && near.test(c));
                if (p == null) return false;
                p.out = Outcome.CRIT;
                p.critSound = true;
                boolean suppress = playCrit(p, now) && env.replaceVanilla(Sound.CRIT);
                cleanup(p);
                return suppress;
            }
            case STRONG_OR_WEAK -> {
                Pending p = find(c -> c.out == null && near.test(c));
                if (p == null) return false;
                p.out = Outcome.HIT;
                queue.remove(p);
                if (p.swingPlayed) return false; // этот клик уже получил звук взмаха
                return playHit(p);
            }
            case SWEEP -> {
                Pending p = find(c -> c.out == null && near.test(c));
                if (p == null) return false;
                p.out = Outcome.SWEEP;
                queue.remove(p);
                if (env.enabled(Sound.SWEEP) && env.tryPlay(Sound.SWEEP)) {
                    return env.replaceVanilla(Sound.SWEEP);
                }
                // Сплеш-канал не играет: удар по цели при этом всё равно был, озвучиваем его как обычный.
                return playHit(p);
            }
            case NODAMAGE -> {
                Pending p = find(c -> c.out == null && near.test(c));
                if (p != null) {
                    p.out = Outcome.NODAMAGE;
                    queue.remove(p);
                }
                return false;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ внутреннее

    /** Крит: один звук на удар, даже если пакеты пришли в другом порядке. Возвращает, играет ли пользовательский. */
    private boolean playCrit(Pending p, long now) {
        if (!env.enabled(Sound.CRIT)) return false;
        if (now - lastCritPlayMs <= CRIT_MERGE_MS) { // второй пакет того же крита
            p.customUsed = lastCritUsed;
            return lastCritUsed;
        }
        boolean used = env.tryPlay(Sound.CRIT);
        lastCritPlayMs = now;
        lastCritUsed = used;
        p.customUsed = used;
        return used;
    }

    /**
     * Обычное попадание. Выбирается самый конкретный включённый канал, у которого есть что играть:
     * игрок/моб, затем «удар по цели». Одно попадание даёт не больше одного звука.
     */
    private boolean playHit(Pending p) {
        Sound[] order = p.targetIsPlayer
                ? new Sound[]{Sound.HIT_PLAYER, Sound.HIT}
                : new Sound[]{Sound.HIT_MOB, Sound.HIT};
        for (Sound s : order) {
            if (env.enabled(s) && env.tryPlay(s)) {
                p.customUsed = true;
                return env.replaceVanilla(s);
            }
        }
        return false;
    }

    private boolean near(Pending p, double x, double y, double z, double px, double py, double pz) {
        if (sq(x - p.x, y - p.y, z - p.z) <= MAX_DIST_SQ) return true;
        return !Double.isNaN(px) && sq(x - px, y - py, z - pz) <= MAX_DIST_SQ;
    }

    private static double sq(double dx, double dy, double dz) {
        return dx * dx + dy * dy + dz * dz;
    }

    private Pending find(Predicate<Pending> f) {
        for (Pending p : queue) if (f.test(p)) return p;
        return null;
    }

    private void cleanup(Pending p) {
        if (p.critSound && p.critAnim) queue.remove(p);
    }

    private void prune(long now) {
        Iterator<Pending> it = queue.iterator();
        while (it.hasNext()) if (now - it.next().at > WINDOW_MS) it.remove();
    }
}
