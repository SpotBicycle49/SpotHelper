package com.spothelper.critsounds;

import org.lwjgl.stb.STBVorbis;
import org.lwjgl.stb.STBVorbisInfo;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.LineEvent;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Декодирование и воспроизведение пользовательских звуков (общее для обоих каналов).
 * OGG декодируется через STBVorbis (уже есть в LWJGL, поставляемом с Minecraft), WAV — через javax.sound.
 * Звук идёт через собственную линию Java Sound и не занимает источники OpenAL игры.
 * Всё тяжёлое (чтение, декодирование, открытие линии) выполняется в отдельном потоке.
 * Тестовое прослушивание одно на оба канала: запуск нового останавливает предыдущее.
 */
public final class SoundPlayer {
    public record Decoded(int sampleRate, int channels, short[] pcm) {
        public long millis() {
            return pcm.length / Math.max(1, channels) * 1000L / Math.max(1, sampleRate);
        }
    }

    private static final long MAX_MILLIS = 60_000;
    private static final int MAX_CACHE_BYTES = 64 * 1024 * 1024;
    private static final int MAX_GAME_CLIPS = 8;

    private static final ExecutorService EXEC = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "SpotHelper-SoundAudio");
        t.setDaemon(true);
        return t;
    });

    private static final Object LOCK = new Object();
    /** LRU-кэш декодированного PCM, ключ — Entry.key(). Доступ только под synchronized(CACHE). */
    private static final Map<String, Decoded> CACHE = new LinkedHashMap<>(16, 0.75f, true);
    private static int cacheBytes;
    /** Ключ записи -> текст ошибки. Для папки очищается при обновлении её библиотеки. */
    private static final Map<String, String> ERRORS = new ConcurrentHashMap<>();
    private static final Set<String> LOADING = ConcurrentHashMap.newKeySet();

    private static final List<Clip> GAME_CLIPS = new ArrayList<>();
    private static Clip previewClip;
    private static volatile String previewKey;
    private static final AtomicLong PREVIEW_TOKEN = new AtomicLong();

    private SoundPlayer() {}

    // ------------------------------------------------------------------ состояние для GUI и логики

    public static Decoded cached(SoundLibrary.Entry e) {
        synchronized (CACHE) {
            return CACHE.get(e.key());
        }
    }

    public static String error(SoundLibrary.Entry e) {
        return ERRORS.get(e.key());
    }

    public static boolean isLoading(SoundLibrary.Entry e) {
        return LOADING.contains(e.key());
    }

    public static boolean isPreviewing(SoundLibrary.Entry e) {
        return e.key().equals(previewKey);
    }

    /** @param dirPrefix абсолютный путь папки библиотеки; чужие папки не затрагиваются */
    static void onLibraryRefreshed(String dirPrefix, List<SoundLibrary.Entry> list) {
        Set<String> keys = new HashSet<>();
        for (SoundLibrary.Entry e : list) keys.add(e.key());
        ERRORS.keySet().removeIf(k -> k.startsWith(dirPrefix)); // файл могли заменить — даём второй шанс
        synchronized (CACHE) {
            CACHE.entrySet().removeIf(en -> {
                if (!en.getKey().startsWith(dirPrefix) || keys.contains(en.getKey())) return false;
                cacheBytes -= en.getValue().pcm().length * 2;
                return true;
            });
        }
    }

    // ------------------------------------------------------------------ загрузка

    /** Асинхронно декодирует файл в кэш (если ещё не там). Ошибка попадает в error(). */
    public static void preload(SoundLibrary.Entry e) {
        if (e == null || !e.supported() || cached(e) != null || ERRORS.containsKey(e.key())) return;
        if (!LOADING.add(e.key())) return;
        EXEC.execute(() -> {
            try {
                decodeCached(e);
            } catch (Throwable t) {
                // ошибка уже записана в decodeCached
            } finally {
                LOADING.remove(e.key());
            }
        });
    }

    private static Decoded decodeCached(SoundLibrary.Entry e) throws IOException {
        Decoded d = cached(e);
        if (d != null) return d;
        String known = ERRORS.get(e.key());
        if (known != null) throw new IOException(known);
        try {
            Path p = e.path();
            if (p == null || !Files.isRegularFile(p)) throw new IOException("Файл не найден");
            d = switch (e.ext()) {
                case "ogg" -> decodeOgg(p);
                case "wav" -> decodeWav(p);
                case "mp3" -> throw new IOException("MP3 не поддерживается, конвертируйте в OGG");
                default -> throw new IOException("Неизвестный формат");
            };
        } catch (IOException | RuntimeException ex) {
            String msg = ex.getMessage() == null ? "Файл повреждён" : ex.getMessage();
            ERRORS.put(e.key(), msg);
            throw ex instanceof IOException io ? io : new IOException(msg, ex);
        }
        synchronized (CACHE) {
            CACHE.put(e.key(), d);
            cacheBytes += d.pcm().length * 2;
            var it = CACHE.entrySet().iterator();
            while (cacheBytes > MAX_CACHE_BYTES && it.hasNext()) {
                var en = it.next();
                if (en.getValue() == d) continue;
                cacheBytes -= en.getValue().pcm().length * 2;
                it.remove();
            }
        }
        return d;
    }

    /**
     * OGG через потоковый API STBVorbis: PCM копируется в наш буфер (memAlloc/memFree), память декодера
     * закрывается stb_vorbis_close. Никакого ручного free() чужой памяти: stb_vorbis_decode_memory
     * возвращает буфер аллокатора LWJGL, и освобождение его libc-free роняет JVM (куча повреждена).
     */
    private static Decoded decodeOgg(Path p) throws IOException {
        if (Files.size(p) > SoundLibrary.MAX_FILE_BYTES) throw new IOException("Файл больше 32 МБ");
        byte[] raw = Files.readAllBytes(p);
        ByteBuffer in = MemoryUtil.memAlloc(raw.length);
        try {
            in.put(raw).flip();
            try (MemoryStack st = MemoryStack.stackPush()) {
                IntBuffer err = st.mallocInt(1);
                long handle = STBVorbis.stb_vorbis_open_memory(in, err, null);
                if (handle == MemoryUtil.NULL) throw new IOException("Не удалось открыть OGG (нужен Vorbis)");
                try {
                    STBVorbisInfo info = STBVorbisInfo.malloc(st);
                    STBVorbis.stb_vorbis_get_info(handle, info);
                    int channels = info.channels();
                    int rate = info.sample_rate();
                    if (channels < 1 || channels > 2) throw new IOException("Поддерживается только моно и стерео");
                    if (rate < 8000 || rate > 192000) throw new IOException("Неподдерживаемая частота дискретизации");
                    int frames = STBVorbis.stb_vorbis_stream_length_in_samples(handle);
                    if (frames <= 0) throw new IOException("Файл пуст или повреждён");
                    if (frames * 1000L / rate > MAX_MILLIS) throw new IOException("Слишком длинный файл (максимум 60 с)");
                    int total = frames * channels;
                    ShortBuffer pcm = MemoryUtil.memAllocShort(total);
                    try {
                        int got = STBVorbis.stb_vorbis_get_samples_short_interleaved(handle, channels, pcm);
                        if (got <= 0) throw new IOException("Не удалось декодировать OGG");
                        short[] out = new short[Math.min(total, got * channels)];
                        pcm.position(0);
                        pcm.get(out);
                        return new Decoded(rate, channels, out);
                    } finally {
                        MemoryUtil.memFree(pcm);
                    }
                } finally {
                    STBVorbis.stb_vorbis_close(handle);
                }
            }
        } finally {
            MemoryUtil.memFree(in);
        }
    }

    private static Decoded decodeWav(Path p) throws IOException {
        if (Files.size(p) > SoundLibrary.MAX_FILE_BYTES) throw new IOException("Файл больше 32 МБ");
        try (AudioInputStream raw = AudioSystem.getAudioInputStream(p.toFile())) {
            AudioFormat f = raw.getFormat();
            AudioInputStream pcm = raw;
            if (f.getEncoding() != AudioFormat.Encoding.PCM_SIGNED || f.getSampleSizeInBits() != 16 || f.isBigEndian()) {
                AudioFormat target = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, f.getSampleRate(), 16,
                        f.getChannels(), f.getChannels() * 2, f.getSampleRate(), false);
                pcm = AudioSystem.getAudioInputStream(target, raw);
            }
            int channels = pcm.getFormat().getChannels();
            if (channels < 1 || channels > 2) throw new IOException("Поддерживается только моно и стерео");
            int rate = Math.round(pcm.getFormat().getSampleRate());
            long maxBytes = MAX_MILLIS * rate / 1000L * channels * 2L;
            byte[] b = pcm.readNBytes((int) Math.min(Integer.MAX_VALUE - 8, maxBytes + 1));
            if (b.length > maxBytes) throw new IOException("Слишком длинный файл (максимум 60 с)");
            if (b.length < 2) throw new IOException("Файл пуст");
            short[] out = new short[b.length / 2];
            for (int i = 0; i < out.length; i++) out[i] = (short) ((b[2 * i] & 0xFF) | (b[2 * i + 1] << 8));
            return new Decoded(rate, channels, out);
        } catch (javax.sound.sampled.UnsupportedAudioFileException | IllegalArgumentException ex) {
            throw new IOException("Неподдерживаемый или повреждённый WAV");
        }
    }

    // ------------------------------------------------------------------ воспроизведение

    private static Clip openClip(Decoded d, float volume) throws Exception {
        float v = Math.max(0f, Math.min(1f, volume));
        short[] s = d.pcm();
        byte[] out = new byte[s.length * 2];
        for (int i = 0; i < s.length; i++) {
            int x = Math.max(-32768, Math.min(32767, Math.round(s[i] * v)));
            out[2 * i] = (byte) x;
            out[2 * i + 1] = (byte) (x >> 8);
        }
        AudioFormat fmt = new AudioFormat(d.sampleRate(), 16, d.channels(), true, false);
        Clip clip = AudioSystem.getClip();
        clip.open(fmt, out, 0, out.length);
        return clip;
    }

    private static void closeQuiet(Clip c) {
        if (c == null) return;
        try {
            c.stop();
        } catch (Throwable ignored) {
        }
        try {
            c.close();
        } catch (Throwable ignored) {
        }
    }

    /** Звук удара/крита. Файл должен быть уже в кэше; возвращает false, если играть нечего. */
    public static boolean playGame(SoundLibrary.Entry e, float volume) {
        Decoded d = cached(e);
        if (d == null) {
            preload(e);
            return false;
        }
        EXEC.execute(() -> {
            try {
                Clip c = openClip(d, volume);
                c.addLineListener(ev -> {
                    if (ev.getType() == LineEvent.Type.STOP) {
                        closeQuiet(c);
                        synchronized (LOCK) {
                            GAME_CLIPS.remove(c);
                        }
                    }
                });
                synchronized (LOCK) {
                    GAME_CLIPS.add(c);
                    while (GAME_CLIPS.size() > MAX_GAME_CLIPS) closeQuiet(GAME_CLIPS.remove(0));
                }
                c.start();
            } catch (Throwable ignored) {
                // нет аудиоустройства или линии — игра продолжает работать без звука
            }
        });
        return true;
    }

    /** Прослушивание. Одновременно звучит не больше одного тестового файла. */
    public static void startPreview(SoundLibrary.Entry e, float volume) {
        long token = PREVIEW_TOKEN.incrementAndGet();
        previewKey = e.key();
        EXEC.execute(() -> {
            try {
                Decoded d = decodeCached(e);
                if (token != PREVIEW_TOKEN.get()) return;
                Clip c = openClip(d, volume);
                synchronized (LOCK) {
                    if (token != PREVIEW_TOKEN.get()) {
                        closeQuiet(c);
                        return;
                    }
                    closeQuiet(previewClip);
                    previewClip = c;
                }
                c.addLineListener(ev -> {
                    if (ev.getType() == LineEvent.Type.STOP) {
                        closeQuiet(c);
                        synchronized (LOCK) {
                            if (previewClip == c) {
                                previewClip = null;
                                if (token == PREVIEW_TOKEN.get()) previewKey = null;
                            }
                        }
                    }
                });
                c.start();
            } catch (Throwable t) {
                if (token == PREVIEW_TOKEN.get()) previewKey = null;
            }
        });
    }

    public static void stopPreview() {
        PREVIEW_TOKEN.incrementAndGet();
        previewKey = null;
        EXEC.execute(() -> {
            Clip c;
            synchronized (LOCK) {
                c = previewClip;
                previewClip = null;
            }
            closeQuiet(c);
        });
    }

    /** Освобождает все линии. Вызывается при закрытии клиента. */
    public static void shutdown() {
        PREVIEW_TOKEN.incrementAndGet();
        previewKey = null;
        List<Clip> all;
        synchronized (LOCK) {
            all = new ArrayList<>(GAME_CLIPS);
            GAME_CLIPS.clear();
            if (previewClip != null) all.add(previewClip);
            previewClip = null;
        }
        for (Clip c : all) closeQuiet(c);
        EXEC.shutdown();
    }
}
