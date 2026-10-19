package com.spothelper.sellprotect;

import com.spothelper.client.SpotHelperClient;
import com.spothelper.config.SpotHelperConfig;
import com.spothelper.util.NumberFormat;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

/**
 * Защита от продажи для обычной команды /ah sell в любом виде
 * (/ah sell 123, /ah sell auto confirm, /ah sell 123 full, /ah sell 123 12 4m и т.д.).
 * Команды самого мода (/sah sell) отправляются через {@link #sendRaw} и повторно не проверяются.
 */
public final class SellGuard {
    private static boolean bypass;

    private SellGuard() {}

    /** Отправляет команду без проверки защитой. */
    public static void sendRaw(MinecraftClient mc, String command) {
        bypass = true;
        try {
            mc.player.networkHandler.sendChatCommand(command);
        } finally {
            bypass = false;
        }
    }

    /** true — команду нужно отменить (показано окно подтверждения). */
    public static boolean intercept(String command) {
        if (bypass || command == null) return false;
        String c = command.trim();
        if (c.startsWith("/")) c = c.substring(1);
        String[] t = c.split("\\s+");
        if (t.length < 2 || !t[0].equalsIgnoreCase("ah") || !t[1].equalsIgnoreCase("sell")) return false;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return false;
        ItemStack held = mc.player.getMainHandStack();
        if (held.isEmpty()) return false;
        SpotHelperConfig cfg = SpotHelperConfig.INSTANCE;
        if (!cfg.isSellProtected(held)) return false;

        // цена — первое целое число после "sell" (дальше идут количество и время вроде 4m)
        long total = 0;
        for (int i = 2; i < t.length; i++) {
            if (t[i].matches("\\d{1,10}")) { total = Long.parseLong(t[i]); break; }
        }
        int count = Math.max(1, held.getCount());
        int sum = (int) Math.min(Integer.MAX_VALUE, total);
        int unit = sum / count;
        if (cfg.matchesLastConfirmed(held, unit)) return false;

        final String cmd = c;
        final ItemStack shown = held.copy();
        final String name = held.getName().getString();
        try {
            SpotHelperClient.pendingScreen = new SellConfirmScreen(mc.currentScreen, shown, unit, count, sum, () -> {
                cfg.rememberConfirmed(shown, unit);
                cfg.save();
                MinecraftClient m = MinecraftClient.getInstance();
                if (m.player == null) return;
                sendRaw(m, cmd);
                m.player.sendMessage(Text.literal("§aSpotHelper: §f" + name + " §7× §f" + NumberFormat.format(count)
                        + " шт. §7(отправлено /" + cmd + ")"), false);
            });
        } catch (Throwable th) {
            com.spothelper.util.Log.error("sell command", th);
            return false;
        }
        return true;
    }
}
