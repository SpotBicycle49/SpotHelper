package com.spothelper.critsounds;

import com.spothelper.config.SpotHelperConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.sound.SoundCategory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Подтверждение критического удара и выбор звука.
 *
 * Как определяется крит. Клиент сам не знает, засчитал ли сервер крит: условия (кулдаун, падение,
 * вода, спринт, лестницы и т. д.) проверяет сервер. Поэтому мы не гадаем по прыжку или клику, а ждём
 * подтверждение сервера на конкретный удар игрока:
 *   1) пакет анимации «крит» (id 4) с id той цели, которую мы только что ударили;
 *   2) пакет ванильного звука entity.player.attack.crit рядом с нашей позицией.
 * Промах в атаку не превращается вовсе (attackEntity не вызывается), обычный удар подтверждений не
 * получает. Каждый удар хранится как Pending: один удар даёт один звук, даже если пришли оба пакета.
 * Все методы on* вызываются только из главного потока клиента.
 */
public final class CritSounds {
    private static final long WINDOW_MS = 1500;
    private static final double MAX_DIST_SQ = 3.5 * 3.5;
    private static final int MAX_PENDING = 8;

    private static final class Pending {
        final int targetId;
        final long atMs;
        final double x, y, z;
        boolean gotSound, gotAnim, played, usedCustom;

        Pending(int targetId, long atMs, double x, double y, double z) {
            this.targetId = targetId;
            this.atMs = atMs;
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    private static final ArrayDeque<Pending> PENDING = new ArrayDeque<>();

    private CritSounds() {}

    private static SpotHelperConfig cfg() {
        return SpotHelperConfig.INSTANCE;
    }

    // ------------------------------------------------------------------ жизненный цикл

    public static void init() {
        CritSoundLibrary.ensureDir();
        CritSoundLibrary.refresh();
        preloadSelected();
    }

    public static void shutdown() {
        CritSoundPlayer.shutdown();
    }

    // ------------------------------------------------------------------ события игры

    /** Игрок ударил сущность (ClientPlayerInteractionManager.attackEntity). */
    public static void onAttack(PlayerEntity player, Entity target) {
        if (!cfg().isCritSoundsEnabled() || player == null || target == null) return;
        long now = System.currentTimeMillis();
        prune(now);
        PENDING.addLast(new Pending(target.getId(), now, player.getX(), player.getY(), player.getZ()));
        while (PENDING.size() > MAX_PENDING) PENDING.pollFirst();
    }

    /** Пакет анимации. Возвращает true, если это подтверждение крита нашего удара. */
    public static boolean onAnimation(int entityId, int animationId) {
        if (animationId != 4 || !cfg().isCritSoundsEnabled()) return false;
        long now = System.currentTimeMillis();
        prune(now);
        for (Pending p : PENDING) {
            if (p.targetId == entityId && !p.gotAnim) {
                p.gotAnim = true;
                confirm(p);
                return true;
            }
        }
        return false;
    }

    /**
     * Пакет звука entity.player.attack.crit. Возвращает true, если его нужно подавить
     * (звук принадлежит нашему криту и вместо него сыграл пользовательский).
     */
    public static boolean onCritSoundPacket(double x, double y, double z) {
        if (!cfg().isCritSoundsEnabled()) return false;
        MinecraftClient mc = MinecraftClient.getInstance();
        long now = System.currentTimeMillis();
        prune(now);
        for (Pending p : PENDING) {
            if (p.gotSound) continue;
            if (!near(p, x, y, z, mc.player)) continue;
            p.gotSound = true;
            confirm(p);
            return p.usedCustom && cfg().critSoundReplaceVanilla == 1;
        }
        return false;
    }

    private static boolean near(Pending p, double x, double y, double z, PlayerEntity now) {
        double dx = x - p.x, dy = y - p.y, dz = z - p.z;
        if (dx * dx + dy * dy + dz * dz <= MAX_DIST_SQ) return true;
        if (now == null) return false;
        dx = x - now.getX();
        dy = y - now.getY();
        dz = z - now.getZ();
        return dx * dx + dy * dy + dz * dz <= MAX_DIST_SQ;
    }

    /** Первое подтверждение удара запускает звук, второе (другой пакет того же удара) — нет. */
    private static void confirm(Pending p) {
        if (!p.played) {
            p.played = true;
            p.usedCustom = playConfirmedCrit();
        }
        if (p.gotSound && p.gotAnim) PENDING.remove(p);
    }

    private static void prune(long now) {
        Iterator<Pending> it = PENDING.iterator();
        while (it.hasNext()) if (now - it.next().atMs > WINDOW_MS) it.remove();
    }

    // ------------------------------------------------------------------ выбор и запуск

    private static boolean playConfirmedCrit() {
        CritSoundLibrary.Entry e = pick();
        if (e == null) return false;
        return CritSoundPlayer.playCrit(e, effectiveVolume());
    }

    /** Какой файл играть сейчас; null — обычный звук Minecraft (в т. ч. если файл ещё не декодирован). */
    private static CritSoundLibrary.Entry pick() {
        SpotHelperConfig c = cfg();
        if (c.critSoundRandom == 1) {
            List<CritSoundLibrary.Entry> ready = new ArrayList<>();
            for (String n : c.critSoundPool) {
                CritSoundLibrary.Entry e = CritSoundLibrary.find(n);
                if (e == null || !e.supported() || CritSoundPlayer.error(e) != null) continue;
                if (CritSoundPlayer.cached(e) != null) ready.add(e);
                else CritSoundPlayer.preload(e);
            }
            return ready.isEmpty() ? null : ready.get(ThreadLocalRandom.current().nextInt(ready.size()));
        }
        CritSoundLibrary.Entry e = CritSoundLibrary.find(c.critSoundName);
        if (e == null || !e.supported() || CritSoundPlayer.error(e) != null) return null;
        if (CritSoundPlayer.cached(e) == null) {
            CritSoundPlayer.preload(e);
            return null;
        }
        return e;
    }

    /** Ползунок × громкость игры (общая и «Игроки»), как у обычных звуков. */
    private static float effectiveVolume() {
        float v = cfg().getCritSoundVolume();
        try {
            var o = MinecraftClient.getInstance().options;
            v *= o.getSoundVolume(SoundCategory.MASTER) * o.getSoundVolume(SoundCategory.PLAYERS);
        } catch (Throwable ignored) {
        }
        return v;
    }

    // ------------------------------------------------------------------ действия GUI

    public static void preloadSelected() {
        SpotHelperConfig c = cfg();
        if (c.critSoundRandom == 1) {
            for (String n : c.critSoundPool) CritSoundPlayer.preload(CritSoundLibrary.find(n));
        } else {
            CritSoundPlayer.preload(CritSoundLibrary.find(c.critSoundName));
        }
    }

    public static boolean isActive(String name) {
        SpotHelperConfig c = cfg();
        return c.critSoundRandom == 1 ? c.critSoundPool.contains(name) : name.equals(c.critSoundName);
    }

    /** «Выбрать»: в обычном режиме назначает активный звук, в случайном — включает/исключает из набора. */
    public static void select(String name) {
        SpotHelperConfig c = cfg();
        if (c.critSoundRandom == 1) {
            if (!c.critSoundPool.remove(name)) c.critSoundPool.add(name);
        } else {
            c.critSoundName = name;
        }
        c.save();
        preloadSelected();
    }

    /** Стандартный режим: пользовательский звук не назначен, играет обычный звук Minecraft. */
    public static void selectDefault() {
        SpotHelperConfig c = cfg();
        if (c.critSoundRandom == 1) c.critSoundPool.clear();
        else c.critSoundName = "";
        c.save();
    }

    public static void toggleRandom() {
        SpotHelperConfig c = cfg();
        c.critSoundRandom = c.critSoundRandom == 1 ? 0 : 1;
        c.save();
        preloadSelected();
    }

    public static void preview(String name) {
        CritSoundLibrary.Entry e = CritSoundLibrary.find(name);
        if (e == null || !e.supported()) return;
        CritSoundPlayer.startPreview(e, effectiveVolume());
    }

    public static void delete(String name) {
        if (name.equals(CritSoundPlayer.previewing())) CritSoundPlayer.stopPreview();
        SpotHelperConfig c = cfg();
        if (CritSoundLibrary.delete(name)) {
            if (name.equals(c.critSoundName)) c.critSoundName = "";
            c.critSoundPool.remove(name);
            c.save();
        }
    }

    public static String importFile(Path src) throws IOException {
        return CritSoundLibrary.importFile(src);
    }

    /** Открывает папку в файловом менеджере ОС. false — не получилось (GUI покажет путь). */
    public static boolean openFolder() {
        CritSoundLibrary.ensureDir();
        String path = CritSoundLibrary.dir().toAbsolutePath().toString();
        String os = System.getProperty("os.name", "").toLowerCase();
        try {
            ProcessBuilder pb;
            if (os.contains("win")) pb = new ProcessBuilder("explorer.exe", path);
            else if (os.contains("mac")) pb = new ProcessBuilder("open", path);
            else pb = new ProcessBuilder("xdg-open", path);
            pb.start();
            return true; // explorer.exe возвращает ненулевой код даже при успехе, поэтому код выхода не проверяем
        } catch (Throwable t) {
            return false;
        }
    }
}
