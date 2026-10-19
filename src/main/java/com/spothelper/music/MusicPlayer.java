package com.spothelper.music;

import com.spothelper.config.SpotHelperConfig;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.SourceDataLine;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Arrays;

/**
 * Single-track player. Потоковое воспроизведение через SourceDataLine в отдельном потоке.
 * Раньше использовался Clip: при лагах игры его внутренний буфер "зацикливался" и повторял
 * последние 2-3 секунды. Здесь при нехватке данных будет в худшем случае короткая тишина, но не повтор.
 */
public final class MusicPlayer {
    /** Размер буфера линии в секундах. Если всё ещё заикается на слабом ПК, поставь 1.0f. */
    private static final float BUFFER_SECONDS = 0.5f;

    private static final class Session {
        final Path path;
        volatile boolean cancelled;
        volatile boolean paused;
        volatile boolean started;
        volatile SourceDataLine line;
        volatile float sampleRate = 44100f;
        volatile long durationMs;
        final ArrayDeque<Long> loopStarts = new ArrayDeque<>();
        Session(Path path) { this.path = path; }
    }

    private static volatile Session session;
    private static volatile Path current;
    private static volatile String error = "";
    private static volatile boolean loading;
    private static volatile float volume = Math.max(0f, Math.min(1f, SpotHelperConfig.INSTANCE.musicVolume));

    private MusicPlayer() {}

    public static synchronized void play(Path path) {
        stop();
        Session s = new Session(path);
        session = s;
        current = path;
        error = "";
        loading = true;
        Thread t = new Thread(() -> run(s), "SpotHelper-Music");
        t.setDaemon(true);
        t.setPriority(Thread.NORM_PRIORITY + 2);
        t.start();
    }

    private static AudioInputStream openDecoded(Path path) throws Exception {
        AudioInputStream source = AudioSystem.getAudioInputStream(path.toFile());
        try {
            AudioFormat base = source.getFormat();
            int ch = Math.max(1, base.getChannels());
            AudioFormat pcm = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, base.getSampleRate(), 16,
                    ch, ch * 2, base.getSampleRate(), false);
            return AudioSystem.getAudioInputStream(pcm, source);
        } catch (Exception ex) {
            source.close();
            throw ex;
        }
    }

    private static long probeDurationMs(Path p) {
        try {
            AudioFileFormat aff = AudioSystem.getAudioFileFormat(p.toFile());
            Object d = aff.properties().get("duration");
            if (d instanceof Long us && us > 0) return us / 1000;
            float rate = aff.getFormat().getFrameRate();
            if (aff.getFrameLength() > 0 && rate > 0) return (long) (aff.getFrameLength() * 1000.0 / rate);
        } catch (Throwable ignored) {}
        return 0;
    }

    private static void run(Session s) {
        SourceDataLine line = null;
        AudioInputStream in = null;
        try {
            Path path = s.path;
            if (path == null || !Files.isRegularFile(path)) throw new IOException("Файл не найден");
            s.durationMs = probeDurationMs(path);

            in = openDecoded(path);
            AudioFormat fmt = in.getFormat();
            s.sampleRate = fmt.getSampleRate();
            if (s.durationMs <= 0 && in.getFrameLength() > 0) {
                s.durationMs = (long) (in.getFrameLength() * 1000.0 / fmt.getSampleRate());
            }
            int frameSize = fmt.getFrameSize();
            int bufBytes = Math.max(1, (int) (fmt.getSampleRate() * BUFFER_SECONDS)) * frameSize;

            line = (SourceDataLine) AudioSystem.getLine(new DataLine.Info(SourceDataLine.class, fmt, bufBytes));
            line.open(fmt, bufBytes);
            s.line = line;
            if (s.cancelled) return;

            byte[] buf = new byte[frameSize * 2048];
            long writtenFrames = 0;
            long passFrames = 0;

            while (!s.cancelled) {
                int n = in.read(buf, 0, buf.length);
                if (n <= 0) {
                    in.close();
                    if (passFrames == 0) throw new IOException("Пустой аудиофайл");
                    if (!SpotHelperConfig.INSTANCE.isMusicLoop()) break;
                    synchronized (s.loopStarts) { s.loopStarts.add(writtenFrames); }
                    passFrames = 0;
                    in = openDecoded(path);
                    continue;
                }
                n -= n % frameSize;
                if (n <= 0) continue;
                scale(buf, n, volume);

                int off = 0;
                while (off < n && !s.cancelled) {
                    int w = line.write(buf, off, n - off);
                    if (w <= 0) break;
                    off += w;
                }
                long frames = n / frameSize;
                writtenFrames += frames;
                passFrames += frames;

                if (!s.started && !s.cancelled) {
                    line.start();
                    s.started = true;
                    synchronized (MusicPlayer.class) { if (session == s) loading = false; }
                }
            }

            if (!s.cancelled) {
                if (!s.started) { line.start(); s.started = true; }
                line.drain();
                synchronized (MusicPlayer.class) {
                    if (session == s) { session = null; current = null; loading = false; }
                }
            }
        } catch (Throwable ex) {
            if (!s.cancelled) {
                synchronized (MusicPlayer.class) {
                    if (session == s) {
                        error = "Не удалось открыть файл: " + (ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage());
                        session = null;
                        current = null;
                        loading = false;
                    }
                }
            }
        } finally {
            try { if (in != null) in.close(); } catch (Throwable ignored) {}
            if (line != null) {
                try { line.stop(); line.flush(); line.close(); } catch (Throwable ignored) {}
            }
            synchronized (MusicPlayer.class) { if (session == s) loading = false; }
        }
    }

    /** Программная громкость для 16-bit little-endian PCM (линейная, как раньше 20*log10(volume) дБ). */
    private static void scale(byte[] b, int n, float v) {
        if (v >= 0.999f) return;
        if (v <= 0.0001f) { Arrays.fill(b, 0, n, (byte) 0); return; }
        for (int i = 0; i + 1 < n; i += 2) {
            int sample = (short) ((b[i] & 0xff) | (b[i + 1] << 8));
            sample = Math.round(sample * v);
            b[i] = (byte) sample;
            b[i + 1] = (byte) (sample >> 8);
        }
    }

    public static synchronized void pause() {
        Session s = session;
        if (s != null && s.started && !s.paused && s.line != null) {
            s.paused = true;
            s.line.stop();
        }
    }

    public static synchronized void resume() {
        Session s = session;
        if (s != null && s.started && s.paused && s.line != null) {
            s.paused = false;
            s.line.start();
        }
    }

    public static synchronized void stop() {
        Session s = session;
        session = null;
        current = null;
        loading = false;
        if (s != null) {
            s.cancelled = true;
            SourceDataLine l = s.line;
            if (l != null) {
                try { l.stop(); l.flush(); l.close(); } catch (Throwable ignored) {}
            }
        }
    }

    public static synchronized void setVolume(float value) {
        volume = Math.max(0, Math.min(1, value));
        SpotHelperConfig.INSTANCE.musicVolume = volume;
        SpotHelperConfig.INSTANCE.save();
    }

    /** Громкость во время перетаскивания ползунка: применяется сразу, конфиг не пишется (запись — setVolume по отпусканию). */
    public static synchronized void setVolumeLive(float value) {
        volume = Math.max(0, Math.min(1, value));
    }

    public static float volume() { return volume; }
    public static Path current() { return current; }
    public static String error() { return error; }
    public static boolean loading() { return loading; }

    public static boolean playing() {
        Session s = session;
        return s != null && s.started && !s.paused;
    }

    public static boolean paused() {
        Session s = session;
        return s != null && s.started && s.paused;
    }

    public static long positionMillis() {
        Session s = session;
        if (s == null || s.line == null || !s.started) return 0;
        try {
            long played = s.line.getLongFramePosition();
            long base = 0;
            synchronized (s.loopStarts) {
                for (long b : s.loopStarts) if (b <= played) base = b;
            }
            return (long) ((played - base) * 1000.0 / s.sampleRate);
        } catch (Throwable ex) {
            return 0;
        }
    }

    public static long durationMillis() {
        Session s = session;
        return s == null ? 0 : s.durationMs;
    }
}