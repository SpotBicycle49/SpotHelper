package com.spothelper.damagetint;

import com.spothelper.config.SpotHelperConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.LivingEntity;

import java.util.HashMap;
import java.util.Map;

/**
 * Красный тик урона. Цвет и прозрачность задаются через красную часть текстуры наложения
 * (см. OverlayTextureMixin), длительность — через собственный таймер на каждую сущность
 * (см. LivingEntityRendererMixin).
 */
public final class DamageTintManager {
    /** Ванильный цвет тика: 0xB2FF0000 (красный, alpha 0.7). */
    public static final int VANILLA_ARGB = 0xB2FF0000;

    public interface OverlayAccess { void spothelper$applyTint(int argb); }

    private static OverlayAccess overlay;
    private static boolean synced;
    private static final Map<Integer, long[]> HITS = new HashMap<>(); // id -> {expiryTick, lastHurtTime}
    private static long lastCleanup;

    private DamageTintManager() {}

    private static SpotHelperConfig cfg() { return SpotHelperConfig.INSTANCE; }

    public static void register(OverlayAccess o) {
        overlay = o;
        reapply();
    }

    /** Один раз после старта клиента подтягивает цвет из загруженного конфига. */
    public static void tick(MinecraftClient mc) {
        if (synced || overlay == null) return;
        synced = true;
        reapply();
    }

    /** ARGB, которым сейчас красится тик. */
    public static int currentArgb() {
        SpotHelperConfig c = cfg();
        if (!c.isDamageTintEnabled()) return VANILLA_ARGB;
        int a = Math.round(Math.max(0f, Math.min(1f, c.damageTintAlpha)) * 255f);
        return (a << 24) | (c.damageTintColor & 0x00FFFFFF);
    }

    /** Перерисовать текстуру наложения по текущим настройкам (вызывать после изменения конфига). */
    public static void reapply() {
        if (overlay == null) return;
        try {
            overlay.spothelper$applyTint(currentArgb());
        } catch (Throwable t) {
            com.spothelper.util.Log.error("damage tint", t);
        }
    }

    /**
     * Нужно ли красить сущность. Возвращает vanillaHurt, если функция выключена.
     * Иначе тик держится damageTintDurationTicks тиков с момента нового удара.
     */
    public static boolean isTinted(LivingEntity e, boolean vanillaHurt) {
        SpotHelperConfig c = cfg();
        if (!c.isDamageTintEnabled()) return vanillaHurt;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) return vanillaHurt;
        long now = mc.world.getTime();
        if (now - lastCleanup > 200) {
            lastCleanup = now;
            HITS.values().removeIf(v -> v[0] < now - 40);
        }
        if (e.deathTime > 0) return true;
        long[] rec = HITS.computeIfAbsent(e.getId(), k -> new long[]{0, 0});
        int ht = e.hurtTime;
        if (ht > rec[1] || (ht > 0 && rec[0] <= now - 1 && rec[1] == 0)) { // новый удар
            rec[0] = now + Math.max(1, c.damageTintDurationTicks);
        }
        rec[1] = ht;
        return now < rec[0];
    }

    /** Цвет, которым сейчас красится сущность (ARGB), или 0, если не окрашена. */
    public static int getColor(LivingEntity e) {
        if (!cfg().isDamageTintEnabled()) return 0;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) return 0;
        long[] rec = HITS.get(e.getId());
        if (rec == null || mc.world.getTime() >= rec[0]) return 0;
        return currentArgb();
    }
}
