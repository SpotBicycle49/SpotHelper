package com.spothelper.music;

import net.fabricmc.loader.api.FabricLoader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

/** User-owned music files; never packaged into the mod jar. */
public final class MusicLibrary {
    private static final Set<String> FORMATS = Set.of("wav", "mp3", "aiff", "aif");
    private static final Path DIR = FabricLoader.getInstance().getConfigDir().resolve("SpotHelper").resolve("music");
    private MusicLibrary() {}
    public static Path dir() { return DIR; }
    public static void ensureDir() throws IOException { Files.createDirectories(DIR); }
    public static List<Path> scan() throws IOException {
        ensureDir();
        try (Stream<Path> stream = Files.list(DIR)) {
            return stream.filter(Files::isRegularFile)
                    .filter(p -> FORMATS.contains(ext(p)))
                    .sorted(Comparator.comparing(p -> p.getFileName().toString().toLowerCase(Locale.ROOT)))
                    .toList();
        }
    }
    public static String ext(Path p) {
        String n = p.getFileName().toString(); int i = n.lastIndexOf('.');
        return i < 0 ? "" : n.substring(i + 1).toLowerCase(Locale.ROOT);
    }
    public static boolean delete(Path p) throws IOException {
        Path normalized = p.toAbsolutePath().normalize();
        if (!normalized.getParent().equals(DIR.toAbsolutePath().normalize())) return false;
        return Files.deleteIfExists(normalized);
    }
}
