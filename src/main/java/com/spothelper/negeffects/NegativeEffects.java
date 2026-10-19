package com.spothelper.negeffects;

import com.spothelper.config.SpotHelperConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.registry.entry.RegistryEntry;

/**
 * Отключение негативных визуальных эффектов — только на клиенте, только на время отрисовки кадра.
 * Серверные эффекты, их длительность и клиентская игровая логика (тики) не затрагиваются:
 * состояние игрока подменяется внутри GameRenderer.render и сразу возвращается.
 */
public final class NegativeEffects {
    public static final int BLINDNESS = 1;
    public static final int NAUSEA = 1 << 1;
    public static final int DAMAGE_TILT = 1 << 2;
    public static final int DARKNESS = 1 << 3;
    public static final int VIEW_BOB = 1 << 4;
    public static final int PORTAL = 1 << 5;
    public static final int VIGNETTE = 1 << 6;
    public static final int LIGHTNING = 1 << 7;
    public static final int ALL = 0xFF;

    private static boolean inFrame;
    private static boolean nauseaSaved;
    private static float savedNausea, savedLastNausea;

    private NegativeEffects() {}

    /** Включён ли эффект (главный переключатель + отдельный). */
    public static boolean hides(int flag) {
        SpotHelperConfig c = SpotHelperConfig.INSTANCE;
        return c.negEffectsEnabled == 1 && (c.negEffectsMask & flag) != 0;
    }

    /** Нужно ли скрыть статус-эффект для этой сущности прямо сейчас (только клиентский игрок, только во время кадра). */
    public static boolean hidesStatusEffect(LivingEntity entity, RegistryEntry<StatusEffect> effect) {
        if (!inFrame || effect == null) return false;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (entity != mc.player && entity != mc.getCameraEntity()) return false;
        if (effect.equals(StatusEffects.BLINDNESS)) return hides(BLINDNESS);
        if (effect.equals(StatusEffects.DARKNESS)) return hides(DARKNESS);
        return false;
    }

    /** Начало кадра: включает подмену эффектов и, при необходимости, гасит интенсивность тошноты/портала. */
    public static void beginFrame(MinecraftClient mc, NauseaAccess access) {
        inFrame = false;
        nauseaSaved = false;
        if (SpotHelperConfig.INSTANCE.negEffectsEnabled != 1) return;
        inFrame = true;
        ClientPlayerEntity p = mc.player;
        if (p == null || access == null) return;
        // эффект тошноты и портал делят одну интенсивность: без зелья считаем её порталом
        boolean nausea = p.hasStatusEffect(StatusEffects.NAUSEA);
        if (nausea ? hides(NAUSEA) : hides(PORTAL)) {
            savedNausea = access.spothelper$getNausea();
            savedLastNausea = access.spothelper$getLastNausea();
            access.spothelper$setNausea(0f, 0f);
            nauseaSaved = true;
        }
    }

    /** Конец кадра: возвращает исходное состояние. */
    public static void endFrame(NauseaAccess access) {
        inFrame = false;
        if (nauseaSaved && access != null) access.spothelper$setNausea(savedNausea, savedLastNausea);
        nauseaSaved = false;
    }

    public interface NauseaAccess {
        float spothelper$getNausea();
        float spothelper$getLastNausea();
        void spothelper$setNausea(float intensity, float last);
    }
}
