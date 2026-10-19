package com.spothelper.critsounds;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Папка пользовательских звуков: config/SpotHelper/crit_sounds/.
 * Файлы никогда не попадают в JAR мода. Список читается только по явному вызову refresh()
 * (кнопка «Обновить библиотеку»), поэтому в кадре отрисовки никакого ввода-вывода нет.
 */
public final class CritSoundLibrary {
    /** Один найденный файл. mtime входит в ключ кэша, чтобы перезаписанный файл перечитывался. */
    public record Entry(String name, String ext, long size, long mtime, boolean supported) {
        public String key() {
            return name + '|' + size + '|' + mtime;
        }
    }

    public static final long MAX_FILE_BYTES = 32L * 1024 * 1024;
    private static final Set<String> KNOWN = Set.of("ogg", "wav", "mp3");
    private static final Set<String> PLAYABLE = Set.of("ogg", "wav");

    private static volatile List<Entry> entries = List.of();

    private CritSoundLibrary() {}

    public static Path dir() {
        return FabricLoader.getInstance().getConfigDir().resolve("SpotHelper").resolve("crit_sounds");
    }

    /** Создаёт папку, если её нет. */
    public static boolean ensureDir() {
        try {
            Files.createDirectories(dir());
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    public static List<Entry> entries() {
        return entries;
    }

    public static Entry find(String name) {
        if (name == null || name.isEmpty()) return null;
        for (Entry e : entries) if (e.name().equals(name)) return e;
        return null;
    }

    /** Пересканирует папку. Вызывать из главного потока по кнопке или при старте. */
    public static List<Entry> refresh() {
        ensureDir();
        List<Entry> out = new ArrayList<>();
        try (Stream<Path> s = Files.list(dir())) {
            s.filter(Files::isRegularFile).forEach(p -> {
                String name = p.getFileName().toString();
                String ext = extOf(name);
                if (!KNOWN.contains(ext)) return;
                try {
                    out.add(new Entry(name, ext, Files.size(p), Files.getLastModifiedTime(p).toMillis(),
                            PLAYABLE.contains(ext)));
                } catch (IOException ignored) {
                }
            });
        } catch (IOException ignored) {
        }
        out.sort(Comparator.comparing(e -> e.name().toLowerCase(Locale.ROOT)));
        entries = List.copyOf(out);
        CritSoundPlayer.onLibraryRefreshed(entries);
        return entries;
    }

    /** Безопасный путь к файлу внутри папки библиотеки (защита от «../»). */
    public static Path resolve(String name) {
        if (name == null || name.isEmpty()) return null;
        Path d = dir().toAbsolutePath().normalize();
        Path p = d.resolve(name).normalize();
        return p.getParent() != null && p.getParent().equals(d) ? p : null;
    }

    /** Копирует выбранный пользователем файл в библиотеку (перетаскивание в окно). */
    public static String importFile(Path src) throws IOException {
        if (src == null || !Files.isRegularFile(src)) throw new IOException("Это не файл");
        String ext = extOf(src.getFileName().toString());
        if (!KNOWN.contains(ext)) throw new IOException("Нужен .ogg или .wav");
        if (!PLAYABLE.contains(ext)) throw new IOException("MP3 не поддерживается, конвертируйте в OGG");
        if (Files.size(src) > MAX_FILE_BYTES) throw new IOException("Файл больше 32 МБ");
        ensureDir();
        String base = src.getFileName().toString().replaceAll("[\\\\/:*?\"<>|]", "_");
        String stem = base.substring(0, base.length() - ext.length() - 1);
        Path dst = dir().resolve(base);
        for (int i = 1; Files.exists(dst); i++) dst = dir().resolve(stem + " (" + i + ")." + ext);
        Files.copy(src, dst, StandardCopyOption.COPY_ATTRIBUTES);
        refresh();
        return dst.getFileName().toString();
    }

    public static boolean delete(String name) {
        Path p = resolve(name);
        if (p == null) return false;
        try {
            boolean ok = Files.deleteIfExists(p);
            refresh();
            return ok;
        } catch (IOException e) {
            return false;
        }
    }

    static String extOf(String name) {
        int i = name.lastIndexOf('.');
        return i < 0 ? "" : name.substring(i + 1).toLowerCase(Locale.ROOT);
    }
}
