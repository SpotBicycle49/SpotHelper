package com.spothelper.gui.ui;

import java.util.HashMap;
import java.util.Map;

/**
 * Плавные значения для GUI: каждый кадр значение по ключу приближается к цели (экспоненциально).
 * Новый или давно не использованный ключ сразу получает целевое значение (без «прыжка» при прокрутке
 * и смене раздела), поэтому ключи по позиции безопасны.
 */
public final class UiAnim {
    private UiAnim() {}

    /** Скорость сглаживания, 1/сек: меньше — медленнее. 7 ≈ 0.5 с до практически полного значения. */
    private static final float SPEED = 7f;
    private static final long STALE_NS = 250_000_000L;
    private static final class E {
        float v;
        long t;
    }

    private static final Map<Object, E> VALUES = new HashMap<>();

    public static float approach(Object key, float target) {
        return approach(key, target, SPEED);
    }

    /** То же, но со своей скоростью (1/сек). */
    public static float approach(Object key, float target, float speed) {
        long now = System.nanoTime();
        E e = VALUES.get(key);
        if (e == null) {
            if (VALUES.size() > 600) prune(now);
            e = new E();
            e.v = target;
            e.t = now;
            VALUES.put(key, e);
            return target;
        }
        long idle = now - e.t;
        e.t = now;
        if (idle > STALE_NS) {
            e.v = target;
        } else {
            float k = 1f - (float) Math.exp(-Math.min(idle / 1_000_000_000f, 0.05f) * speed);
            e.v += (target - e.v) * k;
            if (Math.abs(target - e.v) < 0.002f) e.v = target;
        }
        return e.v;
    }

    /** Принудительно задаёт значение (старт анимации с нужной точки). */
    public static void set(Object key, float v) {
        E e = VALUES.get(key);
        if (e == null) {
            e = new E();
            VALUES.put(key, e);
        }
        e.v = v;
        e.t = System.nanoTime();
    }

    /** Текущее значение без обновления. */
    public static float get(Object key, float def) {
        E e = VALUES.get(key);
        return e == null ? def : e.v;
    }

    private static void prune(long now) {
        VALUES.values().removeIf(e -> now - e.t > 2_000_000_000L);
    }

    /** Ключ для элементов, которые различаются только позицией. */
    public static Object pos(int ns, int x, int y) {
        return ((long) ns << 56) ^ ((long) x << 28) ^ (long) (y & 0xFFFFFFF);
    }
}
