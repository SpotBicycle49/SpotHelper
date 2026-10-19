package com.spothelper.music;

import com.spothelper.gui.ui.UiChip;
import com.spothelper.gui.ui.UiDraw;
import com.spothelper.gui.ui.UiScreen;
import com.spothelper.gui.ui.UiSlider;
import com.spothelper.gui.ui.UiTheme;
import com.spothelper.gui.ui.UiTheme.Role;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;

import java.awt.Desktop;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * «Моя музыка» в общем стиле SpotHelper: каркас UiScreen (окно, заголовок, «‹ Назад», прокручиваемая область),
 * чипы, слайдер и цвета UiTheme. Плеер (MusicPlayer) статический и от экрана не зависит: закрытие окна
 * музыку не останавливает, повторное открытие ничего не инициализирует заново.
 *
 * Фон не размывается: UiScreen рисует затемнение сам и не вызывает Screen.render / renderBackground,
 * поэтому ошибка «Can only blur once per frame» здесь невозможна.
 */
public final class MusicScreen extends UiScreen {
    private static final int ROW_H = 22;
    private static final int KEY_SPACE = 32;
    private static final int KEY_DOWN = 264;
    private static final int KEY_UP = 265;

    /** Выбор и прокрутка сохраняются между открытиями окна. */
    private static String lastSelected = "";
    private static float lastScroll;

    private List<Path> tracks = new ArrayList<>();
    private int selected;
    private boolean confirmDelete;
    private boolean ensureVisible;
    private boolean doubleClick;
    private boolean shown;
    private int listOffset;
    private String status = "";
    private boolean statusOk = true;

    private final UiSlider volume = new UiSlider(0, 100, 1, 40);

    public MusicScreen(Screen parent) {
        super(parent, "Моя музыка");
        volume.format = v -> v + "%";
        volume.onLive = v -> MusicPlayer.setVolumeLive(v / 100f);
        volume.onCommit = v -> MusicPlayer.setVolume(v / 100f);
    }

    // ------------------------------------------------------------------ жизненный цикл

    @Override
    protected int maxWidth() {
        return 600;
    }

    @Override
    protected void afterInit() {
        rescan(false);
        if (!shown) { // первый показ: вернуть прокрутку; при смене размера окна прокрутка остаётся как есть
            shown = true;
            scroll = lastScroll;
        }
        ensureVisible = true;
    }

    @Override
    public void removed() {
        lastScroll = scroll;
        if (!tracks.isEmpty()) lastSelected = tracks.get(clampSel()).getFileName().toString();
        super.removed();
    }

    private int clampSel() {
        return Math.max(0, Math.min(selected, tracks.size() - 1));
    }

    private Path selectedPath() {
        return tracks.isEmpty() ? null : tracks.get(clampSel());
    }

    private void rescan(boolean announce) {
        String keep = tracks.isEmpty() ? lastSelected : tracks.get(clampSel()).getFileName().toString();
        try {
            tracks = MusicLibrary.scan();
            if (announce) setStatus("Библиотека обновлена, треков: " + tracks.size(), true);
        } catch (Exception e) {
            tracks = new ArrayList<>();
            setStatus("Не удалось прочитать папку: " + e.getMessage(), false);
        }
        selected = 0;
        for (int i = 0; i < tracks.size(); i++) {
            if (tracks.get(i).getFileName().toString().equals(keep)) {
                selected = i;
                break;
            }
        }
        confirmDelete = false;
    }

    private void setStatus(String text, boolean ok) {
        status = text;
        statusOk = ok;
    }

    // ------------------------------------------------------------------ действия (те же, что были в старом окне)

    private void select(int i) {
        if (tracks.isEmpty()) return;
        selected = Math.max(0, Math.min(i, tracks.size() - 1));
        confirmDelete = false;
        ensureVisible = true;
    }

    private void playSelected() {
        if (tracks.isEmpty()) {
            setStatus("Поместите .mp3, .wav или .aiff в папку музыки", false);
            return;
        }
        MusicPlayer.play(tracks.get(clampSel()));
    }

    /** Играет выбранный трек → пауза; на паузе → продолжить; выбран другой трек или стоп → играть выбранный. */
    private void togglePlay() {
        Path sel = selectedPath();
        Path cur = MusicPlayer.current();
        boolean same = sel != null && cur != null && sel.equals(cur);
        if (same && MusicPlayer.playing()) MusicPlayer.pause();
        else if (same && MusicPlayer.paused()) MusicPlayer.resume();
        else if (!MusicPlayer.loading() || !same) playSelected();
    }

    private void selectRelative(int d) {
        if (tracks.isEmpty()) return;
        select((selected + d + tracks.size()) % tracks.size());
        playSelected();
    }

    private void toggleLoop() {
        com.spothelper.config.SpotHelperConfig cfg = com.spothelper.config.SpotHelperConfig.INSTANCE;
        cfg.musicLoop = cfg.isMusicLoop() ? 0 : 1;
        cfg.save();
        if (MusicPlayer.current() != null) MusicPlayer.play(MusicPlayer.current()); // применить режим к текущему треку
    }

    private void openFolder() {
        try {
            MusicLibrary.ensureDir();
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().open(MusicLibrary.dir().toFile());
                setStatus("Папка открыта", true);
            } else {
                setStatus("Папка: " + MusicLibrary.dir(), false);
            }
        } catch (Exception e) {
            setStatus("Папка: " + MusicLibrary.dir(), false);
        }
    }

    private void askDelete() {
        if (tracks.isEmpty()) return;
        confirmDelete = true;
    }

    private void doDelete() {
        confirmDelete = false;
        Path p = selectedPath();
        if (p == null) return;
        try {
            if (MusicPlayer.current() != null && MusicPlayer.current().equals(p)) MusicPlayer.stop();
            MusicLibrary.delete(p);
            int keep = selected;
            tracks = MusicLibrary.scan();
            selected = Math.min(keep, Math.max(0, tracks.size() - 1));
            setStatus("Файл удалён", true);
        } catch (Exception e) {
            setStatus("Не удалось удалить файл: " + e.getMessage(), false);
        }
    }

    // ------------------------------------------------------------------ ввод

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        doubleClick = doubled;
        try {
            return super.mouseClicked(click, doubled);
        } finally {
            doubleClick = false;
        }
    }

    @Override
    protected boolean onKey(int key) {
        switch (key) {
            case KEY_SPACE -> togglePlay();
            case ENTER, KP_ENTER -> playSelected();
            case KEY_UP -> select(selected - 1);
            case KEY_DOWN -> select(selected + 1);
            default -> {
                return false;
            }
        }
        return true;
    }

    // ------------------------------------------------------------------ подвал: плеер

    private boolean narrow() {
        return ww < 420;
    }

    @Override
    protected int footerHeight() {
        return 84;
    }

    @Override
    protected void drawFooter(DrawContext ctx) {
        TextRenderer tr = textRenderer;
        int x = viewL;
        int w = viewR - viewL + 4;
        int top = viewB + 2;
        UiDraw.rect(ctx, wx + 1, top, ww - 2, 1, c(Role.CARD_BORDER));

        Path cur = MusicPlayer.current();
        long pos = MusicPlayer.positionMillis();
        long dur = MusicPlayer.durationMillis();
        boolean playing = MusicPlayer.playing();
        boolean paused = MusicPlayer.paused();

        String name = cur == null ? "Ничего не воспроизводится" : cur.getFileName().toString();
        String timeText = (dur > 0 ? time(pos) + " / " + time(dur) : (cur == null ? "--:-- / --:--" : time(pos) + " / --:--"));
        int timeW = tr.getWidth(timeText);
        String state = MusicPlayer.loading() ? "загрузка… " : playing ? "● " : paused ? "пауза · " : "";
        int ty = top + 8;
        UiDraw.text(ctx, tr, UiDraw.fit(tr, state + name, w - timeW - 12), x, ty,
                c(cur == null ? Role.TEXT_DIM : Role.TEXT_MAIN));
        UiDraw.textRight(ctx, tr, timeText, x + w, ty, c(Role.TEXT_DIM));

        // полоса прогресса: длительность известна — заливка, иначе пустой трек
        int by = ty + 16;
        UiDraw.pill(ctx, x, by, w, 6, c(Role.SLIDER_TRACK));
        if (dur > 0 && cur != null) {
            int fw = (int) Math.round(Math.max(0, Math.min(1, pos / (double) dur)) * w);
            if (fw > 0) UiDraw.pill(ctx, x, by, Math.max(fw, 6), 6, c(Role.HIGHLIGHT));
        }

        // кнопки управления
        int cy = by + 14;
        boolean has = !tracks.isEmpty();
        String playLabel = playing && sameAsSelected() ? "Пауза" : paused && sameAsSelected() ? "Продолжить" : "Играть";
        String[] labels = {"Пред.", playLabel, "След.", "Стоп"};
        Runnable[] acts = {() -> selectRelative(-1), this::togglePlay, () -> selectRelative(1), MusicPlayer::stop};
        boolean[] en = {has, has || cur != null, has, cur != null};
        int cx = x;
        for (int i = 0; i < labels.length; i++) {
            int cw = Math.max(narrow() ? 44 : 58, UiChip.width(tr, labels[i]));
            UiChip.draw(ctx, tr, in, cx, cy, cw, 18, labels[i], i == 1 && (playing || paused), en[i], acts[i]);
            cx += cw + 4;
        }
        boolean loop = com.spothelper.config.SpotHelperConfig.INSTANCE.isMusicLoop();
        String loopLabel = narrow() ? "Повтор" : "Повтор трека: " + (loop ? "ВКЛ" : "ВЫКЛ");
        int lw = UiChip.width(tr, loopLabel);
        if (cx + lw <= x + w) {
            UiChip.draw(ctx, tr, in, x + w - lw, cy, lw, 18, loopLabel, loop, true, this::toggleLoop);
        }

        // громкость
        int vy = cy + 24;
        UiDraw.text(ctx, tr, "Громкость", x, vy + 3, c(Role.TEXT_DIM));
        int lx = x + tr.getWidth("Громкость") + 10;
        if (in.dragging != volume) volume.setValue(Math.round(MusicPlayer.volume() * 100f));
        volume.draw(ctx, tr, in, lx, vy, Math.max(60, x + w - lx), true);
    }

    private boolean sameAsSelected() {
        Path sel = selectedPath();
        Path cur = MusicPlayer.current();
        return sel != null && cur != null && sel.equals(cur);
    }

    // ------------------------------------------------------------------ тело: папка, действия, список

    @Override
    protected int drawBody(DrawContext ctx, int x, int top, int w) {
        TextRenderer tr = textRenderer;
        int y = top;

        UiDraw.text(ctx, tr, UiDraw.fit(tr, "Папка: config/SpotHelper/music", w), x, y + 2, c(Role.HIGHLIGHT));
        y += 14;
        UiDraw.text(ctx, tr, UiDraw.fit(tr, "MP3, WAV, AIFF · треков: " + tracks.size(), w), x, y + 1, c(Role.TEXT_DIM));
        y += 16;

        // действия над библиотекой
        if (confirmDelete && selectedPath() != null) {
            String q = "Удалить «" + selectedPath().getFileName() + "»?";
            UiDraw.text(ctx, tr, UiDraw.fit(tr, q, w), x, y + 2, c(Role.ERROR));
            y += 16;
            int yw = UiChip.width(tr, "Да, удалить");
            UiChip.draw(ctx, tr, in, x, y, yw, 18, "Да, удалить", true, true, this::doDelete);
            int nw = UiChip.width(tr, "Отмена");
            UiChip.draw(ctx, tr, in, x + yw + 6, y, nw, 18, "Отмена", false, true, () -> confirmDelete = false);
            y += 24;
        } else {
            String[] labels = {"Обновить библиотеку", "Открыть папку", "Удалить выбранный"};
            Runnable[] acts = {() -> rescan(true), this::openFolder, this::askDelete};
            boolean[] en = {true, true, !tracks.isEmpty()};
            y = flowChips(ctx, x, y, w, labels, acts, null, en);
        }

        // сообщения: ошибка плеера, загрузка, результат последнего действия
        String err = MusicPlayer.error();
        if (!err.isEmpty()) {
            for (String line : wrap(err, w, 2)) {
                UiDraw.text(ctx, tr, line, x, y + 1, c(Role.ERROR));
                y += 11;
            }
            y += 3;
        } else if (MusicPlayer.loading()) {
            UiDraw.text(ctx, tr, "Загрузка трека…", x, y + 1, c(Role.TEXT_DIM));
            y += 14;
        }
        if (!status.isEmpty()) {
            for (String line : wrap(status, w, 2)) {
                UiDraw.text(ctx, tr, line, x, y + 1, c(statusOk ? Role.OK : Role.ERROR));
                y += 11;
            }
            y += 3;
        }

        y += label(ctx, x, y, "Треки (" + tracks.size() + ")");
        listOffset = y - top;

        if (tracks.isEmpty()) {
            for (String line : wrap("Библиотека пуста. Добавьте файлы .mp3, .wav или .aiff в папку и нажмите «Обновить библиотеку».", w, 4)) {
                UiDraw.text(ctx, tr, line, x, y + 2, c(Role.TEXT_DIM));
                y += 11;
            }
            return y - top + 4;
        }

        if (ensureVisible) {
            ensureVisible = false;
            int rowTop = listOffset + clampSel() * ROW_H;
            int vh = viewB - viewT;
            if (rowTop < scroll) scroll = rowTop - ROW_H;
            else if (rowTop + ROW_H > scroll + vh) scroll = rowTop + ROW_H - vh + 4;
            scroll = Math.max(0, scroll);
        }

        Path cur = MusicPlayer.current();
        for (int i = 0; i < tracks.size(); i++) {
            int ry = y + i * ROW_H;
            if (ry + ROW_H < viewT || ry > viewB) continue; // за пределами видимой области не рисуем
            drawRow(ctx, x, ry, w, i, cur);
        }
        return y - top + tracks.size() * ROW_H + 4;
    }

    private void drawRow(DrawContext ctx, int x, int ry, int w, int i, Path cur) {
        TextRenderer tr = textRenderer;
        Path p = tracks.get(i);
        boolean sel = i == clampSel();
        boolean isCur = cur != null && cur.equals(p);
        boolean hov = in.mouseOver(x, ry, w, ROW_H - 2);

        int fill = c(sel ? Role.ACCENT : Role.CARD_BG);
        if (hov && in.down) fill = UiTheme.pressed(fill);
        else if (hov) fill = UiTheme.hover(fill);
        int border = c(sel || hov ? Role.HIGHLIGHT : Role.CARD_BORDER);
        UiDraw.box(ctx, x, ry, w, ROW_H - 2, border, fill);

        int textCol = sel ? UiTheme.onColor(fill) : c(isCur ? Role.HIGHLIGHT : Role.TEXT_MAIN);
        String tag = "";
        if (isCur) tag = MusicPlayer.loading() ? "загрузка…" : MusicPlayer.playing() ? "играет" : MusicPlayer.paused() ? "пауза" : "";
        int tagW = tag.isEmpty() ? 0 : tr.getWidth(tag) + 10;
        String name = (isCur ? "● " : "") + p.getFileName();
        UiDraw.text(ctx, tr, UiDraw.fit(tr, name, w - 14 - tagW), x + 7, ry + 7, textCol);
        if (!tag.isEmpty()) {
            UiDraw.textRight(ctx, tr, tag, x + w - 7, ry + 7, sel ? textCol : c(Role.TEXT_DIM));
        }
        final int idx = i;
        in.addClick(x, ry, w, ROW_H - 2, () -> {
            select(idx);
            if (doubleClick) playSelected();
        });
    }

    // ------------------------------------------------------------------ утилиты

    private static String time(long ms) {
        long sec = Math.max(0, ms / 1000);
        return String.format(java.util.Locale.ROOT, "%02d:%02d", sec / 60, sec % 60);
    }

    /** Перенос по словам, не больше maxLines строк (последняя обрезается «…»). */
    private List<String> wrap(String text, int maxW, int maxLines) {
        TextRenderer tr = textRenderer;
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (String word : text.split(" ")) {
            String trial = cur.length() == 0 ? word : cur + " " + word;
            if (tr.getWidth(trial) <= maxW || cur.length() == 0) {
                cur = new StringBuilder(trial);
            } else {
                out.add(cur.toString());
                cur = new StringBuilder(word);
            }
        }
        if (cur.length() > 0) out.add(cur.toString());
        if (out.size() > maxLines) {
            out = new ArrayList<>(out.subList(0, maxLines));
            out.set(maxLines - 1, text.length() > 0 ? out.get(maxLines - 1) + "…" : "");
        }
        List<String> fitted = new ArrayList<>();
        for (String s : out) fitted.add(UiDraw.fit(tr, s, maxW));
        return fitted;
    }
}
