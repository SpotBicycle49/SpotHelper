package com.spothelper.waypoints;

import com.spothelper.config.SpotHelperConfig;
import com.spothelper.gui.ui.UiCard;
import com.spothelper.gui.ui.UiDraw;
import com.spothelper.gui.ui.UiField;
import com.spothelper.gui.ui.UiScreen;
import com.spothelper.gui.ui.UiTheme.Role;
import com.spothelper.gui.ui.UiToggle;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Метки: добавление, список по измерениям, видимость, цвет, удаление. Оформление — как в главном меню. */
public class WaypointScreen extends UiScreen {

    private static final Pattern NUM = Pattern.compile("-?\\d+");

    private static final String[] TAB_NAMES = {"Верхний мир", "Незер", "Энд", "Другие"};
    private static final String[] TAB_DIMS = {
            WaypointManager.OVERWORLD, WaypointManager.NETHER, WaypointManager.END, null};

    private final UiField nameField = new UiField("Название", 24);
    private final UiField xField = new UiField("X", 9).signed();
    private final UiField yField = new UiField("Y", 9).signed();
    private final UiField zField = new UiField("Z", 9).signed();

    private int dimTab;

    public WaypointScreen(Screen parent) {
        super(parent, "Метки");
        field(nameField);
        field(xField);
        field(yField);
        field(zField);
        nameField.onEnter = this::tryAdd;
        xField.onEnter = this::tryAdd;
        yField.onEnter = this::tryAdd;
        zField.onEnter = this::tryAdd;

        WaypointManager.purgeExpired();
        MinecraftClient mc = MinecraftClient.getInstance();
        dimTab = mc.world != null ? WaypointManager.kind(WaypointManager.dimId()) : 0;
        if (mc.player != null) fillHere();
    }

    private static List<Waypoint> tabList(int tab) {
        List<Waypoint> out = new ArrayList<>();
        for (Waypoint w : WaypointManager.forServer()) {
            if (WaypointManager.kind(w.dim) == tab) out.add(w);
        }
        return out;
    }

    // ---------------------------------------------------------------- отрисовка

    @Override
    protected int drawBody(DrawContext ctx, int x, int top, int w) {
        TextRenderer tr = textRenderer;
        SpotHelperConfig cfg = SpotHelperConfig.INSTANCE;
        int y = top;

        UiDraw.text(ctx, tr, UiDraw.fit(tr, "Сервер: " + WaypointManager.serverKey()
                + " · сейчас в: " + WaypointManager.dimId(), w), x, y, c(Role.TEXT_DIM));
        y += 16;

        // ---- форма
        y += label(ctx, x, y, "Новая метка");
        int coordW = 52;
        int nameW = Math.max(60, w - (coordW + 6) * 3);
        nameField.draw(ctx, in, x, y, nameW, 20, true);
        xField.draw(ctx, in, x + nameW + 6, y, coordW, 20, true);
        yField.draw(ctx, in, x + nameW + 6 + (coordW + 6), y, coordW, 20, true);
        zField.draw(ctx, in, x + nameW + 6 + (coordW + 6) * 2, y, coordW, 20, true);
        y += 26;

        y = flowChips(ctx, x, y, w,
                new String[]{"+ Добавить", "Моя позиция", "Из буфера"},
                new Runnable[]{this::tryAdd, this::fillHere, this::fillFromClipboard},
                new boolean[]{true, false, false}, null);

        // ---- настройки
        toggleCard(ctx, x, y, w, 28, "Метки на миникарте", null,
                cfg.isMinimapWaypoints(), () -> flip(() -> cfg.minimapWaypoints ^= 1));
        y += 34;
        toggleCard(ctx, x, y, w, 28, "Метка при клике на ивент", null,
                cfg.isEventWaypoint(), () -> flip(() -> cfg.eventWaypoint ^= 1));
        y += 34;
        y = flowChips(ctx, x, y, w,
                new String[]{"Удалить метки ивентов"},
                new Runnable[]{() -> {
                    int n = WaypointManager.clearEvents();
                    toast(n > 0 ? "Удалено меток ивентов: " + n : "Меток ивентов нет", n > 0);
                }}, null, null);

        // ---- вкладки по измерениям
        String[] tabs = new String[4];
        Runnable[] acts = new Runnable[4];
        boolean[] act = new boolean[4];
        for (int t = 0; t < 4; t++) {
            final int tab = t;
            tabs[t] = TAB_NAMES[t] + " (" + tabList(t).size() + ")";
            acts[t] = () -> dimTab = tab;
            act[t] = dimTab == t;
        }
        y = flowChips(ctx, x, y, w, tabs, acts, act, null);

        // ---- список
        List<Waypoint> list = tabList(dimTab);
        if (list.isEmpty()) {
            UiDraw.text(ctx, tr, "В разделе «" + TAB_NAMES[dimTab] + "» меток нет.", x, y + 4, c(Role.TEXT_DIM));
            y += 22;
        }
        MinecraftClient mc = MinecraftClient.getInstance();
        String curDim = WaypointManager.dimId();
        for (Waypoint wp : list) {
            y += drawRow(ctx, wp, x, y, w, mc, curDim);
        }
        return y - top;
    }

    private int drawRow(DrawContext ctx, Waypoint wp, int x, int y, int w, MinecraftClient mc, String curDim) {
        TextRenderer tr = textRenderer;
        int h = 30;
        UiCard.draw(ctx, tr, in, x, y, w, h, null, null, 0, null);

        // цвет (клик — следующий)
        int cx = x + 8;
        int cy = y + 9;
        UiDraw.frame(ctx, cx - 1, cy - 1, 14, 14, c(Role.TEXT_DIM));
        UiDraw.rect(ctx, cx, cy, 12, 12, 0xFF000000 | (wp.color & 0xFFFFFF));
        in.addClick(cx - 2, cy - 2, 18, 18, () -> WaypointManager.cycleColor(wp));

        int delX = x + w - 24;
        int tglX = delX - 8 - UiToggle.W;
        int dim = c(Role.TEXT_DIM);
        int main = c(wp.visible ? Role.TEXT_MAIN : Role.TEXT_DIM);

        String coords = wp.x + " " + wp.y + " " + wp.z;
        String dist = "—";
        if (mc.player != null && wp.dim.equals(curDim)) {
            double d = Math.hypot(wp.x + 0.5 - mc.player.getX(), wp.z + 0.5 - mc.player.getZ());
            dist = WaypointRenderer.formatDist(d);
        }
        int distW = tr.getWidth(dist);
        int distX = tglX - 10 - distW;
        int coordX = Math.max(x + 90, x + (int) (w * 0.40));
        coordX = Math.min(coordX, distX - 8 - tr.getWidth(coords));
        coordX = Math.max(coordX, x + 60);

        String title = (wp.event ? "★ " : "") + wp.name;
        int ty = y + 11;
        UiDraw.text(ctx, tr, UiDraw.fit(tr, title, coordX - 8 - (x + 28)), x + 28, ty, main);
        UiDraw.text(ctx, tr, UiDraw.fit(tr, coords, distX - 6 - coordX), coordX, ty, dim);
        UiDraw.text(ctx, tr, dist, distX, ty, c(Role.HIGHLIGHT));

        UiToggle.draw(ctx, in, tglX, y + (h - UiToggle.H) / 2, wp.visible, true,
                () -> { wp.visible = !wp.visible; WaypointManager.save(); });

        boolean hov = in.mouseOver(delX, y + 4, 20, 22);
        UiDraw.text(ctx, tr, "×", delX + 7, ty, c(hov ? Role.ERROR : Role.TEXT_DIM));
        in.addClick(delX, y + 4, 20, 22, () -> WaypointManager.remove(wp));
        return h + 6;
    }

    // ---------------------------------------------------------------- действия

    private static void flip(Runnable r) {
        r.run();
        SpotHelperConfig.INSTANCE.save();
    }

    private void tryAdd() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) return;
        Integer x = parse(xField.text());
        Integer y = parse(yField.text());
        Integer z = parse(zField.text());
        if (x == null || z == null) {
            toast("Введи координаты X и Z", false);
            return;
        }

        String dim = TAB_DIMS[dimTab];
        if (dim == null) {
            String cur = WaypointManager.dimId();
            if (WaypointManager.kind(cur) == 3) {
                dim = cur;
            } else {
                toast("В «Другие» можно добавить метку, только находясь в таком измерении", false);
                return;
            }
        }

        WaypointManager.add(nameField.text(), x, y == null ? 64 : y, z, dim);
        nameField.setText("");
        toast("Метка добавлена", true);
    }

    private void fillHere() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;
        xField.setText(String.valueOf((int) Math.floor(mc.player.getX())));
        yField.setText(String.valueOf((int) Math.floor(mc.player.getY())));
        zField.setText(String.valueOf((int) Math.floor(mc.player.getZ())));
    }

    /** Берёт числа из буфера обмена: «x y z» или «x z» (EventFlow копирует именно координаты). */
    private void fillFromClipboard() {
        MinecraftClient mc = MinecraftClient.getInstance();
        String clip;
        try {
            clip = mc.keyboard.getClipboard();
        } catch (Throwable t) {
            return;
        }
        if (clip == null) return;
        List<String> nums = new ArrayList<>();
        Matcher m = NUM.matcher(clip);
        while (m.find() && nums.size() < 3) nums.add(m.group());
        if (nums.size() == 3) {
            xField.setText(nums.get(0));
            yField.setText(nums.get(1));
            zField.setText(nums.get(2));
        } else if (nums.size() == 2) {
            xField.setText(nums.get(0));
            zField.setText(nums.get(1));
        } else {
            toast("В буфере нет координат", false);
        }
    }

    private static Integer parse(String s) {
        try {
            return Integer.parseInt(s.trim());
        } catch (Exception e) {
            return null;
        }
    }
}
