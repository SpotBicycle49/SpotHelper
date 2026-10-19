package com.spothelper.critsounds;

import org.lwjgl.stb.STBVorbis;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.system.libc.LibCStdlib;

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
 * Декодирование и воспроизведение пользовательских звуков.
 * OGG декодируется через STBVorbis (уже есть в LWJGL, поставляемом с Minecraft), WAV — через javax.sound.
 * Звук идёт через собственную линию Java Sound и не занимает источники OpenAL игры.
 * Всё тяжёлое (чтение, декодирование, открытие линии) выполняется в отдельном потоке.
 */
public final class CritSoundPlayer {
    public record Decoded(int sampleRate, int channels, short[] pcm) {
        public long millis() {
            return pcm.length / Math.max(1, channels) * 1000L / Math.max(1, sampleRate);
        }
    }

    private static final long MAX_MILLIS = 60_000;
    private static final int MAX_CACHE_BYTES = 64 * 1024 * 1024;
    private static final int MAX_CRIT_CLIPS = 6;

    private static final ExecutorService EXEC = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "SpotHelper-CritAudio");
        t.setDaemon(true);
        return t;
    });

    private static final Object LOCK = new Object();
    /** LRU-кэш декодированного PCM, ключ — Entry.key(). Доступ только под synchronized(CACHE). */
    private static final Map<String, Decoded> CACHE = new LinkedHashMap<>(16, 0.75f, true);
    private static int cacheBytes;
    /** Ключ записи -> текст ошибки. Очищается при обновлении библиотеки. */
    private static final Map<String, String> ERRORS = new ConcurrentHashMap<>();
    private static final Set<String> LOADING = ConcurrentHashMap.newKeySet();

    private static final List<Clip> CRIT_CLIPS = new ArrayList<>();
    private static Clip previewClip;
    private static volatile String previewName;
    private static final AtomicLong PREVIEW_TOKEN = new AtomicLong();

    private CritSoundPlayer() {}

    // ------------------------------------------------------------------ состояние для GUI и логики

    public static Decoded cached(CritSoundLibrary.Entry e) {
        synchronized (CACHE) {
            return CACHE.get(e.key());
        }
    }

    public static String error(CritSoundLibrary.Entry e) {
        return ERRORS.get(e.key());
    }

    public static boolean isLoading(CritSoundLibrary.Entry e) {
        return LOADING.contains(e.key());
    }

    public static String previewing() {
        return previewName;
    }

    static void onLibraryRefreshed(List<CritSoundLibrary.Entry> list) {
        Set<String> keys = new HashSet<>();
        for (CritSoundLibrary.Entry e : list) keys.add(e.key());
        ERRORS.clear(); // повреждённый файл могли заменить — даём второй шанс
        synchronized (CACHE) {
            CACHE.entrySet().removeIf(en -> {
                if (keys.contains(en.getKey())) return false;
                cacheBytes -= en.getValue().pcm().length * 2;
                return true;
            });
        }
    }

    // ------------------------------------------------------------------ загрузка

    /** Асинхронно декодирует файл в кэш (если ещё не там). Ошибка попадает в error(). */
    public static void preload(CritSoundLibrary.Entry e) {
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

    private static Decoded decodeCached(CritSoundLibrary.Entry e) throws IOException {
        Decoded d = cached(e);
        if (d != null) return d;
        String known = ERRORS.get(e.key());
        if (known != null) throw new IOException(known);
        try {
            Path p = CritSoundLibrary.resolve(e.name());
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

    private static Decoded decodeOgg(Path p) throws IOException {
        if (Files.size(p) > CritSoundLibrary.MAX_FILE_BYTES) throw new IOException("Файл больше 32 МБ");
        byte[] raw = Files.readAllBytes(p);
        ByteBuffer in = MemoryUtil.memAlloc(raw.length);
        try {
            in.put(raw).flip();
            try (MemoryStack st = MemoryStack.stackPush()) {
                IntBuffer ch = st.mallocInt(1);
                IntBuffer sr = st.mallocInt(1);
                ShortBuffer pcm = STBVorbis.stb_vorbis_decode_memory(in, ch, sr);
                if (pcm == null) throw new IOException("Не удалось декодировать OGG (нужен Vorbis)");
                try {
                    int channels = ch.get(0);
                    int rate = sr.get(0);
                    if (channels < 1 || channels > 2) throw new IOException("Поддерживается только моно и стерео");
                    short[] out = new short[pcm.remaining()];
                    pcm.get(out);
                    Decoded d = new Decoded(rate, channels, out);
                    if (out.length == 0) throw new IOException("Файл пуст");
                    if (d.millis() > MAX_MILLIS) throw new IOException("Слишком длинный файл (максимум 60 с)");
                    return d;
                } finally {
                    LibCStdlib.free(pcm);
                }
            }
        } finally {
            MemoryUtil.memFree(in);
        }
    }

    private static Decoded decodeWav(Path p) throws IOException {
        if (Files.size(p) > CritSoundLibrary.MAX_FILE_BYTES) throw new IOException("Файл больше 32 МБ");
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

    /** Звук крита. Файл должен быть уже в кэше; возвращает false, если играть нечего. */
    public static boolean playCrit(CritSoundLibrary.Entry e, float volume) {
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
                            CRIT_CLIPS.remove(c);
                        }
                    }
                });
                synchronized (LOCK) {
                    CRIT_CLIPS.add(c);
                    while (CRIT_CLIPS.size() > MAX_CRIT_CLIPS) closeQuiet(CRIT_CLIPS.remove(0));
                }
                c.start();
            } catch (Throwable ignored) {
                // нет аудиоустройства или линии — игра продолжает работать без звука
            }
        });
        return true;
    }

    /** Прослушивание. Одновременно звучит не больше одного тестового файла. */
    public static void startPreview(CritSoundLibrary.Entry e, float volume) {
        long token = PREVIEW_TOKEN.incrementAndGet();
        previewName = e.name();
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
                                if (token == PREVIEW_TOKEN.get()) previewName = null;
                            }
                        }
                    }
                });
                c.start();
            } catch (Throwable t) {
                if (token == PREVIEW_TOKEN.get()) previewName = null;
            }
        });
    }

    public static void stopPreview() {
        PREVIEW_TOKEN.incrementAndGet();
        previewName = null;
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
        previewName = null;
        List<Clip> all;
        synchronized (LOCK) {
            all = new ArrayList<>(CRIT_CLIPS);
            CRIT_CLIPS.clear();
            if (previewClip != null) all.add(previewClip);
            previewClip = null;
        }
        for (Clip c : all) closeQuiet(c);
        EXEC.shutdown();
    }
}
