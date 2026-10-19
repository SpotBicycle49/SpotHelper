package com.spothelper.gui;

import com.spothelper.critsounds.AttackSounds;
import com.spothelper.critsounds.SoundChannel;
import com.spothelper.gui.ui.UiChip;
import com.spothelper.gui.ui.UiDraw;
import com.spothelper.gui.ui.UiInput;
import com.spothelper.gui.ui.UiTheme;
import com.spothelper.gui.ui.UiTheme.Role;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

/** Окно-гайд «Звуки критов»: где взять звук и как его добавить. Закрывается по ESC или кнопке. */
public class CritSoundsGuideScreen extends Screen {
    private static final int ESC = 256;

    /** Строки, начинающиеся с «# », — заголовки. */
    private static String[] guideFor(SoundChannel ch) {
        String folder = ".minecraft/config/SpotHelper/" + ch.library.folder() + "/";
        return new String[]{
                "# 1. Где взять звук",
                "Нужен короткий звук (обычно 0,2–2 секунды). Скачайте его с сайта свободных звуков, например freesound.org "
                        + "(проверьте лицензию), или возьмите свой файл.",
                "Подходят форматы .ogg (кодек Vorbis) и .wav, моно или стерео, до 60 секунд и до 32 МБ.",
                "MP3 мод не воспроизводит. Сконвертируйте его в OGG: в Audacity «Файл → Экспорт → OGG» или командой "
                        + "ffmpeg -i sound.mp3 -c:a libvorbis sound.ogg. OGG с кодеком Opus не подойдёт.",
                "# 2. Как добавить файл",
                "Способ 1. Перетащите файл мышью прямо в это меню SpotHelper (блок «" + ch.title + "» должен быть "
                        + "раскрыт). Файл скопируется в библиотеку сам.",
                "Способ 2. Нажмите «Открыть папку» и скопируйте файл в открывшуюся папку. Если папка не открылась, "
                        + "путь будет скопирован в буфер обмена.",
                "Способ 3. Положите файл вручную в " + folder + " и нажмите «Обновить библиотеку». "
                        + "Перезапускать игру не нужно.",
                "# 3. Как включить",
                "1. Нажмите «Слушать», чтобы проверить звук.",
                "2. Нажмите «Выбрать» у нужного файла (в случайном режиме «В набор» у нескольких файлов).",
                "3. Включите переключатель «" + ch.title + "» в заголовке блока.",
                "4. Настройте громкость ползунком.",
                "# 4. Если звука нет",
                ch.hint(),
                "Если у файла написано «ошибка», он повреждён или в неподдерживаемом формате: сконвертируйте его заново и "
                        + "нажмите «Обновить библиотеку».",
                "Файл можно удалить кнопкой «×» (нажмите дважды для подтверждения)."
        };
    }

    private final Screen parent;
    private final SoundChannel channel;
    private final String[] guide;
    private final UiInput in = new UiInput();
    private float scroll;
    private int contentH;
    private int px, py, pw, ph, viewT, viewB;

    public CritSoundsGuideScreen(Screen parent, SoundChannel channel) {
        super(Text.literal(channel.title + " — гайд"));
        this.parent = parent;
        this.channel = channel;
        this.guide = guideFor(channel);
    }

    @Override
    protected void init() {
        in.down = false;
    }

    @Override
    public void close() {
        if (client != null) client.setScreen(parent);
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (input.key() == ESC) {
            close();
            return true;
        }
        return super.keyPressed(input);
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (click.button() == 0) {
            in.down = true;
            if (in.click(click.x(), click.y())) return true;
        }
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseReleased(Click click) {
        in.down = false;
        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int max = Math.max(0, contentH - (viewB - viewT));
        scroll = Math.max(0, Math.min(scroll - (float) (verticalAmount * 20), max));
        return true;
    }

    private static int c(Role r) {
        return UiTheme.get(r);
    }

    private List<String> wrap(String text, int maxW) {
        List<String> out = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            String cand = line.length() == 0 ? word : line + " " + word;
            if (line.length() > 0 && textRenderer.getWidth(cand) > maxW) {
                out.add(line.toString());
                line = new StringBuilder(word);
            } else {
                line = new StringBuilder(cand);
            }
        }
        if (line.length() > 0) out.add(line.toString());
        return out;
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        TextRenderer tr = textRenderer;
        in.begin(mouseX, mouseY);

        pw = Math.max(160, Math.min(560, width - 40));
        ph = Math.max(120, Math.min(420, height - 40));
        px = (width - pw) / 2;
        py = (height - ph) / 2;
        viewT = py + 30;
        viewB = py + ph - 34;

        ctx.fill(0, 0, width, height, UiTheme.scrim());
        UiDraw.rect(ctx, px, py, pw, ph, c(Role.WINDOW_BG));
        UiDraw.frame(ctx, px, py, pw, ph, c(Role.ACCENT));
        UiDraw.text(ctx, tr, channel.title + " — гайд", px + 12, py + 11, c(Role.HIGHLIGHT));
        UiDraw.rect(ctx, px + 1, py + 28, pw - 2, 1, c(Role.CARD_BORDER));

        int tx = px + 12;
        int tw = pw - 24 - 6;
        ctx.enableScissor(px + 1, viewT, px + pw - 1, viewB);
        int y = viewT + 6 - Math.round(scroll);
        int start = y;
        for (String raw : guide) {
            boolean head = raw.startsWith("# ");
            String text = head ? raw.substring(2) : raw;
            if (head) y += 4;
            for (String line : wrap(text, tw)) {
                UiDraw.text(ctx, tr, line, tx, y, c(head ? Role.HIGHLIGHT : Role.TEXT_MAIN));
                y += 11;
            }
            y += head ? 4 : 6;
        }
        contentH = y - start + 12;
        ctx.disableScissor();

        int max = Math.max(0, contentH - (viewB - viewT));
        if (scroll > max) scroll = max;

        UiDraw.rect(ctx, px + 1, viewB + 1, pw - 2, 1, c(Role.CARD_BORDER));
        int by = py + ph - 26;
        int closeW = UiChip.width(tr, "Закрыть");
        UiChip.draw(ctx, tr, in, px + pw - 12 - closeW, by, closeW, 18, "Закрыть", false, true, this::close);
        int openW = UiChip.width(tr, "Открыть папку");
        UiChip.draw(ctx, tr, in, px + pw - 12 - closeW - 6 - openW, by, openW, 18, "Открыть папку", false, true, () -> {
            if (!AttackSounds.openFolder(channel) && client != null && client.keyboard != null) {
                client.keyboard.setClipboard(channel.library.dir().toAbsolutePath().toString());
            }
        });
    }
}
