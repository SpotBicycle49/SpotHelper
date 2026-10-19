package com.spothelper.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Самопроверка миксинов. Первое срабатывание каждого хука пишется в latest.log,
 * а через ~6 секунд в мире в чат выводится список хуков, которые так и не сработали.
 */
public final class MixinDebug {
    private static final Logger LOG = LoggerFactory.getLogger("SpotHelper");
    private static final Map<String, Integer> HITS = new ConcurrentHashMap<>();
    private static final String[] REQUIRED = {"Camera.update", "Camera.setRotation", "GameRenderer.getFov"};
    private static int ticksInWorld;
    private static boolean reported;

    private MixinDebug() {}

    public static void hit(String id) {
        if (HITS.merge(id, 1, Integer::sum) == 1) {
            LOG.info("[SpotHelper] hook fired: {}", id);
        }
    }

    public static int hits(String id) {
        return HITS.getOrDefault(id, 0);
    }

    public static void tick(MinecraftClient mc) {
        if (mc == null || mc.player == null) {
            ticksInWorld = 0;
            return;
        }
        if (reported || ++ticksInWorld < 120) return;
        reported = true;

        List<String> missing = new ArrayList<>();
        for (String id : REQUIRED) {
            if (hits(id) == 0) missing.add(id);
        }
        if (missing.isEmpty()) {
            LOG.info("[SpotHelper] all required hooks fired");
            return;
        }
        String msg = "SpotHelper: не сработали хуки: " + String.join(", ", missing);
        LOG.warn("[SpotHelper] {}", msg);
        mc.player.sendMessage(Text.literal(msg), false);
    }
}
