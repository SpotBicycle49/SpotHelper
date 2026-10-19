package com.spothelper.gui;

import com.spothelper.config.SpotHelperConfig;
import com.spothelper.critsounds.AttackSounds;
import com.spothelper.critsounds.SoundChannel;
import com.spothelper.critsounds.SoundLibrary;
import com.spothelper.critsounds.SoundPlayer;
import com.spothelper.gui.ui.UiCard;
import com.spothelper.gui.ui.UiChip;
import com.spothelper.gui.ui.UiColorRow;
import com.spothelper.gui.ui.UiDraw;
import com.spothelper.gui.ui.UiField;
import com.spothelper.gui.ui.UiInput;
import com.spothelper.gui.ui.UiScreen;
import com.spothelper.gui.ui.UiSlider;
import com.spothelper.gui.ui.UiTheme;
import com.spothelper.gui.ui.UiTheme.Role;
import com.spothelper.gui.ui.UiToggle;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.joml.Matrix3x2fStack;

import com.spothelper.minimap.MinimapSettingsScreen;
import com.spothelper.music.MusicScreen;
import com.spothelper.sellprotect.SellProtectScreen;
import com.spothelper.slotlock.SlotLockManager;
import com.spothelper.waypoints.WaypointScreen;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Objects;

/** Главное меню: боковая панель + карточки модулей. Все цвета берутся из UiTheme. */
public class SpotHelperScreen extends Screen {
    private static final int ESC = 256; // GLFW_KEY_ESCAPE
    private static final Role[] ROLES = Role.values();

    private enum Section {
        EVENTS("События", false),
        VOTES("Голосования", false),
        FUNCTIONS("Функции", false),
        VISUAL("Визуализация", false),
        SOUNDS("Звуки", false),
        MINIMAP("Миникарта", false),
        WAYPOINTS("Метки", false),
        BINDS("Бинды команд", false),
        FRIENDS("Друзья", false),
        HUD("HUD", false),
        THEME("Оформление", false);

        final String title;
        /** true — пункт не переключает раздел, а открывает отдельный экран. */
        final boolean external;

        Section(String title, boolean external) {
            this.title = title;
            this.external = external;
        }
    }

    private enum Module {
        GAMMA("Гамма", "Яркость"),
        SHULKER("Блок шалкера", "Не даёт выйти из игры с шалкерами в инвентаре"),
        HEALTH("Здоровье", "Полоска HP над сущностями и панель цели в HUD");

        final String title;
        final String desc;

        Module(String title, String desc) {
            this.title = title;
            this.desc = desc;
        }
    }

    private enum Feature {
        TOTEM_COLOR("Цвет тотемов", "Этернити, Инфинити, Стингер — по названию", 40),
        HUD("HUD", "Броня · эффекты · курс коина", 46),
        WAYPOINTS("Метки", "Точки в мире и на миникарте", 40),
        BINDS("Бинды команд", "Цикл вкл/выкл одной клавишей", 52),
        SELL("Защита продажи", "Предметы, которые нельзя продать", 40),
        ARMOR("Броня у ника", "Прочность брони игроков под ником", 40),
        SHULKER("Блок шалкера", "Не даёт выйти с шалкерами в инвентаре", 52),
        WP_WORLD("Метки в мире", "Маркер и луч сквозь стены, подпись", 40),
        HEALTH("Здоровье", "HP вторым ником под именем и в HUD", 40),
        REPLY("Автоответ", "Отвечает в чат по шаблонам", 40),
        ENTITY_HEIGHT("Высота сущностей", "Y рядом с именем на миникарте", 82),
        AUTO_ANARCHY("Автопереход", "На анархию при старте события", 40),
        GAMMA("Гамма", "Яркость", 58),
        FREE_LOOK("Свободная камера F5", "Удерживайте клавишу «Свободная камера» в F5", 40),
        ZOOM("Зум (OptiFine)", "Удерживайте Z, колёсико меняет приближение", 40),
        F5_ZOOM("Зум камеры F5", "Колёсико в F5 отдаляет/приближает камеру", 40),
        CROSSHAIR("Цвет прицела", "Красный на сущности, жёлтый на блоке", 40),
        HITBOXES("Хитбоксы", "Видны всегда, даже без F3+B", 40),
        AUTOSPRINT("Автоспринт", "Бежит при нажатии W без Ctrl", 40),
        TNT_TIMER("Таймер ТНТ", "Время до взрыва любых ТНТ, в т.ч. кастомных", 82),
        INVENTORY_HUD("Inventory HUD", "Основной инвентарь на экране, двигается в «HUD»", 82),
        MUSIC("Моя музыка", "Своя музыкальная библиотека и плеер", 40),
        FILTERS("Фильтры", "Списки враждебных, мирных мобов и предметов", 40);

        final String title;
        final String desc;
        final int height;

        Feature(String title, String desc, int height) {
            this.title = title;
            this.desc = desc;
            this.height = height;
        }
    }

    private final Screen parent;
    private final UiInput in = new UiInput();

    private final UiSlider gamma = new UiSlider(1, 16, 1, 28);
    private final UiSlider opacity = new UiSlider(40, 100, 1, 36);
    private static final String[] HEIGHT_MODES = {"всегда", "враждебные", "при Tab"};
    private final UiSlider shulkerAllowed = new UiSlider(0, 37, 1, 44);
    private final UiSlider tntRange = new UiSlider(4, 64, 4, 90);
    private final UiSlider invHudScale = new UiSlider(50, 200, 5, 90);

    /** Раскрытая функция в списке «Функции» (аккордеон: одновременно не больше одной). */
    private Feature openFeature;
    /** Функция, к строке которой надо прокрутить список после раскрытия. */
    private Feature pendingReveal;
    private final Map<Feature, UiScreen> featurePages = new EnumMap<>(Feature.class);
    private final UiSlider healthRange = new UiSlider(8, 64, 2, 48);

    /** Встроенные страницы разделов «Миникарта», «Метки», «Бинды команд», «HUD». */
    private final Map<Section, UiScreen> pages = new EnumMap<>(Section.class);
    private UiScreen activePage;
    private long lastPoll;
    private String eventFilterName; // null — все события

    /** Блок «Свапы в инвентаре» в разделе «Функции»: раскрыт ли и какой слот ждёт клавишу (-1 — никакой). */
    private boolean swapsExpanded;
    private int swapWaiting = -1;
    /** Блок «Блокировка слотов» в «Функциях»; одновременно раскрыт только один из двух блоков. */
    private boolean slotLockExpanded;
    private boolean damageTintExpanded;
    private boolean negFxExpanded;
    private final UiSlider tintAlpha = new UiSlider(5, 100, 5, 80);
    private final UiSlider tintDuration = new UiSlider(1, 40, 1, 80);
    private int slotLockSel = 0; // выбранный слот в сетке настроек

    private Section section = Section.EVENTS;
    private Module module;
    private float scroll;
    private int contentH;
    private int selectedRole = -1;
    private String serverFilter;
    private String voteEvent;
    private boolean eventSortByTime;

    // состояние демонстрационных элементов предпросмотра темы (на настройки не влияют)
    private boolean pvA = true;
    private boolean pvB = false;
    private int pvChip = 0;
    private int pvNav = 0;
    private final UiSlider pvSlider = new UiSlider(0, 100, 1, 0);

    private String toast = "";
    private boolean toastOk;
    private long toastUntil;

    // раскладка текущего кадра
    private int wx, wy, ww, wh, sideW, viewL, viewT, viewR, viewB;

    public SpotHelperScreen() {
        this(null);
    }

    public SpotHelperScreen(Screen parent) {
        super(Text.literal("SpotHelper"));
        this.parent = parent;


        gamma.onLive = MenuBridge::setGammaLevel;
        gamma.onCommit = v -> MenuBridge.save();

        pvSlider.setValue(60);

        tntRange.format = v -> "Подписи в радиусе: " + v + " бл.";
        tntRange.onLive = MenuBridge::setTntRange;
        tntRange.onCommit = v -> MenuBridge.save();

        invHudScale.format = v -> "Масштаб: " + v + "%";
        invHudScale.onLive = v -> SpotHelperConfig.INSTANCE.invHudScale = v;
        invHudScale.onCommit = v -> SpotHelperConfig.INSTANCE.save();

        healthRange.format = v -> v + " бл.";
        healthRange.onLive = MenuBridge::setHealthRange;
        healthRange.onCommit = v -> MenuBridge.save();

        shulkerAllowed.format = v -> v + " шт.";
        shulkerAllowed.onLive = MenuBridge::setShulkerAllowed;
        shulkerAllowed.onCommit = v -> MenuBridge.save();


        tintAlpha.format = v -> "Прозрачность: " + v + "%";
        tintAlpha.onLive = v -> { SpotHelperConfig.INSTANCE.damageTintAlpha = v / 100f; com.spothelper.damagetint.DamageTintManager.reapply(); };
        tintAlpha.onCommit = v -> SpotHelperConfig.INSTANCE.save();
        tintDuration.format = v -> "Длительность: " + v + " т.";
        tintDuration.onLive = v -> SpotHelperConfig.INSTANCE.damageTintDurationTicks = v;
        tintDuration.onCommit = v -> SpotHelperConfig.INSTANCE.save();

        for (SoundBlock sb : soundBlocks) {
            sb.volume.format = v -> "Громкость: " + v + "%";
            sb.volume.onLive = v -> sb.ch.setVolume(v / 100f);
            sb.volume.onCommit = v -> sb.ch.save();
        }

        opacity.format = v -> v + "%";
        opacity.onLive = UiTheme::setOpacity;
        opacity.onCommit = v -> UiTheme.save();
    }

    // ================================================================ жизненный цикл

    @Override
    protected void init() {
        // после возврата с другого экрана кнопка могла остаться «зажатой»
        in.down = false;
        in.dragging = null;
        attachPage();
        lastPoll = System.currentTimeMillis();
        MenuBridge.refresh();
    }

    @Override
    public void close() {
        if (client != null) client.setScreen(parent);
    }

    @Override
    public void removed() {
        SoundPlayer.stopPreview(); // тестовое прослушивание не должно жить дольше меню
        super.removed();
    }

    /** Перетаскивание .ogg/.wav в окно, когда раскрыт блок звуков, копирует файлы в его библиотеку. */
    @Override
    public void onFilesDropped(List<Path> paths) {
        SoundBlock target = null;
        for (SoundBlock sb : soundBlocks) if (sb.expanded) { target = sb; break; }
        if ((section != Section.FUNCTIONS && section != Section.SOUNDS) || target == null) {
            super.onFilesDropped(paths);
            return;
        }
        int ok = 0;
        String err = null;
        for (Path p : paths) {
            try {
                AttackSounds.importFile(target.ch, p);
                ok++;
            } catch (IOException e) {
                err = e.getMessage();
            }
        }
        if (ok > 0) toast("Добавлено файлов: " + ok + (err != null ? " (часть пропущена: " + err + ")" : ""), true);
        else toast(err != null ? err : "Файлы не добавлены", false);
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (activePage != null) {
            boolean typing = getFocused() instanceof TextFieldWidget;
            if (activePage.embeddedKey(input.key(), this)) return true;
            if (typing) return super.keyPressed(input);
        }
        if (input.key() == ESC) {
            if (module != null) {
                module = null;
                scroll = 0;
            } else if (openFeature != null) {
                closeFeature();
            } else {
                close();
            }
            return true;
        }
        return super.keyPressed(input);
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (click.button() == 0) {
            in.down = true;
            setFocused(null);
            UiSlider s = in.hitSlider(click.x(), click.y());
            if (s != null) {
                in.dragging = s;
                s.dragTo(click.x());
                return true;
            }
            if (in.click(click.x(), click.y())) return true;
        }
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseDragged(Click click, double offsetX, double offsetY) {
        if (in.dragging != null) {
            in.dragging.dragTo(click.x());
            return true;
        }
        return super.mouseDragged(click, offsetX, offsetY);
    }

    @Override
    public boolean mouseReleased(Click click) {
        in.down = false;
        if (in.dragging != null) {
            in.dragging.finish();
            in.dragging = null;
            return true;
        }
        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int maxScroll = Math.max(0, contentH - (viewB - viewT));
        scroll = Math.max(0, Math.min(scroll - (float) (verticalAmount * 20), maxScroll));
        return true;
    }

    // ================================================================ раскладка и каркас

    private static int c(Role r) {
        return UiTheme.get(r);
    }

    private void layout() {
        ww = Math.max(160, Math.min(900, width - 40));
        wh = Math.max(120, Math.min(520, height - 40));
        wx = (width - ww) / 2;
        wy = (height - wh) / 2;
        sideW = Math.max(88, Math.min(150, ww / 4));
        viewL = wx + sideW + 12;
        viewR = wx + ww - 14;
        viewT = wy + 42;
        viewB = wy + wh - 10 - (module != null ? 30 : 0);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        long interval = MenuBridge.pollIntervalMs();
        long now = System.currentTimeMillis();
        if (interval > 0 && now - lastPoll >= interval) {
            lastPoll = now;
            MenuBridge.refresh();
        }
        in.begin(mouseX, mouseY);
        layout();

        ctx.fill(0, 0, width, height, UiTheme.scrim());
        final int wr = WIN_RADIUS;
        UiDraw.shadow(ctx, wx, wy, ww, wh, wr);
        UiDraw.roundRect(ctx, wx, wy, ww, wh, wr, c(Role.ACCENT)); // рамка
        int winBg = c(Role.WINDOW_BG);
        int sideBg = c(Role.SIDEBAR_BG);
        // «стекло»: лёгкий светлый градиент сверху вниз
        UiDraw.roundGradient(ctx, wx + 1, wy + 1, ww - 2, wh - 2, wr - 1,
                UiTheme.mix(winBg, 0xFFFFFF, 0.05), winBg, true, true);
        UiDraw.roundGradient(ctx, wx + 1, wy + 1, sideW - 1, wh - 2, wr - 1,
                UiTheme.mix(sideBg, 0xFFFFFF, 0.04), sideBg, true, false);
        UiDraw.rect(ctx, wx + sideW, wy + 1, 1, wh - 2, c(Role.CARD_BORDER));
        UiDraw.rect(ctx, wx + sideW + 1, wy + 34, ww - sideW - 2, 1, c(Role.CARD_BORDER));

        drawSidebar(ctx);
        drawHeader(ctx);
        drawContent(ctx);
        if (module != null) drawFooter(ctx);
        if (section != Section.THEME) drawToast(ctx);
    }

    private void drawToast(DrawContext ctx) {
        if (System.currentTimeMillis() >= toastUntil) return;
        TextRenderer tr = textRenderer;
        int col = c(toastOk ? Role.OK : Role.ERROR);
        String shown = UiDraw.fit(tr, toast, viewR - viewL - 16);
        int tw = UiDraw.width(tr, shown) + 16;
        int tx = viewL + (viewR - viewL - tw) / 2;
        int ty = wy + wh - 28;
        UiDraw.box(ctx, tx, ty, tw, 18, col, UiTheme.mix(c(Role.CARD_BG), col, 0.15));
        UiDraw.text(ctx, tr, shown, tx + 8, ty + 5, col);
    }

    private static final int WIN_RADIUS = 12;

    private void drawSidebar(DrawContext ctx) {
        TextRenderer tr = textRenderer;
        UiDraw.text(ctx, tr, UiDraw.fit(tr, "SpotHelper", sideW - 20), wx + 12, wy + 13, c(Role.HIGHLIGHT));

        Section[] all = Section.values();
        int avail = wh - 34 - 8;
        int ih = Math.max(14, Math.min(22, avail / all.length - 2));
        int ix = wx + 6;
        int iw = sideW - 12;
        int iy = wy + 34;

        for (Section s : all) {
            boolean active = s == section && !s.external;
            boolean hov = in.mouseOver(ix, iy, iw, ih);
            // плавные состояния пункта: наведение и «выбран»
            float kh = com.spothelper.gui.ui.UiAnim.approach("nav:h:" + s.name(), hov ? 1f : 0f);
            float ka = com.spothelper.gui.ui.UiAnim.approach("nav:a:" + s.name(), active ? 1f : 0f);
            float kv = Math.max(ka, kh);
            if (kv > 0.01f) {
                int base = c(Role.SIDEBAR_BG);
                int bg = UiTheme.mix(UiTheme.hover(base), UiTheme.navActive(), ka);
                if (hov && in.down) bg = UiTheme.pressed(bg);
                int rgbaBg = ((int) (kv * 255) << 24) | (bg & 0xFFFFFF);
                UiDraw.roundRect(ctx, ix, iy, iw, ih, Math.min(ih / 2, 8), rgbaBg);
                int barH = Math.round((ih - 8) * ka);
                if (barH > 0) UiDraw.rect(ctx, ix + 2, iy + (ih - barH) / 2, 2, barH, c(Role.ACCENT));
            }
            int tc = UiTheme.mix(c(Role.TEXT_MAIN), c(Role.HIGHLIGHT), kv);
            UiDraw.text(ctx, tr, UiDraw.fit(tr, s.title, iw - 24), ix + 8, iy + (ih - 8) / 2, tc);
            if (s.external) {
                UiDraw.textRight(ctx, tr, "›", ix + iw - 6, iy + (ih - 8) / 2, c(Role.TEXT_DIM));
            }
            final Section target = s;
            in.addClick(ix, iy, iw, ih, () -> selectSection(target));
            iy += ih + 2;
        }
    }

    private void selectSection(Section s) {
        section = s;
        module = null;
        openFeature = null;
        scroll = 0;
        com.spothelper.gui.ui.UiAnim.set("secfade", 0f); // контент нового раздела плавно «выезжает» снизу
        swapWaiting = -1;
        setFocused(null);
        attachPage();
    }

    private UiScreen pageFor(Section s) {
        return switch (s) {
            case MINIMAP -> pages.computeIfAbsent(s, k -> new MinimapSettingsScreen(this));
            case WAYPOINTS -> pages.computeIfAbsent(s, k -> new WaypointScreen(this));
            case BINDS -> pages.computeIfAbsent(s, k -> new CommandBindScreen(this));
            case FRIENDS -> pages.computeIfAbsent(s, k -> new FriendsScreen(this));
            case HUD -> pages.computeIfAbsent(s, k -> new HudEditScreen(this));
            default -> null;
        };
    }

    /** Страница, которую надо встроить сейчас: у раздела «Функции» — страница раскрытой функции. */
    private UiScreen currentPage() {
        if (section == Section.FUNCTIONS || section == Section.VISUAL) return openFeature == null ? null : featurePage(openFeature);
        return pageFor(section);
    }

    /** Встраиваемая страница настроек функции (null — у функции нет отдельной страницы). */
    private UiScreen featurePage(Feature f) {
        return switch (f) {
            case HUD -> pageFor(Section.HUD);
            case WAYPOINTS -> pageFor(Section.WAYPOINTS);
            case BINDS -> pageFor(Section.BINDS);
            case SELL -> featurePages.computeIfAbsent(f, k -> new SellProtectScreen(this));
            default -> null;
        };
    }

    /** Подключает страницу текущего раздела (и её поля ввода) к этому экрану. */
    private void attachPage() {
        clearChildren();
        activePage = currentPage();
        if (activePage == null || client == null) return;
        activePage.embedInit(client, width, height, this);
        for (UiField f : activePage.fieldList()) addSelectableChild(f.widget());
    }

    private int drawPage(DrawContext ctx, int x, int top, int w) {
        if (activePage == null) return 0;
        float[] sc = {scroll};
        int used = activePage.drawEmbedded(ctx, x, top, w, viewT, viewB, in, sc, this::toast);
        scroll = sc[0];
        return used;
    }

    private void drawHeader(DrawContext ctx) {
        TextRenderer tr = textRenderer;
        MenuBridge.Status st = MenuBridge.status();
        String txt;
        int col;
        if (st.ok()) {
            String arrow = st.trend() > 0 ? " ▲" : st.trend() < 0 ? " ▼" : "";
            txt = Double.isNaN(st.coin()) ? "API ok" : "API ok · Коин " + fmtCoin(st.coin()) + arrow;
            col = c(Role.OK);
        } else if (st.loading()) {
            txt = "API: загрузка…";
            col = c(Role.TEXT_DIM);
        } else {
            txt = "API: ошибка";
            col = c(Role.ERROR);
        }
        int maxChip = Math.max(60, (viewR - viewL) / 2);
        String shown = UiDraw.fit(tr, txt, maxChip - 16);
        int cw = UiDraw.width(tr, shown) + 16;
        int cx = viewR - cw;
        int cy = wy + 8;
        UiDraw.box(ctx, cx, cy, cw, 18, col, UiTheme.mix(c(Role.CARD_BG), col, 0.15));
        UiDraw.text(ctx, tr, shown, cx + 8, cy + 5, col);

        String title = module != null ? module.title : section.title;
        UiDraw.text(ctx, tr, UiDraw.fit(tr, title, cx - 8 - viewL), viewL, wy + 13, c(Role.HIGHLIGHT));
    }

    private void drawContent(DrawContext ctx) {
        int vw = viewR - viewL;
        int vh = viewB - viewT;
        if (vh < 10 || vw < 20) return;

        int maxScroll = Math.max(0, contentH - vh);
        scroll = Math.max(0, Math.min(scroll, maxScroll));

        ctx.enableScissor(viewL - 3, viewT, viewR + 3, viewB);
        in.pushClip(viewL - 3, viewT, vw + 6, vh);
        float fadeP = com.spothelper.gui.ui.UiAnim.approach("secfade", 1f, 10f);
        int top = viewT - Math.round(scroll) + Math.round((1f - fadeP) * 14f);
        int used;
        if (module != null) {
            used = drawModulePanel(ctx, viewL, top, vw);
        } else {
            used = switch (section) {
                case EVENTS -> drawEvents(ctx, viewL, top, vw);
                case VOTES -> drawVotes(ctx, viewL, top, vw);
                case FUNCTIONS -> drawFunctions(ctx, viewL, top, vw);
                case VISUAL -> drawVisual(ctx, viewL, top, vw);
                case SOUNDS -> drawSounds(ctx, viewL, top, vw);
                case THEME -> drawTheme(ctx, viewL, top, vw);
                case MINIMAP, WAYPOINTS, BINDS, FRIENDS, HUD -> drawPage(ctx, viewL, top, vw);
                default -> 0;
            };
        }
        in.popClip();
        ctx.disableScissor();
        contentH = used + 4;

        if (contentH > vh) {
            int tx = viewR + 4;
            UiDraw.rect(ctx, tx, viewT, 3, vh, c(Role.SLIDER_TRACK));
            int th = Math.max(16, vh * vh / contentH);
            int ty = viewT + (int) ((vh - th) * (scroll / (float) (contentH - vh)));
            UiDraw.rect(ctx, tx, ty, 3, th, c(Role.ACCENT));
        }
    }

    // ================================================================ События и Голосования


    private int drawEvents(DrawContext ctx, int x, int top, int w) {
        TextRenderer tr = textRenderer;

        List<MenuBridge.EventRow> list = MenuBridge.events(); // уже по анархиям от меньшей к большей
        if (eventSortByTime) {
            list.sort(java.util.Comparator.comparingInt(
                    (MenuBridge.EventRow e) -> e.src().available ? e.src().liveSeconds() : Integer.MAX_VALUE));
        }

        // чипы: все известные события + любые другие, что пришли из API
        LinkedHashSet<String> names = new LinkedHashSet<>(com.spothelper.holyworld.EventTexts.KNOWN_EVENTS);
        java.util.TreeSet<String> extra = new java.util.TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (MenuBridge.EventRow e : list) {
            String t = e.title();
            if (t == null || t.isEmpty()) continue;
            boolean known = false;
            for (String k : names) {
                if (k.equalsIgnoreCase(t)) {
                    known = true;
                    break;
                }
            }
            if (!known) extra.add(t);
        }
        names.addAll(extra);
        if (eventFilterName != null && !names.contains(eventFilterName)) eventFilterName = null;

        String[] labels = new String[names.size() + 3];
        Runnable[] acts = new Runnable[labels.length];
        boolean[] active = new boolean[labels.length];
        labels[0] = "Все";
        acts[0] = () -> eventFilterName = null;
        active[0] = eventFilterName == null;
        int i = 1;
        for (String n : names) {
            final String name = n;
            labels[i] = n;
            acts[i] = () -> eventFilterName = Objects.equals(eventFilterName, name) ? null : name;
            active[i] = n.equals(eventFilterName);
            i++;
        }
        labels[i] = eventSortByTime ? "Сортировка: по времени" : "Сортировка: анархия ↑";
        acts[i] = () -> eventSortByTime = !eventSortByTime;
        i++;
        labels[i] = "⟳ Обновить";
        acts[i] = () -> {
            lastPoll = System.currentTimeMillis();
            MenuBridge.refresh();
        };
        int y = chipRow(ctx, x, top, w, labels, acts, active);

        int shown = 0;
        for (MenuBridge.EventRow e : list) {
            if (eventFilterName != null && !eventFilterName.equalsIgnoreCase(e.title())) continue;
            final MenuBridge.EventRow row = e;
            UiCard.draw(ctx, tr, in, x, y, w, 38, e.title(), e.detail(), 0, () -> MenuBridge.startEvent(row));
            y += 38 + 6;
            shown++;
        }
        if (shown == 0) {
            UiDraw.text(ctx, tr, "Нет событий", x, y + 4, c(Role.TEXT_DIM));
            y += 20;
        }
        return y - top;
    }

    /** Ряд чипов с переносом строк. Возвращает y под рядом. */
    private int chipRow(DrawContext ctx, int x, int y, int w, String[] labels, Runnable[] acts, boolean[] active) {
        TextRenderer tr = textRenderer;
        int cx = x;
        for (int i = 0; i < labels.length; i++) {
            int cw = Math.min(UiChip.width(tr, labels[i]), w);
            if (cx + cw > x + w && cx > x) {
                cx = x;
                y += 24;
            }
            UiChip.draw(ctx, tr, in, cx, y, cw, 18, labels[i], active[i], true, acts[i]);
            cx += cw + 6;
        }
        return y + 26;
    }

    private int drawVotes(DrawContext ctx, int x, int top, int w) {
        TextRenderer tr = textRenderer;
        List<MenuBridge.Vote> all = MenuBridge.votes(); // по анархиям от меньшей к большей
        if (all.isEmpty()) {
            UiDraw.text(ctx, tr, "Нет активных голосований", x, top + 4, c(Role.TEXT_DIM));
            return 16;
        }

        // анархии — в порядке возрастания (список уже отсортирован), ивенты — все, что встречаются в вариантах
        LinkedHashSet<String> servers = new LinkedHashSet<>();
        java.util.TreeSet<String> events = new java.util.TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (MenuBridge.Vote v : all) {
            servers.add(v.server());
            for (MenuBridge.VoteOption o : v.options()) events.add(o.name());
        }
        if (serverFilter != null && !servers.contains(serverFilter)) serverFilter = null;
        if (voteEvent != null && !events.contains(voteEvent)) voteEvent = null;

        int y = top;
        UiDraw.text(ctx, tr, "Анархия", x, y, c(Role.TEXT_DIM));
        y += 12;
        List<String> sl = new ArrayList<>();
        sl.add("Все");
        sl.addAll(servers);
        String[] sLabels = sl.toArray(new String[0]);
        Runnable[] sActs = new Runnable[sLabels.length];
        boolean[] sActive = new boolean[sLabels.length];
        for (int i = 0; i < sLabels.length; i++) {
            final String value = i == 0 ? null : sLabels[i];
            sActive[i] = Objects.equals(serverFilter, value);
            sActs[i] = () -> serverFilter = (value == null || Objects.equals(serverFilter, value)) ? null : value;
        }
        y = chipRow(ctx, x, y, w, sLabels, sActs, sActive);

        UiDraw.text(ctx, tr, "Ивент", x, y, c(Role.TEXT_DIM));
        y += 12;
        List<String> el = new ArrayList<>();
        el.add("Все");
        el.addAll(events);
        String[] eLabels = el.toArray(new String[0]);
        Runnable[] eActs = new Runnable[eLabels.length];
        boolean[] eActive = new boolean[eLabels.length];
        for (int i = 0; i < eLabels.length; i++) {
            final String value = i == 0 ? null : eLabels[i];
            eActive[i] = Objects.equals(voteEvent, value);
            eActs[i] = () -> voteEvent = (value == null || Objects.equals(voteEvent, value)) ? null : value;
        }
        y = chipRow(ctx, x, y, w, eLabels, eActs, eActive);

        int shown = 0;
        for (MenuBridge.Vote v : all) {
            if (serverFilter != null && !serverFilter.equals(v.server())) continue;
            if (voteEvent != null) {
                boolean has = false;
                for (MenuBridge.VoteOption o : v.options()) {
                    if (o.name().equalsIgnoreCase(voteEvent)) {
                        has = true;
                        break;
                    }
                }
                if (!has) continue;
            }
            shown++;
            List<MenuBridge.VoteOption> opts = v.options();
            int h = 34 + opts.size() * 14 + 6;
            UiCard.draw(ctx, tr, in, x, y, w, h, null, null, 0, null);
            UiDraw.text(ctx, tr, UiDraw.fit(tr, v.title(), w - 16), x + 8, y + 7, c(Role.TEXT_MAIN));

            String rem = "осталось " + v.remaining();
            int remW = UiDraw.width(tr, rem);
            UiDraw.text(ctx, tr, UiDraw.fit(tr, v.server(), w - 24 - remW), x + 8, y + 19, c(Role.HIGHLIGHT));
            UiDraw.textRight(ctx, tr, rem, x + w - 8, y + 19, c(Role.TEXT_DIM));

            int total = 0;
            int best = 0;
            for (MenuBridge.VoteOption o : opts) {
                total += o.votes();
                best = Math.max(best, o.votes());
            }
            total = Math.max(1, total);
            int labW = Math.max(40, (w - 16) * 35 / 100);
            int valW = 58;
            int barX = x + 8 + labW + 6;
            int barW = Math.max(10, w - 16 - labW - valW - 12);
            int by = y + 34;
            for (MenuBridge.VoteOption o : opts) {
                boolean picked = voteEvent != null && o.name().equalsIgnoreCase(voteEvent);
                UiDraw.text(ctx, tr, UiDraw.fit(tr, o.name(), labW), x + 8, by,
                        c(picked ? Role.HIGHLIGHT : Role.TEXT_MAIN));
                int fw = Math.round(barW * (o.votes() / (float) total));
                UiDraw.rect(ctx, barX, by + 1, barW, 6, c(Role.SLIDER_TRACK));
                UiDraw.rect(ctx, barX, by + 1, fw, 6, c(o.votes() == best || picked ? Role.HIGHLIGHT : Role.ACCENT));
                String val = o.votes() + " · " + Math.round(100f * o.votes() / total) + "%";
                UiDraw.textRight(ctx, tr, UiDraw.fit(tr, val, valW), x + w - 8, by, c(Role.TEXT_DIM));
                by += 14;
            }
            y += h + 6;
        }
        if (shown == 0) {
            UiDraw.text(ctx, tr, "Нет голосований по выбранным фильтрам", x, y + 4, c(Role.TEXT_DIM));
            y += 20;
        }
        return y - top;
    }

    // ================================================================ Функции

    /** Минимальная ширина колонки раздела «Функции»; уже окно — меньше колонок (но не меньше одной). */
    private static final int FN_MIN_COL_W = 132;
    private static final int FN_COLS = 3;
    private static final int FN_GAP = 8;
    private static final int FN_CARD_BASE_H = 40;

    /** Карточка раздела «Функции»: рисует себя в (x, y) шириной w и возвращает занятую высоту. */
    private interface FnCard {
        int draw(int x, int y, int w);
    }

    private record FnItem(FnCard card, Feature feature, String label) {}

    /** Функции, которые живут в разделе «Визуализация» (всё остальное остаётся в «Функциях»). */
    private static final java.util.EnumSet<Feature> VISUAL_FEATURES = java.util.EnumSet.of(
            Feature.TOTEM_COLOR, Feature.ARMOR, Feature.HEALTH,
            Feature.GAMMA, Feature.FREE_LOOK, Feature.ZOOM, Feature.F5_ZOOM, Feature.CROSSHAIR,
            Feature.HITBOXES, Feature.WP_WORLD, Feature.TNT_TIMER);

    private FnItem featureItem(DrawContext ctx, Feature f) {
        return new FnItem((x, y, w) -> animated(ctx, "fr:" + f, FEATURE_HEAD_H, x, y, w,
                () -> drawFeatureRow(ctx, f, x, y, w)), f, null);
    }

    /** Все карточки раздела «Функции» в фиксированном порядке (не зависит от раскрытия). */
    private List<FnItem> functionItems(DrawContext ctx) {
        List<FnItem> items = new ArrayList<>();
        for (Feature f : Feature.values()) {
            if (f == Feature.FILTERS || VISUAL_FEATURES.contains(f)) continue; // «Фильтры» — под сеткой
            items.add(featureItem(ctx, f));
        }
        items.add(new FnItem((x, y, w) -> animated(ctx, "sl", 40, x, y, w, () -> drawSlotLock(ctx, x, y, w)), null, null));
        return items;
    }

    /** Карточки раздела «Визуализация». */
    private List<FnItem> visualItems(DrawContext ctx) {
        List<FnItem> items = new ArrayList<>();
        for (Feature f : Feature.values()) if (VISUAL_FEATURES.contains(f)) items.add(featureItem(ctx, f));
        items.add(new FnItem((x, y, w) -> animated(ctx, "dt", 40, x, y, w, () -> drawDamageTint(ctx, x, y, w)), null, null));
        items.add(new FnItem((x, y, w) -> animated(ctx, "nf", 40, x, y, w, () -> drawNegFx(ctx, x, y, w)), null, null));
        return items;
    }

    private int drawVisual(DrawContext ctx, int x, int top, int w) {
        return layoutColumns(visualItems(ctx), x, top, w) + FN_GAP;
    }

    /** Последняя полная высота раскрытой карточки (по ключу) — для плавного раскрытия. */
    private final java.util.Map<Object, Integer> lastFullH = new java.util.HashMap<>();

    /** Тело карточки рисуется, пока она открыта или ещё закрывается (анимация). */
    private boolean bodyVisible(Object key, boolean open) {
        float r = com.spothelper.gui.ui.UiAnim.approach(key, open ? 1f : 0f);
        return open || r > 0.01f;
    }

    /**
     * Плавное раскрытие: карточка рисуется целиком, но обрезается по текущей высоте
     * (scissor + клип кликов), а колонке отдаётся промежуточная высота.
     */
    private int animated(DrawContext ctx, Object key, int headH, int x, int y, int w,
                         java.util.function.IntSupplier draw) {
        float r = com.spothelper.gui.ui.UiAnim.get(key, 0f);
        Integer lf = lastFullH.get(key);
        boolean clip = r < 0.999f;
        int shown = lf == null ? headH : headH + Math.round((lf - headH) * r);
        if (clip) {
            ctx.enableScissor(x - 2, y - 2, x + w + 2, y + shown);
            in.pushClip(x - 2, y - 2, w + 4, shown + 2);
        }
        int full = draw.getAsInt();
        if (clip) {
            in.popClip();
            ctx.disableScissor();
        }
        lastFullH.put(key, full);
        float r2 = Math.min(1f, com.spothelper.gui.ui.UiAnim.get(key, 0f));
        return full <= headH ? full : headH + Math.round((full - headH) * r2);
    }

    /** Раздел «Звуки»: блоки звуков (крит и типы ударов) в тех же трёх колонках. */
    private int drawSounds(DrawContext ctx, int x, int top, int w) {
        List<FnItem> items = new ArrayList<>();
        for (SoundBlock sb : soundBlocks) {
            items.add(new FnItem((cx, cy, cw) -> animated(ctx, sb, 40, cx, cy, cw,
                    () -> drawSoundBlock(ctx, cx, cy, cw, sb)), null, null));
        }
        return layoutColumns(items, x, top, w);
    }

    private int drawFunctions(DrawContext ctx, int x, int top, int w) {
        int used = layoutColumns(functionItems(ctx), x, top, w);
        // «Фильтры» и всё остальное — ниже самой длинной колонки
        int fy = top + used;
        if (pendingReveal == Feature.FILTERS) {
            scroll = Math.max(0, fy - top - 2);
            pendingReveal = null;
        }
        return used + animated(ctx, "fr:" + Feature.FILTERS, FEATURE_HEAD_H, x, fy, w,
                () -> drawFeatureRow(ctx, Feature.FILTERS, x, fy, w)) + FN_GAP;
    }

    /** Раскладка карточек по колонкам (общая для «Функций» и «Звуков»). Возвращает высоту самой длинной колонки. */
    private int layoutColumns(List<FnItem> items, int x, int top, int w) {
        int cols = Math.max(1, Math.min(FN_COLS, (w + FN_GAP) / (FN_MIN_COL_W + FN_GAP)));
        int colW = (w - FN_GAP * (cols - 1)) / cols;
        int[] colX = new int[cols];
        for (int i = 0; i < cols; i++) colX[i] = x + i * (colW + FN_GAP);

        // Колонка выбирается по высоте СВЁРНУТЫХ карточек: раскрытие не перекидывает остальные карточки
        // между колонками, раскрытая карточка растёт только вниз внутри своей колонки.
        int[] baseH = new int[cols];
        int[] curH = new int[cols];
        for (FnItem it : items) {
            int col = 0;
            for (int i = 1; i < cols; i++) if (baseH[i] < baseH[col]) col = i;
            int cx = colX[col];
            int cy = top + curH[col];
            if (it.feature() != null && pendingReveal == it.feature()) { // только что раскрыли: прокрутка к карточке
                scroll = Math.max(0, cy - top - 2);
                pendingReveal = null;
            }
            int h = it.card().draw(cx, cy, colW);
            baseH[col] += FN_CARD_BASE_H + FN_GAP;
            curH[col] += h + FN_GAP;
        }
        int used = 0;
        for (int h : curH) used = Math.max(used, h);
        return used;
    }

    private void toggleInventoryHud() {
        SpotHelperConfig.INSTANCE.invHud ^= 1;
        SpotHelperConfig.INSTANCE.save();
    }

    // ================================================================ Список функций (аккордеон)

    private static final int FEATURE_HEAD_H = 40;
    private static final int ARROW_W = 22;

    /** Переключатель вкл/выкл функции: текущее состояние и действие. */
    private record Tgl(boolean on, Runnable action) {}

    private Tgl featureToggle(Feature f) {
        return switch (f) {
            case HUD -> new Tgl(MenuBridge.hudOn(), this::toggleHud);
            case FREE_LOOK -> new Tgl(MenuBridge.freeLookOn(), this::toggleFreeLook);
            case ZOOM -> new Tgl(MenuBridge.zoomOn(), this::toggleZoom);
            case F5_ZOOM -> new Tgl(MenuBridge.f5ZoomOn(), this::toggleF5Zoom);
            case CROSSHAIR -> new Tgl(MenuBridge.crosshairOn(), this::toggleCrosshair);
            case HITBOXES -> new Tgl(MenuBridge.hitboxesOn(), this::toggleHitboxes);
            case TNT_TIMER -> new Tgl(MenuBridge.tntOn(), this::toggleTntTimer);
            case INVENTORY_HUD -> new Tgl(SpotHelperConfig.INSTANCE.invHud == 1, this::toggleInventoryHud);
            case AUTOSPRINT -> new Tgl(MenuBridge.autoSprintOn(), this::toggleAutoSprint);
            case ARMOR -> new Tgl(MenuBridge.armorOn(), this::toggleArmor);
            case WP_WORLD -> new Tgl(MenuBridge.wpWorldOn(), this::toggleWpWorld);
            case TOTEM_COLOR -> new Tgl(MenuBridge.totemColorsOn(), this::toggleTotemColor);
            case AUTO_ANARCHY -> new Tgl(MenuBridge.anarchyOn(), this::toggleAnarchy);
            case ENTITY_HEIGHT -> new Tgl(MenuBridge.heightOn(), this::toggleHeight);
            case HEALTH -> new Tgl(MenuBridge.healthOn(), this::toggleHealth);
            case SHULKER -> new Tgl(MenuBridge.shulkerOn(), this::toggleShulker);
            case REPLY -> new Tgl(MenuBridge.replyOn(), this::toggleReply);
            case GAMMA -> new Tgl(MenuBridge.gammaOn(), this::toggleGamma);
            default -> null; // WAYPOINTS, BINDS, SELL, MUSIC, FILTERS: только настройки, без общего переключателя
        };
    }

    /** Есть ли у функции раскрывающиеся настройки (нет — значит, нет и стрелки). */
    private static boolean hasSettings(Feature f) {
        return switch (f) {
            case HUD, WAYPOINTS, BINDS, SELL, TNT_TIMER, INVENTORY_HUD,
                 ENTITY_HEIGHT, HEALTH, SHULKER, GAMMA, MUSIC, FILTERS -> true;
            default -> false;
        };
    }

    private static Module moduleOf(Feature f) {
        return switch (f) {
            case HEALTH -> Module.HEALTH;
            case SHULKER -> Module.SHULKER;
            case GAMMA -> Module.GAMMA;
            default -> null;
        };
    }

    /** Раскрывает функцию (закрывая остальные) или закрывает её, если она уже раскрыта. */
    private void toggleFeature(Feature f) {
        if (openFeature == f) {
            openFeature = null;
        } else {
            swapsExpanded = false;
            swapWaiting = -1;
            slotLockExpanded = false;
            damageTintExpanded = false;
            negFxExpanded = false;
            collapseSoundBlocks();
            SoundPlayer.stopPreview();
            openFeature = f;
            pendingReveal = f;
        }
        setFocused(null);
        attachPage();
    }

    private void closeFeature() {
        if (openFeature != null) {
            openFeature = null;
            setFocused(null);
            attachPage();
        }
    }

    private void bodyBox(DrawContext ctx, int x, int y, int w, int h) {
        UiDraw.box(ctx, x, y, w, h, c(Role.CARD_BORDER), c(Role.CARD_BG));
    }

    /** Строка списка: заголовок (название, описание, переключатель, стрелка) и, если раскрыта, настройки. */
    private int drawFeatureRow(DrawContext ctx, Feature f, int x, int y, int w) {
        TextRenderer tr = textRenderer;
        boolean settings = hasSettings(f);
        boolean open = openFeature == f;
        Tgl t = featureToggle(f);

        int tx = x + w - 8 - UiToggle.W;
        int ax = (t != null ? tx - 4 : x + w - 8) - ARROW_W;
        int pad = 0;
        if (t != null) pad += UiToggle.W + 8;
        if (settings) pad += ARROW_W + 8;

        // клик по заголовку: у функции с настройками раскрывает их, у простой — переключает её
        Runnable click = settings ? () -> toggleFeature(f) : (t != null ? t.action() : null);
        UiCard.draw(ctx, tr, in, x, y, w, FEATURE_HEAD_H, f.title, f.desc, pad, click);
        if (t != null) UiToggle.draw(ctx, in, tx, y + 6, t.on(), true, t.action());
        if (settings) {
            UiChip.draw(ctx, tr, in, ax, y + 5, ARROW_W, 16, open ? "▲" : "▼", open, true, () -> toggleFeature(f));
        }
        if (!settings || !bodyVisible("fr:" + f, open)) return FEATURE_HEAD_H;
        int by = y + FEATURE_HEAD_H + 4;
        return FEATURE_HEAD_H + 4 + drawFeatureBody(ctx, f, x, by, w);
    }

    /** Настройки раскрытой функции. Возвращает занятую высоту. */
    private int drawFeatureBody(DrawContext ctx, Feature f, int x, int by, int w) {
        TextRenderer tr = textRenderer;
        int iw = w - 16;
        int rx = x + 8;
        switch (f) {
            case HEALTH, SHULKER, GAMMA -> {
                Module m = moduleOf(f);
                int h = drawModuleSettings(ctx, x, by, w, m);
                String reset = "Сбросить настройки";
                UiChip.draw(ctx, tr, in, x, by + h, Math.min(w, UiChip.width(tr, reset)), 20, reset, false, true,
                        () -> resetModule(m));
                return h + 24;
            }
            case HUD, WAYPOINTS, BINDS, SELL -> {
                return drawPage(ctx, x, by, w); // встроенная страница функции
            }
            case TNT_TIMER -> {
                boolean tOn = MenuBridge.tntOn();
                int bh = 8 + UiSlider.H + 8 + 18 + 8;
                bodyBox(ctx, x, by, w, bh);
                int cy = by + 8;
                sync(tntRange, MenuBridge.tntRange());
                tntRange.draw(ctx, tr, in, rx, cy, iw, tOn);
                cy += UiSlider.H + 8;
                UiChip.draw(ctx, tr, in, rx, cy, iw, 18,
                        "HUD-список (двигается в «HUD»): " + (MenuBridge.tntInHud() ? "ВКЛ" : "ВЫКЛ"),
                        MenuBridge.tntInHud(), tOn, () -> {
                            MenuBridge.setTntInHud(!MenuBridge.tntInHud());
                            MenuBridge.save();
                        });
                return bh;
            }
            case INVENTORY_HUD -> {
                SpotHelperConfig icfg = SpotHelperConfig.INSTANCE;
                boolean iOn = icfg.invHud == 1;
                int bh = 8 + UiSlider.H + 8 + 18 + 8;
                bodyBox(ctx, x, by, w, bh);
                int cy = by + 8;
                sync(invHudScale, icfg.invHudScale);
                invHudScale.draw(ctx, tr, in, rx, cy, iw, iOn);
                cy += UiSlider.H + 8;
                UiChip.draw(ctx, tr, in, rx, cy, iw, 18,
                        "Режим: " + (icfg.invHudMini == 1 ? "mini" : "обычный"), false, iOn, () -> {
                            icfg.invHudMini ^= 1;
                            icfg.save();
                        });
                return bh;
            }
            case ENTITY_HEIGHT -> {
                boolean hOn = MenuBridge.heightOn();
                int bh = 8 + 18 + 6 + 18 + 8;
                bodyBox(ctx, x, by, w, bh);
                int cy = by + 8;
                UiChip.draw(ctx, tr, in, rx, cy, iw, 18,
                        "Показ: " + HEIGHT_MODES[MenuBridge.heightMode()], false, hOn, () -> {
                            MenuBridge.cycleHeightMode();
                            MenuBridge.save();
                        });
                cy += 24;
                int hHalf = (iw - 6) / 2;
                UiChip.draw(ctx, tr, in, rx, cy, hHalf, 18,
                        MenuBridge.heightPrefix() ? "Формат: у 64" : "Формат: 64",
                        MenuBridge.heightPrefix(), hOn, () -> {
                            MenuBridge.toggleHeightPrefix();
                            MenuBridge.save();
                        });
                UiChip.draw(ctx, tr, in, rx + hHalf + 6, cy, hHalf, 18,
                        "Цвет: " + (MenuBridge.heightColored() ? "по высоте" : "белый"),
                        MenuBridge.heightColored(), hOn, () -> {
                            MenuBridge.toggleHeightColored();
                            MenuBridge.save();
                        });
                return bh;
            }
            case MUSIC -> {
                int bh = 8 + 20 + 8;
                bodyBox(ctx, x, by, w, bh);
                String open = "Открыть плеер ›";
                UiChip.draw(ctx, tr, in, rx, by + 8, Math.min(iw, UiChip.width(tr, open)), 20, open, false, true, () -> {
                    if (client != null) client.setScreen(new MusicScreen(this));
                });
                return bh;
            }
            case FILTERS -> {
                int bh = 8 + 18 + 8;
                bodyBox(ctx, x, by, w, bh);
                int chipW = (iw - 12) / 3;
                UiChip.draw(ctx, tr, in, rx, by + 8, chipW, 18, "Враждебные", false, true,
                        () -> MenuBridge.openHostileFilter(this));
                UiChip.draw(ctx, tr, in, rx + chipW + 6, by + 8, chipW, 18, "Мирные", false, true,
                        () -> MenuBridge.openPassiveFilter(this));
                UiChip.draw(ctx, tr, in, rx + (chipW + 6) * 2, by + 8, chipW, 18, "Предметы", false, true,
                        () -> MenuBridge.openItemFilter(this));
                return bh;
            }
            default -> {
                return 0;
            }
        }
    }


    private List<String> wrapText(String text, int maxW) {
        List<String> out = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            String candidate = line.length() == 0 ? word : line + " " + word;
            if (line.length() > 0 && UiDraw.width(textRenderer, candidate) > maxW) {
                out.add(line.toString());
                line = new StringBuilder(word);
            } else {
                line = new StringBuilder(candidate);
            }
        }
        if (line.length() > 0) out.add(line.toString());
        return out;
    }

    // ================================================================ Блокировка слотов

    private int drawSlotLock(DrawContext ctx, int x, int y, int w) {
        TextRenderer tr = textRenderer;
        SpotHelperConfig cfg = SpotHelperConfig.INSTANCE;
        final int headH = 40;
        int arrowW = 22;
        int tx = x + w - 8 - UiToggle.W;
        int ax = tx - 4 - arrowW;

        UiCard.draw(ctx, tr, in, x, y, w, headH, "Блокировка слотов",
                "Защита предметов от выброса, перемещения и продажи", UiToggle.W + arrowW + 16, this::toggleSlotLockExpanded);
        UiToggle.draw(ctx, in, tx, y + 6, cfg.isSlotLockEnabled(), true, this::toggleSlotLock);
        UiChip.draw(ctx, tr, in, ax, y + 5, arrowW, 16, slotLockExpanded ? "▲" : "▼", slotLockExpanded, true,
                this::toggleSlotLockExpanded);
        if (!bodyVisible("sl", slotLockExpanded)) return headH;

        int by = y + headH + 4;
        int iw = w - 16;
        int rx = x + 8;
        final int lockGap = 2;
        final int cell = Math.max(10, Math.min(18, (iw - 8 * lockGap) / 9));
        final boolean stackModes = (iw - 12) / 3 < 72; // три чипа в ряд не помещаются — ставим столбиком
        final int modeBlockH = stackModes ? 3 * 22 - 4 : 18;
        String hint = "Выберите слот кликом и отметьте, что ему запретить. В окне инвентаря: Alt + клик по слоту — все запреты сразу или снять.";
        List<String> hintLines = wrapText(hint, iw);
        int bodyH = 8 + 12 + modeBlockH + 6 + 20 + 6 + 3 * (cell + lockGap) + 6 + (cell + lockGap) + 6 + 12 + hintLines.size() * 11 + 4 + 20 + 8;
        UiDraw.box(ctx, x, by, w, bodyH, c(Role.CARD_BORDER), c(Role.CARD_BG));

        int cy = by + 8;
        int mode = SlotLockManager.rawMode(slotLockSel);
        String selName = slotLockSel < 9 ? "хотбар " + (slotLockSel + 1) : "инвентарь " + (slotLockSel - 8);
        UiDraw.text(ctx, tr, "Слот: " + selName, rx, cy, c(Role.TEXT_MAIN));
        cy += 12;
        String[] names = {"Выброс", "Перемещение", "Продажа"};
        int[] bits = {SlotLockManager.MODE_DROP, SlotLockManager.MODE_MOVE, SlotLockManager.MODE_SELL};
        int chipW = (iw - 12) / 3;
        for (int i = 0; i < 3; i++) {
            final int bit = bits[i];
            if (stackModes) {
                UiChip.draw(ctx, tr, in, rx, cy + i * 22, iw, 18, names[i], (mode & bit) != 0, true,
                        () -> SlotLockManager.toggleBit(slotLockSel, bit));
            } else {
                UiChip.draw(ctx, tr, in, rx + i * (chipW + 6), cy, chipW, 18, names[i], (mode & bit) != 0, true,
                        () -> SlotLockManager.toggleBit(slotLockSel, bit));
            }
        }
        cy += modeBlockH + 6;
        final int selMode = mode;
        String applyAll = "Применить ко всем слотам";
        UiChip.draw(ctx, tr, in, rx, cy, Math.min(iw, UiChip.width(tr, applyAll)), 20, applyAll, false, true,
                () -> SlotLockManager.setAll(selMode));
        cy += 26;

        int gridW = 9 * (cell + lockGap) - lockGap;
        int gx = rx + Math.max(0, (iw - gridW) / 2);
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                drawLockCell(ctx, gx + col * (cell + lockGap), cy, 9 + row * 9 + col, cell);
            }
            cy += cell + lockGap;
        }
        cy += 6;
        for (int col = 0; col < 9; col++) {
            drawLockCell(ctx, gx + col * (cell + lockGap), cy, col, cell);
        }
        cy += cell + lockGap + 6;

        UiDraw.text(ctx, tr, "Заблокировано слотов: " + SlotLockManager.count(), rx, cy, c(Role.TEXT_MAIN));
        cy += 12;
        for (String line : hintLines) {
            UiDraw.text(ctx, tr, line, rx, cy, c(Role.TEXT_DIM));
            cy += 11;
        }
        cy += 4;
        UiChip.draw(ctx, tr, in, rx, cy, Math.min(iw, UiChip.width(tr, "Снять все блокировки")), 20,
                "Снять все блокировки", false, SlotLockManager.count() > 0, SlotLockManager::clearAll);

        return headH + 4 + bodyH;
    }

    private void drawLockCell(DrawContext ctx, int cx, int cy, int invIndex, int size) {
        boolean locked = SlotLockManager.isMarked(invIndex);
        boolean sel = invIndex == slotLockSel;
        boolean hov = in.mouseOver(cx, cy, size, size);
        UiDraw.box(ctx, cx, cy, size, size,
                c(sel || hov ? Role.HIGHLIGHT : Role.CARD_BORDER), c(Role.WINDOW_BG));
        if (client != null && client.player != null) {
            var stack = client.player.getInventory().getStack(invIndex);
            if (!stack.isEmpty()) {
                if (size >= 18) {
                    ctx.drawItem(stack, cx + 1, cy + 1);
                } else { // ячейка уже предмета 16×16 — рисуем предмет уменьшенным
                    Matrix3x2fStack m = ctx.getMatrices();
                    m.pushMatrix();
                    m.translate(cx + 1, cy + 1);
                    float sc = (size - 2) / 16f;
                    m.scale(sc, sc);
                    ctx.drawItem(stack, 0, 0);
                    m.popMatrix();
                }
            }
        }
        if (locked) com.spothelper.slotlock.SlotLockOverlay.drawMark(ctx, cx + 1, cy + 1, size - 2, SlotLockManager.rawMode(invIndex));
        in.addClick(cx, cy, size, size, () -> slotLockSel = invIndex);
    }

    private void toggleSlotLockExpanded() {
        slotLockExpanded = !slotLockExpanded;
        if (slotLockExpanded) {
            closeFeature();
            swapsExpanded = false;
            damageTintExpanded = false;
            negFxExpanded = false;
            collapseSoundBlocks();
            swapWaiting = -1;
        }
    }

    private void toggleSlotLock() {
        SpotHelperConfig cfg = SpotHelperConfig.INSTANCE;
        cfg.slotLockEnabled = cfg.isSlotLockEnabled() ? 0 : 1;
        cfg.save();
    }


    // ================================================================ Звуки критов и ударов

    private static final class SoundBlock {
        final SoundChannel ch;
        boolean expanded;
        final UiSlider volume = new UiSlider(0, 100, 1, 96);
        String deleteArm;
        long deleteArmUntil;

        SoundBlock(SoundChannel ch) {
            this.ch = ch;
        }
    }

    /** По блоку на каждый канал из SoundChannel.ALL: крит первым, затем типы ударов. */
    private final List<SoundBlock> soundBlocks = buildSoundBlocks();

    private static List<SoundBlock> buildSoundBlocks() {
        List<SoundBlock> out = new ArrayList<>();
        for (SoundChannel ch : SoundChannel.ALL) out.add(new SoundBlock(ch));
        return out;
    }

    private static final String[] SND_TOP = {"Обновить библиотеку", "Открыть папку", "Копировать путь", "Гайд"};
    private static final String SND_EMPTY = "Папка пуста. Добавьте файлы .ogg или .wav и обновите библиотеку.";

    private void collapseSoundBlocks() {
        for (SoundBlock sb : soundBlocks) sb.expanded = false;
    }

    private String sndHint(SoundChannel ch) {
        return "Положите .ogg или .wav в папку (или перетащите файл в это окно) и нажмите «Обновить библиотеку». "
                + ch.hint();
    }

    private int drawSoundBlock(DrawContext ctx, int x, int y, int w, SoundBlock b) {
        TextRenderer tr = textRenderer;
        SoundChannel ch = b.ch;
        final int headH = 40;
        int arrowW = 22;
        int tx = x + w - 8 - UiToggle.W;
        int ax = tx - 4 - arrowW;

        UiCard.draw(ctx, tr, in, x, y, w, headH, ch.title, ch.desc, UiToggle.W + arrowW + 16,
                () -> toggleSoundExpanded(b));
        UiToggle.draw(ctx, in, tx, y + 6, ch.enabled(), true, () -> toggleSoundEnabled(ch));
        UiChip.draw(ctx, tr, in, ax, y + 5, arrowW, 16, b.expanded ? "▲" : "▼", b.expanded, true,
                () -> toggleSoundExpanded(b));
        if (!bodyVisible(b, b.expanded)) return headH;

        final int rowH = 22;
        int by = y + headH + 4;
        int iw = w - 16;
        int rx = x + 8;
        List<SoundLibrary.Entry> list = ch.library.entries();
        List<String> hint = wrapText(sndHint(ch), iw);
        List<String> empty = list.isEmpty() ? wrapText(SND_EMPTY, iw) : List.of();

        int rows = 1;
        {
            int cx0 = 0;
            for (String n : SND_TOP) {
                int cw = Math.min(UiChip.width(tr, n), iw);
                if (cx0 + cw > iw && cx0 > 0) { rows++; cx0 = 0; }
                cx0 += cw + 4;
            }
        }
        int bodyH = 8 + rows * 24 + 12 + hint.size() * 11 + 6 + (UiSlider.H + 8) + 24 + (ch.hasVanilla() ? 24 : 0) + 14 + rowH
                + (list.isEmpty() ? empty.size() * 11 + 4 : list.size() * rowH) + 6;
        UiDraw.box(ctx, x, by, w, bodyH, c(Role.CARD_BORDER), c(Role.CARD_BG));

        int cy = by + 8;
        Runnable[] topActs = {() -> sndRefresh(ch), () -> sndOpenFolder(ch), () -> sndCopyPath(ch),
                () -> { if (client != null) client.setScreen(new CritSoundsGuideScreen(this, ch)); }};
        int cx = rx;
        for (int i = 0; i < SND_TOP.length; i++) {
            int cw = Math.min(UiChip.width(tr, SND_TOP[i]), iw);
            if (cx + cw > rx + iw && cx > rx) { cx = rx; cy += 24; }
            UiChip.draw(ctx, tr, in, cx, cy, cw, 18, SND_TOP[i], false, true, topActs[i]);
            cx += cw + 4;
        }
        cy += 24;

        UiDraw.text(ctx, tr, UiDraw.fit(tr, "Папка: config/SpotHelper/" + ch.library.folder(), iw), rx, cy,
                c(Role.HIGHLIGHT));
        cy += 12;
        for (String line : hint) {
            UiDraw.text(ctx, tr, line, rx, cy, c(Role.TEXT_DIM));
            cy += 11;
        }
        cy += 6;

        sync(b.volume, Math.round(ch.volume() * 100f));
        b.volume.draw(ctx, tr, in, rx, cy, iw, true);
        cy += UiSlider.H + 8;

        boolean random = ch.random();
        UiChip.draw(ctx, tr, in, rx, cy, iw, 18,
                "Режим: " + (random ? "случайный из выбранных" : "один выбранный звук"),
                random, true, () -> AttackSounds.toggleRandom(ch));
        cy += 24;
        if (ch.hasVanilla()) {
            boolean replace = ch.replaceVanilla();
            UiChip.draw(ctx, tr, in, rx, cy, iw, 18,
                    "Заменять обычный звук: " + (replace ? "ВКЛ" : "ВЫКЛ"),
                    replace, true, () -> {
                        ch.toggleReplaceVanilla();
                        ch.save();
                    });
            cy += 24;
        }

        UiDraw.text(ctx, tr, UiDraw.fit(tr, "Библиотека (" + list.size() + ")"
                + (random ? " · отметьте файлы для случайного выбора" : ""), iw), rx, cy + 2, c(Role.TEXT_DIM));
        cy += 14;

        sndDefaultRow(ctx, rx, cy, iw, ch);
        cy += rowH;
        if (list.isEmpty()) {
            for (String line : empty) {
                UiDraw.text(ctx, tr, line, rx, cy + 2, c(Role.TEXT_DIM));
                cy += 11;
            }
        } else {
            for (SoundLibrary.Entry e : list) {
                sndRow(ctx, rx, cy, iw, b, e);
                cy += rowH;
            }
        }
        return headH + 4 + bodyH;
    }

    private void sndDefaultRow(DrawContext ctx, int rx, int cy, int iw, SoundChannel ch) {
        TextRenderer tr = textRenderer;
        boolean active = ch.random() ? ch.pool().isEmpty() : ch.name().isEmpty();
        int fill = c(active ? Role.ACCENT : Role.CARD_BG);
        UiDraw.box(ctx, rx, cy, iw, 20, c(active ? Role.HIGHLIGHT : Role.CARD_BORDER), fill);
        int selW = UiChip.width(tr, "В наборе");
        UiChip.draw(ctx, tr, in, rx + iw - 2 - selW, cy + 1, selW, 18, active ? "Выбран" : "Выбрать", active, true,
                () -> AttackSounds.selectDefault(ch));
        int col = active ? UiTheme.onColor(fill) : c(Role.TEXT_MAIN);
        UiDraw.text(ctx, tr, UiDraw.fit(tr, (active ? "● " : "") + "Стандартный звук Minecraft", iw - selW - 14),
                rx + 6, cy + 6, col);
    }

    private void sndRow(DrawContext ctx, int rx, int cy, int iw, SoundBlock b, SoundLibrary.Entry e) {
        TextRenderer tr = textRenderer;
        SoundChannel ch = b.ch;
        final String name = e.name();
        boolean random = ch.random();
        boolean active = AttackSounds.isActive(ch, name);
        boolean previewing = SoundPlayer.isPreviewing(e);
        String err = SoundPlayer.error(e);
        boolean armed = name.equals(b.deleteArm) && System.currentTimeMillis() < b.deleteArmUntil;

        int fill = c(active ? Role.ACCENT : Role.CARD_BG);
        UiDraw.box(ctx, rx, cy, iw, 20, c(active ? Role.HIGHLIGHT : Role.CARD_BORDER), fill);
        int textCol = active ? UiTheme.onColor(fill) : c(Role.TEXT_MAIN);

        int delW = armed ? UiChip.width(tr, "Точно?") : 20;
        int cx = rx + iw - 2 - delW;
        UiChip.draw(ctx, tr, in, cx, cy + 1, delW, 18, armed ? "Точно?" : "×", armed, true, () -> onSndDelete(b, name));

        int selW = UiChip.width(tr, "В наборе");
        cx -= 4 + selW;
        String selLabel = random ? (active ? "В наборе" : "В набор") : (active ? "Выбран" : "Выбрать");
        UiChip.draw(ctx, tr, in, cx, cy + 1, selW, 18, selLabel, active, e.supported(), () -> onSndSelect(ch, name));

        int lisW = UiChip.width(tr, "Слушать");
        cx -= 4 + lisW;
        UiChip.draw(ctx, tr, in, cx, cy + 1, lisW, 18, previewing ? "Стоп" : "Слушать", previewing, e.supported(),
                () -> onSndListen(ch, name));

        String info;
        if (!e.supported()) info = e.ext().toUpperCase(Locale.ROOT) + " · нет декодера";
        else if (err != null) info = "ошибка";
        else {
            info = e.ext().toUpperCase(Locale.ROOT);
            var d = SoundPlayer.cached(e);
            if (d != null) info += String.format(Locale.ROOT, " · %.1f с", d.millis() / 1000.0);
            else if (SoundPlayer.isLoading(e)) info += " · …";
        }
        int right = cx - 6;
        int avail = right - (rx + 6);
        int infoW = UiDraw.width(tr, info);
        if (avail > infoW + 60) {
            boolean bad = err != null || !e.supported();
            UiDraw.textRight(ctx, tr, info, right, cy + 6, bad ? c(Role.ERROR) : (active ? textCol : c(Role.TEXT_DIM)));
            avail -= infoW + 8;
        }
        UiDraw.text(ctx, tr, UiDraw.fit(tr, (active ? "● " : "") + name, avail), rx + 6, cy + 6, textCol);
    }

    private void onSndListen(SoundChannel ch, String name) {
        SoundLibrary.Entry e = ch.library.find(name);
        if (e == null) return;
        if (SoundPlayer.isPreviewing(e)) {
            SoundPlayer.stopPreview();
            return;
        }
        String err = SoundPlayer.error(e);
        if (err != null) {
            toast(name + ": " + err, false);
            return;
        }
        AttackSounds.preview(ch, name);
    }

    private void onSndSelect(SoundChannel ch, String name) {
        SoundLibrary.Entry e = ch.library.find(name);
        if (e == null) return;
        String err = SoundPlayer.error(e);
        if (err != null) {
            toast(name + ": " + err, false);
            return;
        }
        AttackSounds.select(ch, name);
        if (!ch.random()) toast("Активный звук: " + name, true);
    }

    private void onSndDelete(SoundBlock b, String name) {
        long now = System.currentTimeMillis();
        if (name.equals(b.deleteArm) && now < b.deleteArmUntil) {
            b.deleteArm = null;
            AttackSounds.delete(b.ch, name);
            toast("Удалён: " + name, true);
        } else {
            b.deleteArm = name; // первое нажатие только «заряжает» кнопку
            b.deleteArmUntil = now + 3000;
        }
    }

    private void sndRefresh(SoundChannel ch) {
        int n = ch.library.refresh().size();
        AttackSounds.preloadSelected(ch);
        toast("Библиотека обновлена, файлов: " + n, true);
    }

    private void sndOpenFolder(SoundChannel ch) {
        if (AttackSounds.openFolder(ch)) {
            toast("Папка открыта", true);
        } else {
            String path = ch.library.dir().toAbsolutePath().toString();
            if (client != null && client.keyboard != null) client.keyboard.setClipboard(path);
            toast("Не удалось открыть папку. Путь скопирован: " + path, false);
        }
    }

    private void sndCopyPath(SoundChannel ch) {
        String path = ch.library.dir().toAbsolutePath().toString();
        if (client != null && client.keyboard != null) client.keyboard.setClipboard(path);
        toast("Путь скопирован: " + path, true);
    }

    private void toggleSoundExpanded(SoundBlock b) {
        boolean open = !b.expanded;
        collapseSoundBlocks();
        b.expanded = open;
        if (open) {
            closeFeature();
            swapsExpanded = false;
            swapWaiting = -1;
            slotLockExpanded = false;
            damageTintExpanded = false;
            negFxExpanded = false;
            b.ch.library.refresh();
            AttackSounds.preloadSelected(b.ch);
        } else {
            SoundPlayer.stopPreview();
        }
    }

    private void toggleSoundEnabled(SoundChannel ch) {
        ch.toggleEnabled();
        ch.save();
        if (ch.enabled()) AttackSounds.preloadSelected(ch);
    }

    // ================================================================ Красный тик урона

    private static final String[] TINT_NAMES = {"Красный", "Оранжевый", "Жёлтый", "Зелёный", "Синий", "Фиолетовый", "Белый"};
    private static final int[] TINT_COLORS = {0xFF0000, 0xFF8000, 0xFFE020, 0x30E030, 0x3070FF, 0xA040FF, 0xFFFFFF};

    private int drawDamageTint(DrawContext ctx, int x, int y, int w) {
        TextRenderer tr = textRenderer;
        SpotHelperConfig cfg = SpotHelperConfig.INSTANCE;
        final int headH = 40;
        int arrowW = 22;
        int tx = x + w - 8 - UiToggle.W;
        int ax = tx - 4 - arrowW;

        UiCard.draw(ctx, tr, in, x, y, w, headH, "Подсветка урона",
                "Свой цвет, прозрачность и длительность вспышки при уроне", UiToggle.W + arrowW + 16,
                this::toggleDamageTintExpanded);
        UiToggle.draw(ctx, in, tx, y + 6, cfg.isDamageTintEnabled(), true, this::toggleDamageTint);
        UiChip.draw(ctx, tr, in, ax, y + 5, arrowW, 16, damageTintExpanded ? "▲" : "▼", damageTintExpanded, true,
                this::toggleDamageTintExpanded);
        if (!bodyVisible("dt", damageTintExpanded)) return headH;

        int by = y + headH + 4;
        int iw = w - 16;
        int rx = x + 8;
        int rows = 1;
        {
            int cx0 = 0;
            for (String n : TINT_NAMES) {
                int cw = UiChip.width(tr, n);
                if (cx0 + cw > iw && cx0 > 0) { rows++; cx0 = 0; }
                cx0 += cw + 4;
            }
        }
        int bodyH = 8 + 12 + rows * 24 + 4 + (UiSlider.H + 8) * 2 + 4;
        UiDraw.box(ctx, x, by, w, bodyH, c(Role.CARD_BORDER), c(Role.CARD_BG));

        int cy = by + 8;
        UiDraw.text(ctx, tr, "Цвет", rx, cy, c(Role.TEXT_MAIN));
        cy += 12;
        int cx = rx;
        for (int i = 0; i < TINT_NAMES.length; i++) {
            int cw = UiChip.width(tr, TINT_NAMES[i]);
            if (cx + cw > rx + iw && cx > rx) { cx = rx; cy += 24; }
            final int rgb = TINT_COLORS[i];
            boolean on = (cfg.damageTintColor & 0xFFFFFF) == rgb;
            UiChip.draw(ctx, tr, in, cx, cy, cw, 18, TINT_NAMES[i], on, true, () -> {
                cfg.damageTintColor = 0xFF000000 | rgb;
                cfg.save();
                com.spothelper.damagetint.DamageTintManager.reapply();
            });
            UiDraw.rect(ctx, cx + 3, cy + 20, cw - 6, 2, 0xFF000000 | rgb);
            cx += cw + 4;
        }
        cy += 24 + 4;

        sync(tintAlpha, Math.round(cfg.damageTintAlpha * 100f));
        tintAlpha.draw(ctx, tr, in, rx, cy, iw, true);
        cy += UiSlider.H + 8;
        sync(tintDuration, cfg.damageTintDurationTicks);
        tintDuration.draw(ctx, tr, in, rx, cy, iw, true);

        return headH + 4 + bodyH;
    }

    private void toggleDamageTintExpanded() {
        damageTintExpanded = !damageTintExpanded;
        if (damageTintExpanded) { closeFeature(); swapsExpanded = false; swapWaiting = -1; slotLockExpanded = false; negFxExpanded = false; collapseSoundBlocks(); }
    }

    private void toggleNegFxExpanded() {
        negFxExpanded = !negFxExpanded;
        if (negFxExpanded) { closeFeature(); swapsExpanded = false; swapWaiting = -1; slotLockExpanded = false; damageTintExpanded = false; collapseSoundBlocks(); }
    }

    private void toggleNegFx() {
        SpotHelperConfig cfg = SpotHelperConfig.INSTANCE;
        cfg.negEffectsEnabled = cfg.negEffectsEnabled == 1 ? 0 : 1;
        cfg.save();
    }

    private void toggleNegFxBit(int bit) {
        SpotHelperConfig cfg = SpotHelperConfig.INSTANCE;
        cfg.negEffectsMask ^= bit;
        cfg.save();
    }

    private static final String[] NEG_NAMES = {
            "Слепота", "Тошнота", "Тряска камеры при уроне", "Тьма (в т.ч. Варден)",
            "Покачивание камеры при ходьбе", "Искажения в портале", "Виньетка по краям экрана", "Вспышки молний"};
    private static final int[] NEG_BITS = {
            com.spothelper.negeffects.NegativeEffects.BLINDNESS, com.spothelper.negeffects.NegativeEffects.NAUSEA,
            com.spothelper.negeffects.NegativeEffects.DAMAGE_TILT, com.spothelper.negeffects.NegativeEffects.DARKNESS,
            com.spothelper.negeffects.NegativeEffects.VIEW_BOB, com.spothelper.negeffects.NegativeEffects.PORTAL,
            com.spothelper.negeffects.NegativeEffects.VIGNETTE, com.spothelper.negeffects.NegativeEffects.LIGHTNING};

    private int drawNegFx(DrawContext ctx, int x, int y, int w) {
        TextRenderer tr = textRenderer;
        SpotHelperConfig cfg = SpotHelperConfig.INSTANCE;
        final int headH = 40;
        int arrowW = 22;
        int tx = x + w - 8 - UiToggle.W;
        int ax = tx - 4 - arrowW;

        UiCard.draw(ctx, tr, in, x, y, w, headH, "Отключение негативных эффектов",
                "Отключает мешающие визуальные эффекты, не изменяя игровую механику", UiToggle.W + arrowW + 16,
                this::toggleNegFxExpanded);
        UiToggle.draw(ctx, in, tx, y + 6, cfg.negEffectsEnabled == 1, true, this::toggleNegFx);
        UiChip.draw(ctx, tr, in, ax, y + 5, arrowW, 16, negFxExpanded ? "▲" : "▼", negFxExpanded, true,
                this::toggleNegFxExpanded);
        if (!bodyVisible("nf", negFxExpanded)) return headH;

        final int rowH = 22;
        int by = y + headH + 4;
        int iw = w - 16;
        int rx = x + 8;
        int bodyH = 8 + rowH + 6 + NEG_NAMES.length * rowH + 4;
        UiDraw.box(ctx, x, by, w, bodyH, c(Role.CARD_BORDER), c(Role.CARD_BG));

        int cy = by + 8;
        UiDraw.text(ctx, tr, "Отключать негативные эффекты", rx, cy + 4, c(Role.TEXT_MAIN));
        UiToggle.draw(ctx, in, rx + iw - UiToggle.W, cy, cfg.negEffectsEnabled == 1, true, this::toggleNegFx);
        cy += rowH + 6;
        boolean master = cfg.negEffectsEnabled == 1;
        for (int i = 0; i < NEG_NAMES.length; i++) {
            final int bit = NEG_BITS[i];
            UiDraw.text(ctx, tr, UiDraw.fit(tr, NEG_NAMES[i], iw - UiToggle.W - 8), rx, cy + 4,
                    c(master ? Role.TEXT_MAIN : Role.TEXT_DIM));
            UiToggle.draw(ctx, in, rx + iw - UiToggle.W, cy, (cfg.negEffectsMask & bit) != 0, true,
                    () -> toggleNegFxBit(bit));
            cy += rowH;
        }
        return headH + 4 + bodyH;
    }

    private void toggleDamageTint() {
        SpotHelperConfig cfg = SpotHelperConfig.INSTANCE;
        cfg.damageTintEnabled = cfg.isDamageTintEnabled() ? 0 : 1;
        cfg.save();
        com.spothelper.damagetint.DamageTintManager.reapply();
    }

    // ================================================================ Панель настроек модуля

    private int drawModulePanel(DrawContext ctx, int x, int top, int w) {
        TextRenderer tr = textRenderer;
        boolean shulker = module == Module.SHULKER;
        boolean healthMod = module == Module.HEALTH;
        boolean on = shulker ? MenuBridge.shulkerOn()
                : healthMod ? MenuBridge.healthOn() : MenuBridge.gammaOn();
        int y = top;

        UiDraw.text(ctx, tr, UiDraw.fit(tr, module.title, w - UiToggle.W - 10), x, y + 2, c(Role.HIGHLIGHT));
        UiDraw.text(ctx, tr, UiDraw.fit(tr, module.desc, w - UiToggle.W - 10), x, y + 15, c(Role.TEXT_DIM));
        UiToggle.draw(ctx, in, x + w - UiToggle.W, y + 4, on, true,
                shulker ? this::toggleShulker : healthMod ? this::toggleHealth : this::toggleGamma);
        y += 34;
        return y - top + drawModuleSettings(ctx, x, y, w, module);
    }

    /** Настройки модуля без заголовка: используются и старой панелью, и раскрытой функцией. */
    private int drawModuleSettings(DrawContext ctx, int x, int top, int w, Module m) {
        TextRenderer tr = textRenderer;
        boolean shulker = m == Module.SHULKER;
        boolean healthMod = m == Module.HEALTH;
        boolean on = shulker ? MenuBridge.shulkerOn()
                : healthMod ? MenuBridge.healthOn() : MenuBridge.gammaOn();
        int y = top;

        if (healthMod) {
            UiCard.block(ctx, tr, in, x, y, w, 50, "Где показывать", on);
            String hw = "Над сущностями";
            String hh = "В HUD: цель под прицелом";
            int hHalf = (w - 16 - 6) / 2;
            int ww1 = Math.min(UiChip.width(tr, hw) + 6, hHalf);
            int ww2 = Math.min(UiChip.width(tr, hh) + 6, hHalf);
            UiChip.draw(ctx, tr, in, x + 8, y + 24, ww1, 18, hw, MenuBridge.healthWorld(), on, () -> {
                MenuBridge.setHealthWorld(!MenuBridge.healthWorld());
                MenuBridge.save();
            });
            UiChip.draw(ctx, tr, in, x + 8 + ww1 + 6, y + 24, ww2, 18, hh, MenuBridge.healthHud(), on, () -> {
                MenuBridge.setHealthHud(!MenuBridge.healthHud());
                MenuBridge.save();
            });
            y += 58;

            UiCard.block(ctx, tr, in, x, y, w, 50, "Радиус", on);
            sync(healthRange, MenuBridge.healthRange());
            healthRange.draw(ctx, tr, in, x + 8, y + 27, w - 16, on);
            y += 58;
        } else if (shulker) {
            UiCard.block(ctx, tr, in, x, y, w, 50, "Допустимо шалкеров в инвентаре", on);
            sync(shulkerAllowed, MenuBridge.shulkerAllowed());
            shulkerAllowed.draw(ctx, tr, in, x + 8, y + 27, w - 16, on);
            y += 58;

            String[] what = {"Команды", "Кнопка «Отключиться»", "Закрытие окна"};
            boolean[] state = {MenuBridge.shulkerCommands(), MenuBridge.shulkerMenu(), MenuBridge.shulkerWindow()};
            Runnable[] flip = {
                    () -> { MenuBridge.setShulkerCommands(!MenuBridge.shulkerCommands()); MenuBridge.save(); },
                    () -> { MenuBridge.setShulkerMenu(!MenuBridge.shulkerMenu()); MenuBridge.save(); },
                    () -> { MenuBridge.setShulkerWindow(!MenuBridge.shulkerWindow()); MenuBridge.save(); }
            };
            int rows = 1;
            int sx = x + 8;
            for (String label : what) {
                int cw = Math.min(UiChip.width(tr, label), w - 16);
                if (sx + cw > x + w - 8 && sx > x + 8) {
                    sx = x + 8;
                    rows++;
                }
                sx += cw + 6;
            }
            int blockH = 28 + rows * 24;
            UiCard.block(ctx, tr, in, x, y, w, blockH, "Что блокировать", on);
            sx = x + 8;
            int cy = y + 24;
            for (int i = 0; i < what.length; i++) {
                int cw = Math.min(UiChip.width(tr, what[i]), w - 16);
                if (sx + cw > x + w - 8 && sx > x + 8) {
                    sx = x + 8;
                    cy += 24;
                }
                UiChip.draw(ctx, tr, in, sx, cy, cw, 18, what[i], state[i], on, flip[i]);
                sx += cw + 6;
            }
            y += blockH + 8;

            UiCard.block(ctx, tr, in, x, y, w, 62, "Команды (" + MenuBridge.shulkerCommandCount() + ")", on);
            UiDraw.text(ctx, tr, UiDraw.fit(tr, MenuBridge.shulkerCommandPreview(), w - 16), x + 8, y + 22,
                    c(Role.TEXT_DIM));
            String edit = "Изменить список ›";
            UiChip.draw(ctx, tr, in, x + 8, y + 36, Math.min(UiChip.width(tr, edit), w - 16), 18, edit, false, on,
                    () -> MenuBridge.openShulkerCommands(this));
            y += 70;
        } else {
            UiCard.block(ctx, tr, in, x, y, w, 50, "Уровень яркости", on);
            sync(gamma, MenuBridge.gammaLevel());
            gamma.draw(ctx, tr, in, x + 8, y + 27, w - 16, on);
            y += 58;
        }
        return y - top;
    }

    private void drawFooter(DrawContext ctx) {
        TextRenderer tr = textRenderer;
        int fy = viewB + 8;
        String back = "‹ Назад";
        String reset = "Сбросить";
        int bw = UiChip.width(tr, back);
        int rw = UiChip.width(tr, reset);
        UiChip.draw(ctx, tr, in, viewL, fy, bw, 20, back, false, true, () -> {
            module = null;
            scroll = 0;
        });
        UiChip.draw(ctx, tr, in, viewR - rw, fy, rw, 20, reset, false, true, this::resetModule);
    }

    private void openModule(Module m) {
        module = m;
        scroll = 0;
    }

    private void resetModule() {
        resetModule(module);
    }

    private void resetModule(Module m) {
        if (m == Module.GAMMA) {
            MenuBridge.resetGamma();
        } else if (m == Module.SHULKER) {
            MenuBridge.resetShulker();
        } else if (m == Module.HEALTH) {
            MenuBridge.resetHealth();
        }
    }

    // ================================================================ Оформление

    private int drawTheme(DrawContext ctx, int x, int top, int w) {
        TextRenderer tr = textRenderer;
        int y = top;

        // 1. пресеты
        int cur = UiTheme.presetIndex();
        int cx = x;
        int ch = 18;
        for (int i = 0; i <= UiTheme.CUSTOM; i++) {
            boolean custom = i == UiTheme.CUSTOM;
            String name = custom ? UiTheme.CUSTOM_NAME : UiTheme.PRESET_NAMES[i];
            int cw = UiChip.width(tr, name);
            if (cx + cw > x + w && cx > x) {
                cx = x;
                y += ch + 6;
            }
            final int idx = i;
            UiChip.draw(ctx, tr, in, cx, y, cw, ch, name, i == cur, !custom || cur == UiTheme.CUSTOM,
                    custom ? null : () -> UiTheme.applyPreset(idx));
            cx += cw + 6;
        }
        y += ch + 12;

        boolean wide = w >= 460;
        int leftW = wide ? w * 55 / 100 : w;
        int ly = y;

        // 2. список ролей
        int rowH = 18;
        for (int i = 0; i < ROLES.length; i++) {
            final int idx = i;
            final Role role = ROLES[i];
            UiColorRow.draw(ctx, tr, in, x, ly, leftW, rowH, role, i == selectedRole, () -> {
                selectedRole = idx;
                MenuBridge.openColorPicker(this, UiTheme.raw(role), rgb -> UiTheme.set(role, rgb));
            });
            ly += rowH + 2;
        }
        ly += 6;

        // 4. прозрачность окна
        UiDraw.text(ctx, tr, UiDraw.fit(tr, "Прозрачность окна", leftW), x, ly, c(Role.TEXT_MAIN));
        ly += 12;
        if (in.dragging != opacity) opacity.setValue(UiTheme.opacity());
        opacity.draw(ctx, tr, in, x, ly, leftW, true);
        ly += UiSlider.H + 8;

        // 5. кнопки
        String[] names = {"Сбросить роль", "Сбросить всё", "Скопировать тему", "Вставить тему"};
        Runnable[] acts = {
                () -> {
                    if (selectedRole >= 0) UiTheme.resetRole(ROLES[selectedRole]);
                },
                UiTheme::resetAll,
                this::copyTheme,
                this::pasteTheme
        };
        boolean[] enabled = {selectedRole >= 0, true, true, true};
        ly = flowChips(ctx, x, ly, leftW, names, acts, enabled);

        if (System.currentTimeMillis() < toastUntil) {
            UiDraw.text(ctx, tr, UiDraw.fit(tr, toast, leftW), x, ly + 2, c(toastOk ? Role.OK : Role.ERROR));
            ly += 14;
        }

        // 6. контроль читаемости
        if (UiTheme.lowContrast()) {
            UiDraw.text(ctx, tr, UiDraw.fit(tr, "Низкий контраст текста", leftW), x, ly + 2, UiTheme.WARNING);
            ly += 14;
        }

        // 3. живой предпросмотр
        int bottom = ly;
        if (wide) {
            int px = x + leftW + 12;
            drawPreview(ctx, px, y, w - leftW - 12, 118);
            bottom = Math.max(bottom, y + 118);
        } else {
            int pw = Math.min(w, 240);
            drawPreview(ctx, x, ly + 10, pw, 118);
            bottom = ly + 10 + 118;
        }
        return bottom - top;
    }

    /** Уменьшенное окно из текущих цветов темы: сайдбар, две карточки, переключатели, слайдер, чипы. */
    private void drawPreview(DrawContext ctx, int x, int y, int w, int h) {
        TextRenderer tr = textRenderer;
        int sw = Math.max(34, w / 4);

        UiDraw.rect(ctx, x, y, w, h, c(Role.WINDOW_BG));
        UiDraw.rect(ctx, x, y, sw, h, c(Role.SIDEBAR_BG));
        UiDraw.frame(ctx, x, y, w, h, c(Role.ACCENT));

        UiDraw.text(ctx, tr, UiDraw.fit(tr, "Spot", sw - 8), x + 4, y + 4, c(Role.HIGHLIGHT));
        String[] nav = {"Меню", "Метки", "HUD"};
        for (int n = 0; n < nav.length; n++) {
            final int idx = n;
            int ny = y + 16 + n * 13;
            boolean sel = pvNav == n;
            boolean hov = in.mouseOver(x + 3, ny, sw - 6, 11);
            if (sel) {
                UiDraw.rect(ctx, x + 3, ny, sw - 6, 11, UiTheme.navActive());
            } else if (hov) {
                UiDraw.rect(ctx, x + 3, ny, sw - 6, 11, UiTheme.hover(c(Role.SIDEBAR_BG)));
            }
            UiDraw.text(ctx, tr, UiDraw.fit(tr, nav[n], sw - 12), x + 6, ny + 2,
                    c(sel || hov ? Role.HIGHLIGHT : Role.TEXT_MAIN));
            in.addClick(x + 3, ny, sw - 6, 11, () -> pvNav = idx);
        }

        int cx = x + sw + 6;
        int cw = w - sw - 12;
        if (cw < 40) return;

        // карточка 1: переключатель (клик по карточке или по переключателю)
        UiCard.draw(ctx, tr, in, cx, y + 6, cw, 34, "Карточка", "Описание", 36, () -> pvA = !pvA);
        UiToggle.draw(ctx, in, cx + cw - 6 - UiToggle.W, y + 9, pvA, true, () -> pvA = !pvA);

        // карточка 2: переключатель + рабочий слайдер
        UiCard.draw(ctx, tr, in, cx, y + 44, cw, 46, "Слайдер", null, 36, () -> pvB = !pvB);
        UiToggle.draw(ctx, in, cx + cw - 6 - UiToggle.W, y + 47, pvB, true, () -> pvB = !pvB);
        pvSlider.draw(ctx, tr, in, cx + 6, y + 66, cw - 12, true);

        // чипы: выбираются кликом
        int chipW = Math.min(40, Math.max(24, cw / 3));
        UiChip.draw(ctx, tr, in, cx, y + 96, chipW, 14, "Чип", pvChip == 0, true, () -> pvChip = 0);
        UiChip.draw(ctx, tr, in, cx + chipW + 4, y + 96, chipW, 14, "Чип", pvChip == 1, true, () -> pvChip = 1);
        int dx = cx + cw - 8;
        UiDraw.rect(ctx, dx, y + 99, 8, 8, c(Role.ERROR));
        UiDraw.rect(ctx, dx - 12, y + 99, 8, 8, c(Role.OK));
    }

    private void copyTheme() {
        if (client == null) return;
        client.keyboard.setClipboard(UiTheme.export());
        toast("Тема скопирована в буфер обмена", true);
    }

    private void pasteTheme() {
        if (client == null) return;
        if (UiTheme.importString(client.keyboard.getClipboard())) {
            toast("Тема применена", true);
        } else {
            toast("Неверный формат темы (нужно SH1:…)", false);
        }
    }

    private void toast(String text, boolean ok) {
        toast = text;
        toastOk = ok;
        toastUntil = System.currentTimeMillis() + 3000;
    }

    // ================================================================ действия


    private void toggleHud() {
        MenuBridge.setHudOn(!MenuBridge.hudOn());
        MenuBridge.save();
    }

    private void toggleFreeLook() {
        MenuBridge.setFreeLookOn(!MenuBridge.freeLookOn());
        MenuBridge.save();
    }

    private void toggleZoom() {
        MenuBridge.setZoomOn(!MenuBridge.zoomOn());
        MenuBridge.save();
    }

    private void toggleF5Zoom() {
        MenuBridge.setF5ZoomOn(!MenuBridge.f5ZoomOn());
        MenuBridge.save();
    }

    private void toggleCrosshair() {
        MenuBridge.setCrosshairOn(!MenuBridge.crosshairOn());
        MenuBridge.save();
    }

    private void toggleHitboxes() {
        MenuBridge.setHitboxesOn(!MenuBridge.hitboxesOn());
        MenuBridge.save();
    }

    private void toggleTntTimer() {
        MenuBridge.setTntOn(!MenuBridge.tntOn());
        MenuBridge.save();
    }

    private void toggleAutoSprint() {
        MenuBridge.setAutoSprintOn(!MenuBridge.autoSprintOn());
        MenuBridge.save();
    }

    private void toggleArmor() {
        MenuBridge.setArmorOn(!MenuBridge.armorOn());
        MenuBridge.save();
    }

    private void toggleReply() {
        MenuBridge.setReplyOn(!MenuBridge.replyOn());
        MenuBridge.save();
    }

    private void toggleWpWorld() {
        MenuBridge.setWpWorldOn(!MenuBridge.wpWorldOn());
        MenuBridge.save();
    }

    private void toggleTotemColor() {
        MenuBridge.setTotemColorsOn(!MenuBridge.totemColorsOn());
        MenuBridge.save();
    }


    private void toggleAnarchy() {
        MenuBridge.setAnarchyOn(!MenuBridge.anarchyOn());
        MenuBridge.save();
    }

    private void toggleHeight() {
        MenuBridge.setHeightOn(!MenuBridge.heightOn());
        MenuBridge.save();
    }


    private void toggleHealth() {
        MenuBridge.setHealthOn(!MenuBridge.healthOn());
        MenuBridge.save();
    }

    private void toggleShulker() {
        MenuBridge.setShulkerOn(!MenuBridge.shulkerOn());
        MenuBridge.save();
    }

    private void toggleGamma() {
        MenuBridge.setGammaOn(!MenuBridge.gammaOn());
        MenuBridge.save();
    }

    // ================================================================ утилиты

    /** Подтягивает значение слайдера из конфига, пока его не тянут. */
    private void sync(UiSlider s, int value) {
        if (in.dragging != s) s.setValue(value);
    }

    /** Чипы в строку с переносом. enabled может быть null. Возвращает y под последней строкой. */
    private int flowChips(DrawContext ctx, int x, int y, int w, String[] labels, Runnable[] actions, boolean[] enabled) {
        TextRenderer tr = textRenderer;
        int cx = x;
        for (int i = 0; i < labels.length; i++) {
            int cw = Math.min(UiChip.width(tr, labels[i]), w);
            if (cx + cw > x + w && cx > x) {
                cx = x;
                y += 24;
            }
            UiChip.draw(ctx, tr, in, cx, y, cw, 18, labels[i], false, enabled == null || enabled[i], actions[i]);
            cx += cw + 6;
        }
        return y + 18 + 6;
    }

    private static String hearts(int hp) {
        return hp % 2 == 0 ? String.valueOf(hp / 2) : (hp / 2) + ".5";
    }

    private static String fmtCoin(double v) {
        return String.format(Locale.ROOT, "%,.2f", v).replace(',', ' ');
    }
}