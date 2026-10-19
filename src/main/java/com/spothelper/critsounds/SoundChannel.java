package com.spothelper.critsounds;

import com.spothelper.config.SpotHelperConfig;

import java.util.List;

/**
 * Один из независимых наборов пользовательских звуков: крит, удар по цели, по игроку, по мобу,
 * сплеш, взмах в воздух, удар по блоку, заклик. У каждого своя папка config/SpotHelper/&lt;folder&gt;/,
 * свой переключатель, громкость, режим выбора и список файлов (общая система SoundLibrary / SoundPlayer).
 * CRIT и HIT хранятся в прежних полях конфига (старые настройки не теряются), остальные — в soundSlots.
 */
public final class SoundChannel {
    private enum Store { CRIT, HIT, SLOT }

    public static final SoundChannel CRIT = new SoundChannel(AttackTracker.Sound.CRIT, Store.CRIT, "crit_sounds", "crit",
            "Звуки критов", "Свой звук при критическом ударе (по игроку, мобу и любой сущности)", true,
            "Звук играет, когда сервер подтвердил критический удар: для крита нужно бить в падении, "
                    + "не в спринте, с заряженной атакой.");
    public static final SoundChannel HIT = new SoundChannel(AttackTracker.Sound.HIT, Store.HIT, "hit_sounds", "hit",
            "Удар по цели", "Любой подтверждённый обычный удар по сущности", true,
            "Звук играет при обычном ударе, который засчитал сервер (не при крите, не при сплеше). "
                    + "Если для игрока или моба включён свой звук, он заменяет этот.");
    public static final SoundChannel HIT_PLAYER = new SoundChannel(AttackTracker.Sound.HIT_PLAYER, Store.SLOT,
            "hit_player_sounds", "hit_player", "Удар по игроку", "Обычный удар, попавший по игроку", true,
            "Звук играет только при обычном ударе по игроку. Если звук не выбран, играет «Удар по цели».");
    public static final SoundChannel HIT_MOB = new SoundChannel(AttackTracker.Sound.HIT_MOB, Store.SLOT,
            "hit_mob_sounds", "hit_mob", "Удар по мобу", "Обычный удар по мобу и другой не-игровой сущности", true,
            "Звук играет при обычном ударе по мобу или другой сущности, кроме игрока. "
                    + "Если звук не выбран, играет «Удар по цели».");
    public static final SoundChannel SWEEP = new SoundChannel(AttackTracker.Sound.SWEEP, Store.SLOT,
            "sweep_sounds", "sweep", "Сплеш-атака", "Размашистая атака мечом (sweep)", true,
            "Звук играет, когда сервер подтвердил размашистую атаку мечом. Сервер даёт тот же сигнал и когда "
                    + "рядом нет других целей, поэтому клиент не может проверить, что урон получили несколько "
                    + "сущностей. Если канал не играет, такой удар озвучивается как обычный.");
    public static final SoundChannel AIR = new SoundChannel(AttackTracker.Sound.AIR, Store.SLOT,
            "air_sounds", "air", "Промах по воздуху", "Взмах без попадания по цели или блоку", false,
            "Звук играет на каждый взмах, который Minecraft выполнил в пустоту. Если игра отбросила клик "
                    + "(например, из-за паузы между взмахами), звука нет.");
    public static final SoundChannel BLOCK = new SoundChannel(AttackTracker.Sound.BLOCK, Store.SLOT,
            "block_sounds", "block", "Удар по блоку", "Клик атаки по блоку (начало разрушения)", false,
            "Звук играет на клик ЛКМ по блоку. Непрерывное удержание кнопки при ломании блока игра обрабатывает "
                    + "отдельно, на каждый такт звук не играет.");
    public static final SoundChannel SPAM = new SoundChannel(AttackTracker.Sound.SPAM, Store.SLOT,
            "spam_sounds", "spam", "Заклик (шкала не заряжена)", "Взмах по цели, пока шкала атаки не восстановилась", false,
            "Звук играет сразу при каждом взмахе по цели с незаряженной шкалой. Это звук самой попытки, попаданием "
                    + "он не считается. Слабое попадание этого клика отдельным звуком удара не дублируется.");

    /** Порядок как в меню. */
    public static final List<SoundChannel> ALL = List.of(CRIT, HIT, HIT_PLAYER, HIT_MOB, SWEEP, AIR, BLOCK, SPAM);

    public final String title;
    public final String desc;
    public final SoundLibrary library;
    private final AttackTracker.Sound sound;
    private final Store store;
    private final String id;
    private final boolean vanilla;
    private final String hint;

    private SoundChannel(AttackTracker.Sound sound, Store store, String folder, String id, String title, String desc,
                         boolean vanilla, String hint) {
        this.sound = sound;
        this.store = store;
        this.id = id;
        this.title = title;
        this.desc = desc;
        this.vanilla = vanilla;
        this.hint = hint;
        this.library = new SoundLibrary(folder);
    }

    public static SoundChannel of(AttackTracker.Sound s) {
        for (SoundChannel c : ALL) if (c.sound == s) return c;
        throw new IllegalArgumentException(String.valueOf(s));
    }

    public AttackTracker.Sound sound() {
        return sound;
    }

    public boolean isCrit() {
        return store == Store.CRIT;
    }

    /** Есть ли у события свой ванильный звук, который можно заменить (у взмаха, заклика и блока его нет). */
    public boolean hasVanilla() {
        return vanilla;
    }

    /** Пояснение «когда играет» для меню и гайда. */
    public String hint() {
        return hint;
    }

    private static SpotHelperConfig cfg() {
        return SpotHelperConfig.INSTANCE;
    }

    private SpotHelperConfig.SoundSlot slot() {
        return cfg().soundSlots.computeIfAbsent(id, k -> new SpotHelperConfig.SoundSlot());
    }

    public boolean enabled() {
        return switch (store) {
            case CRIT -> cfg().critSoundsEnabled == 1;
            case HIT -> cfg().hitSoundsEnabled == 1;
            case SLOT -> slot().enabled == 1;
        };
    }

    public void toggleEnabled() {
        int v = enabled() ? 0 : 1;
        switch (store) {
            case CRIT -> cfg().critSoundsEnabled = v;
            case HIT -> cfg().hitSoundsEnabled = v;
            case SLOT -> slot().enabled = v;
        }
    }

    public String name() {
        String n = switch (store) {
            case CRIT -> cfg().critSoundName;
            case HIT -> cfg().hitSoundName;
            case SLOT -> slot().name;
        };
        return n == null ? "" : n;
    }

    public void setName(String n) {
        switch (store) {
            case CRIT -> cfg().critSoundName = n;
            case HIT -> cfg().hitSoundName = n;
            case SLOT -> slot().name = n;
        }
    }

    public float volume() {
        float v = switch (store) {
            case CRIT -> cfg().critSoundVolume;
            case HIT -> cfg().hitSoundVolume;
            case SLOT -> slot().volume;
        };
        return Math.max(0f, Math.min(1f, v));
    }

    public void setVolume(float v) {
        switch (store) {
            case CRIT -> cfg().critSoundVolume = v;
            case HIT -> cfg().hitSoundVolume = v;
            case SLOT -> slot().volume = v;
        }
    }

    public boolean random() {
        return switch (store) {
            case CRIT -> cfg().critSoundRandom == 1;
            case HIT -> cfg().hitSoundRandom == 1;
            case SLOT -> slot().random == 1;
        };
    }

    public void toggleRandom() {
        int v = random() ? 0 : 1;
        switch (store) {
            case CRIT -> cfg().critSoundRandom = v;
            case HIT -> cfg().hitSoundRandom = v;
            case SLOT -> slot().random = v;
        }
    }

    public List<String> pool() {
        return switch (store) {
            case CRIT -> cfg().critSoundPool;
            case HIT -> cfg().hitSoundPool;
            case SLOT -> slot().pool;
        };
    }

    public boolean replaceVanilla() {
        if (!vanilla) return false;
        return switch (store) {
            case CRIT -> cfg().critSoundReplaceVanilla == 1;
            case HIT -> cfg().hitSoundReplaceVanilla == 1;
            case SLOT -> slot().replaceVanilla == 1;
        };
    }

    public void toggleReplaceVanilla() {
        int v = replaceVanilla() ? 0 : 1;
        switch (store) {
            case CRIT -> cfg().critSoundReplaceVanilla = v;
            case HIT -> cfg().hitSoundReplaceVanilla = v;
            case SLOT -> slot().replaceVanilla = v;
        }
    }

    public void save() {
        cfg().save();
    }
}
