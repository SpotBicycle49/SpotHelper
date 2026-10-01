package com.spothelper.client;

import net.fabricmc.loader.api.FabricLoader;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Предохранитель от "тихих" вылетов (когда игра закрывается без краш-репорта).
 *
 * Перед рискованным действием (первая отрисовка кастомным шрифтом, создание курсора мыши)
 * пишется маленький файл-флаг, после успешного завершения - удаляется. Если процесс умер
 * посреди действия, флаг остаётся, и при следующем запуске мод сам отключает виновника
 * (config: fancyText / customCursors).
 */
final class GuiGuard {
    private GuiGuard() {
    }

    private static Path file(String stage) {
        return FabricLoader.getInstance().getConfigDir().resolve("spothelper_guard_" + stage + ".txt");
    }

    static void begin(String stage) {
        try {
            Files.write(file(stage), "open".getBytes(StandardCharsets.UTF_8));
        } catch (Exception ignored) {
        }
    }

    static void end(String stage) {
        try {
            Files.deleteIfExists(file(stage));
        } catch (Exception ignored) {
        }
    }

    /** true, если прошлый запуск оборвался на этом этапе. Флаг при этом снимается. */
    static boolean takeTripped(String stage) {
        try {
            Path p = file(stage);
            if (Files.exists(p)) {
                Files.deleteIfExists(p);
                return true;
            }
        } catch (Exception ignored) {
        }
        return false;
    }
}
