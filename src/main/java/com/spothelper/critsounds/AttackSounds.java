package com.spothelper.critsounds;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.sound.SoundCategory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Пользовательские звуки боя: клиентская обвязка вокруг AttackTracker (там вся логика различения событий).
 *
 * Источники событий:
 *  - клик ЛКМ и взмах: MinecraftClientAttackMixin (doAttack) и ClientPlayerEntitySwingMixin (swingHand);
 *  - атака по сущности: ClientPlayerInteractionManagerMixin (attackEntity, после проверки защиты игроков);
 *  - подтверждения сервера (крит, обычный удар, сплеш, удар без урона): ClientPlayNetworkHandlerCritMixin.
 * Мод не меняет урон, кулдаун и пакеты: звук только слушает то, что игра и сервер делают сами.
 * Методы on* и *Click вызываются только из главного потока клиента.
 */
public final class AttackSounds {
    private static final AttackTracker TRACKER = new AttackTracker(new AttackTracker.Env() {
        @Override
        public long nowMs() {
            return System.currentTimeMillis();
        }

        @Override
        public boolean enabled(AttackTracker.Sound s) {
            return SoundChannel.of(s).enabled();
        }

        @Override
        public boolean tryPlay(AttackTracker.Sound s) {
            return playChannel(SoundChannel.of(s));
        }

        @Override
        public boolean replaceVanilla(AttackTracker.Sound s) {
            return SoundChannel.of(s).replaceVanilla();
        }
    });

    private AttackSounds() {}

    // ------------------------------------------------------------------ жизненный цикл

    public static void init() {
        for (SoundChannel ch : SoundChannel.ALL) {
            ch.library.ensureDir();
            ch.library.refresh();
            preloadSelected(ch);
        }
    }

    public static void shutdown() {
        SoundPlayer.shutdown();
    }

    // ------------------------------------------------------------------ события игры

    /** Начало обработки клика ЛКМ: куда смотрит прицел. */
    public static void beginClick(AttackTracker.Swing type) {
        TRACKER.beginClick(type);
    }

    public static void endClick() {
        TRACKER.endClick();
    }

    /** Minecraft выполнил взмах рукой внутри клика ЛКМ. */
    public static void onSwing() {
        TRACKER.onSwing();
    }

    /** Игрок ударил сущность (ClientPlayerInteractionManager.attackEntity), атака не отменена. */
    public static void onAttack(PlayerEntity player, Entity target) {
        if (player == null || target == null) return;
        // шкала считается до сброса кулдауна; 0.9 — тот же порог, по которому сервер выбирает strong/weak
        boolean charged = player.getAttackCooldownProgress(0.5f) > 0.9f;
        TRACKER.onAttack(target.getId(), target instanceof PlayerEntity, charged,
                player.getX(), player.getY(), player.getZ());
    }

    /** Пакет анимации. Анимация «крит» (id 4) подтверждает крит по сущности с этим id. */
    public static void onAnimation(int entityId, int animationId) {
        if (animationId != 4) return;
        TRACKER.onCritAnimation(entityId);
    }

    /**
     * Ванильный звук атаки игрока.
     * Возвращает true, если пакет нужно подавить (он принадлежит нашей атаке, вместо него сыграл свой звук).
     */
    public static boolean onAttackSound(AttackTracker.Vanilla kind, double x, double y, double z) {
        var p = MinecraftClient.getInstance().player;
        double px = p == null ? Double.NaN : p.getX();
        double py = p == null ? Double.NaN : p.getY();
        double pz = p == null ? Double.NaN : p.getZ();
        return TRACKER.onVanillaSound(kind, x, y, z, px, py, pz);
    }

    // ------------------------------------------------------------------ выбор и запуск

    private static boolean playChannel(SoundChannel ch) {
        SoundLibrary.Entry e = pick(ch);
        if (e == null) return false;
        return SoundPlayer.playGame(e, effectiveVolume(ch));
    }

    /** Какой файл играть сейчас; null — обычный звук Minecraft (в т. ч. если файл ещё не декодирован). */
    private static SoundLibrary.Entry pick(SoundChannel ch) {
        if (ch.random()) {
            List<SoundLibrary.Entry> ready = new ArrayList<>();
            for (String n : ch.pool()) {
                SoundLibrary.Entry e = ch.library.find(n);
                if (e == null || !e.supported() || SoundPlayer.error(e) != null) continue;
                if (SoundPlayer.cached(e) != null) ready.add(e);
                else SoundPlayer.preload(e);
            }
            return ready.isEmpty() ? null : ready.get(ThreadLocalRandom.current().nextInt(ready.size()));
        }
        SoundLibrary.Entry e = ch.library.find(ch.name());
        if (e == null || !e.supported() || SoundPlayer.error(e) != null) return null;
        if (SoundPlayer.cached(e) == null) {
            SoundPlayer.preload(e);
            return null;
        }
        return e;
    }

    /** Ползунок × громкость игры (общая и «Игроки»), как у обычных звуков. */
    private static float effectiveVolume(SoundChannel ch) {
        float v = ch.volume();
        try {
            var o = MinecraftClient.getInstance().options;
            v *= o.getSoundVolume(SoundCategory.MASTER) * o.getSoundVolume(SoundCategory.PLAYERS);
        } catch (Throwable ignored) {
        }
        return v;
    }

    // ------------------------------------------------------------------ действия GUI

    public static void preloadSelected(SoundChannel ch) {
        if (ch.random()) {
            for (String n : ch.pool()) SoundPlayer.preload(ch.library.find(n));
        } else {
            SoundPlayer.preload(ch.library.find(ch.name()));
        }
    }

    public static boolean isActive(SoundChannel ch, String name) {
        return ch.random() ? ch.pool().contains(name) : name.equals(ch.name());
    }

    /** «Выбрать»: в обычном режиме назначает активный звук, в случайном — включает/исключает из набора. */
    public static void select(SoundChannel ch, String name) {
        if (ch.random()) {
            if (!ch.pool().remove(name)) ch.pool().add(name);
        } else {
            ch.setName(name);
        }
        ch.save();
        preloadSelected(ch);
    }

    /** Стандартный режим: пользовательский звук не назначен, играет обычный звук Minecraft. */
    public static void selectDefault(SoundChannel ch) {
        if (ch.random()) ch.pool().clear();
        else ch.setName("");
        ch.save();
    }

    public static void toggleRandom(SoundChannel ch) {
        ch.toggleRandom();
        ch.save();
        preloadSelected(ch);
    }

    public static void preview(SoundChannel ch, String name) {
        SoundLibrary.Entry e = ch.library.find(name);
        if (e == null || !e.supported()) return;
        SoundPlayer.startPreview(e, effectiveVolume(ch));
    }

    public static void delete(SoundChannel ch, String name) {
        SoundLibrary.Entry e = ch.library.find(name);
        if (e != null && SoundPlayer.isPreviewing(e)) SoundPlayer.stopPreview();
        if (ch.library.delete(name)) {
            if (name.equals(ch.name())) ch.setName("");
            ch.pool().remove(name);
            ch.save();
        }
    }

    public static String importFile(SoundChannel ch, Path src) throws IOException {
        return ch.library.importFile(src);
    }

    /** Открывает папку в файловом менеджере ОС. false — не получилось (GUI покажет путь). */
    public static boolean openFolder(SoundChannel ch) {
        ch.library.ensureDir();
        String path = ch.library.dir().toAbsolutePath().toString();
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
