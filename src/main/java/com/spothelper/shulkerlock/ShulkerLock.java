package com.spothelper.shulkerlock;

import com.spothelper.config.SpotHelperConfig;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableTextContent;
import org.lwjgl.glfw.GLFW;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public final class ShulkerLock {

    private ShulkerLock() {}

    private static boolean carrying = false;
    private static int lastCount = 0;
    private static String menuTipText = "";
    private static boolean menuLockApplied = false;
    private static long lastNotify = 0L;

    private static final String KEY_DISCONNECT = "menu.disconnect";
    private static final String KEY_RETURN_TO_MENU = "menu.returnToMenu";

    private static final Map<Character, Character> LAYOUT = new HashMap<>();
    static {
        String ru = "йцукенгшщзхъфывапролджэячсмитьбю";
        String en = "qwertyuiop[]asdfghjkl;'zxcvbnm,.";
        for (int i = 0; i < ru.length(); i++) LAYOUT.put(ru.charAt(i), en.charAt(i));
    }

    public static void init() {
        // Клавиша пока отключена — вернём, когда разберёмся с KeyBinding.Category
        // toggleKey = ...

        ClientTickEvents.END_CLIENT_TICK.register(ShulkerLock::tick);

        ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
            if (screen instanceof GameMenuScreen) {
                menuLockApplied = false;
                refreshMenuButtons(screen);
                ScreenEvents.afterTick(screen).register(ShulkerLock::refreshMenuButtons);
            }
        });
    }

    private static void tick(MinecraftClient mc) {
        carrying = mc.player != null && mc.world != null && overLimit(mc);
    }

    private static boolean isShulker(ItemStack st) {
        return st != null && !st.isEmpty()
                && st.getItem() instanceof BlockItem bi
                && bi.getBlock() instanceof ShulkerBoxBlock;
    }

    private static int countShulkers(MinecraftClient mc) {
        if (mc.player == null) return 0;
        int n = 0;
        PlayerInventory inv = mc.player.getInventory();
        for (int i = 0; i < inv.size(); i++) {
            if (isShulker(inv.getStack(i))) n++;
        }
        if (mc.player.currentScreenHandler != null
                && isShulker(mc.player.currentScreenHandler.getCursorStack())) {
            n++;
        }
        return n;
    }

    private static boolean overLimit(MinecraftClient mc) {
        lastCount = countShulkers(mc);
        return lastCount > SpotHelperConfig.INSTANCE.getShulkerLockAllowed();
    }

    private static String limitText() {
        return "шалкеров " + lastCount + ", можно до " + SpotHelperConfig.INSTANCE.getShulkerLockAllowed();
    }

    private static void notify(MinecraftClient mc, String what, boolean chat) {
        long now = System.currentTimeMillis();
        if (now - lastNotify < 700) return;
        lastNotify = now;
        if (mc.player != null) {
            mc.player.sendMessage(Text.literal("§c[SpotHelper] Заблокировано (" + what + "): " + limitText()), !chat);
        }
    }

    private static String norm(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (char ch : s.toLowerCase(Locale.ROOT).toCharArray()) {
            Character m = LAYOUT.get(ch);
            sb.append(m != null ? m : ch);
        }
        return sb.toString();
    }

    public static boolean blockCommand(String raw) {
        SpotHelperConfig cfg = SpotHelperConfig.INSTANCE;
        if (!cfg.shulkerLock || !cfg.shulkerLockCommands || cfg.shulkerLockCommandList == null || raw == null) return false;

        String first = raw.trim();
        if (first.startsWith("/")) first = first.substring(1);
        int sp = first.indexOf(' ');
        if (sp >= 0) first = first.substring(0, sp);
        int colon = first.lastIndexOf(':');
        if (colon >= 0) first = first.substring(colon + 1);
        if (first.isEmpty()) return false;

        String wanted = norm(first);
        boolean listed = false;
        for (String entry : cfg.shulkerLockCommandList) {
            if (entry == null) continue;
            String e = entry.trim();
            if (e.startsWith("/")) e = e.substring(1);
            if (!e.isEmpty() && norm(e).equals(wanted)) { listed = true; break; }
        }
        if (!listed) return false;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || !overLimit(mc)) return false;
        notify(mc, "/" + first, true);
        return true;
    }

    public static boolean blockWindowClose(MinecraftClient mc) {
        SpotHelperConfig cfg = SpotHelperConfig.INSTANCE;
        if (!cfg.shulkerLock || !cfg.shulkerLockWindow) return false;
        if (mc.player == null || mc.world == null || !carrying) return false;
        GLFW.glfwSetWindowShouldClose(mc.getWindow().getHandle(), false);
        notify(mc, "закрытие игры", false);
        return true;
    }

    private static boolean isLeaveButton(ClickableWidget w) {
        Text t = w.getMessage();
        if (t != null && t.getContent() instanceof TranslatableTextContent tc) {
            String k = tc.getKey();
            return KEY_DISCONNECT.equals(k) || KEY_RETURN_TO_MENU.equals(k);
        }
        return false;
    }

    private static void refreshMenuButtons(Screen screen) {
        SpotHelperConfig cfg = SpotHelperConfig.INSTANCE;
        boolean lock = cfg.shulkerLock && cfg.shulkerLockMenu && carrying;
        if (!lock && !menuLockApplied) return;

        String tip = "Заблокировано: " + limitText();
        boolean retip = !menuLockApplied || !tip.equals(menuTipText);

        for (ClickableWidget w : Screens.getButtons(screen)) {
            if (!isLeaveButton(w)) continue;
            if (lock) {
                w.active = false;
                if (retip) w.setTooltip(Tooltip.of(Text.literal(tip)));
            } else {
                w.active = true;
                w.setTooltip(null);
            }
        }
        menuLockApplied = lock;
        menuTipText = tip;
    }
}