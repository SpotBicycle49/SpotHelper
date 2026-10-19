package com.spothelper.tnt;

import com.spothelper.config.SpotHelperConfig;
import com.spothelper.util.WorldToScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.TntEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Таймеры ТНТ. Время берётся из самого TntEntity (getFuse() — остаток тиков), поэтому кастомные ТНТ
 * с любым временем взрыва считаются верно: ничего не предполагается про «80 тиков».
 *
 * Каждый активированный ТНТ запоминается и остаётся в списке (HUD), пока не взорвётся: если сущность
 * пропала из мира раньше времени (вышла из дальности прорисовки), остаток досчитывается локально
 * по клиентским тикам до нуля.
 */
public final class TntTimerManager {
    private TntTimerManager() {}

    public static final class Entry {
        public final int id;
        public Vec3d pos;
        public int remaining;       // тиков до взрыва
        public String name;         // кастомное имя ТНТ или null
        TntEntity live;             // пока сущность есть в мире

        Entry(int id) { this.id = id; }
    }

    private static final double SCAN_RADIUS = 256.0;
    private static final int MAX_HUD_LINES = 12;

    private static final Map<Integer, Entry> TRACKED = new HashMap<>();
    private static ClientWorld lastWorld;

    /** Раз в клиентский тик. */
    public static void tick(MinecraftClient mc) {
        SpotHelperConfig cfg = SpotHelperConfig.INSTANCE;
        if (!cfg.isTntTimerEnabled() || mc == null || mc.player == null || mc.world == null) {
            TRACKED.clear();
            lastWorld = null;
            return;
        }
        if (mc.world != lastWorld) { // другой мир/измерение — старые таймеры не актуальны
            TRACKED.clear();
            lastWorld = mc.world;
        }

        Box box = mc.player.getBoundingBox().expand(SCAN_RADIUS);
        List<TntEntity> found = mc.world.getEntitiesByClass(TntEntity.class, box, e -> !e.isRemoved());

        for (Entry en : TRACKED.values()) en.live = null;
        for (TntEntity e : found) {
            Entry en = TRACKED.computeIfAbsent(e.getId(), Entry::new);
            en.live = e;
            en.pos = new Vec3d(e.getX(), e.getY(), e.getZ());
            en.remaining = e.getFuse();
            en.name = e.hasCustomName() && e.getCustomName() != null ? e.getCustomName().getString() : null;
        }

        // не видим сущность: досчитываем локально, при нуле считаем взорвавшимся
        TRACKED.values().removeIf(en -> {
            if (en.live != null) return en.remaining <= 0;
            en.remaining--;
            return en.remaining <= 0;
        });
    }

    /** Ближайшие по времени взрыва записи (для HUD). */
    public static List<Entry> entries() {
        List<Entry> out = new ArrayList<>(TRACKED.values());
        out.sort(Comparator.comparingInt(en -> en.remaining));
        return out;
    }

    public static int maxHudLines() { return MAX_HUD_LINES; }

    public static String seconds(int ticks) {
        return String.format(Locale.ROOT, "%.1f", Math.max(0, ticks) / 20.0);
    }

    public static int color(int ticks) {
        if (ticks > 40) return 0xFF55FF55;
        if (ticks > 20) return 0xFFFFFF55;
        return 0xFFFF5555;
    }

    /** Подписи над ТНТ в мире (только для тех, что сейчас в радиусе подписей). */
    public static void render(DrawContext ctx, float tickProgress) {
        SpotHelperConfig cfg = SpotHelperConfig.INSTANCE;
        if (!cfg.isTntTimerEnabled() || TRACKED.isEmpty()) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null || mc.options.hudHidden) return;

        TextRenderer tr = mc.textRenderer;
        int sw = mc.getWindow().getScaledWidth();
        int sh = mc.getWindow().getScaledHeight();
        double range = cfg.getTntTimerRange();

        for (Entry en : TRACKED.values()) {
            TntEntity e = en.live;
            if (e == null || e.isRemoved()) continue;
            if (e.squaredDistanceTo(mc.player) > range * range) continue;
            Vec3d p = e.getLerpedPos(tickProgress).add(0.0, 1.2, 0.0);
            float[] s = WorldToScreen.project(mc, p);
            if (s == null || s[0] < 0 || s[0] > sw || s[1] < 0 || s[1] > sh) continue;
            ctx.drawCenteredTextWithShadow(tr, seconds(en.remaining) + " с",
                    Math.round(s[0]), Math.round(s[1]) - 4, color(en.remaining));
        }
    }
}
