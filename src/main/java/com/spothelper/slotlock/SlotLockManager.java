package com.spothelper.slotlock;

import com.spothelper.config.SpotHelperConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;

/**
 * Блокировка слотов инвентаря игрока (индексы 0–35: 0–8 хотбар, 9–35 основной).
 * Маска режима хранится отдельно для каждого слота: 1 — выброс, 2 — перемещение, 4 — продажа (/ah sell продаёт предмет из руки).
 */
public final class SlotLockManager {
    public static final int MODE_DROP = 1;
    public static final int MODE_MOVE = 2;
    public static final int MODE_SELL = 4;

    private static long lastNotice;

    private SlotLockManager() {}

    private static SpotHelperConfig cfg() { return SpotHelperConfig.INSTANCE; }

    /** Маска запретов слота (0 — слот свободен или защита выключена). */
    public static int modeOf(int slot) {
        SpotHelperConfig c = cfg();
        if (!c.isSlotLockEnabled() || slot < 0 || slot > 35) return 0;
        return c.slotModes[slot] & 7;
    }

    public static boolean isLocked(int slot) { return modeOf(slot) != 0; }

    /** Маска слота независимо от общего переключателя (для GUI). */
    public static int rawMode(int slot) {
        return slot < 0 || slot > 35 ? 0 : cfg().slotModes[slot] & 7;
    }

    public static boolean isMarked(int slot) { return rawMode(slot) != 0; }

    public static boolean canDrop(int slot) { return (modeOf(slot) & MODE_DROP) == 0; }
    public static boolean canMove(int slot) { return (modeOf(slot) & MODE_MOVE) == 0; }
    public static boolean canSell(int slot) { return (modeOf(slot) & MODE_SELL) == 0; }

    public static int count() {
        int n = 0;
        for (int i = 0; i < 36; i++) if (cfg().slotModes[i] != 0) n++;
        return n;
    }

    /** Есть ли хоть один слот с запретом перемещения. */
    private static boolean anyMoveLock() {
        for (int i = 0; i < 36; i++) if ((modeOf(i) & MODE_MOVE) != 0) return true;
        return false;
    }

    /** Alt+клик: слот без защиты получает полный набор запретов, защищённый — освобождается. */
    public static void toggle(int slot) {
        if (slot < 0 || slot > 35) return;
        cfg().slotModes[slot] = rawMode(slot) == 0 ? 7 : 0;
        cfg().save();
    }

    public static void toggleBit(int slot, int bit) {
        if (slot < 0 || slot > 35) return;
        cfg().slotModes[slot] = rawMode(slot) ^ bit;
        cfg().save();
    }

    public static void setMode(int slot, int mode) {
        if (slot < 0 || slot > 35) return;
        cfg().slotModes[slot] = mode & 7;
        cfg().save();
    }

    public static void setAll(int mode) {
        for (int i = 0; i < 36; i++) cfg().slotModes[i] = mode & 7;
        cfg().save();
    }

    public static void clearAll() { setAll(0); }

    /** Индекс слота в инвентаре игрока или -1, если слот принадлежит контейнеру. */
    public static int playerIndex(Slot s) {
        if (s == null || !(s.inventory instanceof PlayerInventory)) return -1;
        int i = s.getIndex();
        return i >= 0 && i <= 35 ? i : -1;
    }

    /** Клик по инвентарю (ClientPlayerInteractionManager.clickSlot): true — отменить. */
    public static boolean blocksClick(PlayerEntity player, int slotId, int button, SlotActionType type) {
        if (!cfg().isSlotLockEnabled() || count() == 0 || player == null) return false;
        ScreenHandler h = player.currentScreenHandler;

        // двойной клик «собрать всё» вытянет предметы и из заблокированных слотов
        if (type == SlotActionType.PICKUP_ALL && anyMoveLock()) {
            notice(player);
            return true;
        }

        if (h != null && slotId >= 0 && slotId < h.slots.size()) {
            int idx = playerIndex(h.slots.get(slotId));
            if (idx >= 0 && isLocked(idx)) {
                boolean deny = type == SlotActionType.THROW ? !canDrop(idx) : !canMove(idx);
                if (deny) { notice(player); return true; }
            }
        }
        // обмен с хотбаром по цифре: кнопка 0–8 = слот хотбара
        if (type == SlotActionType.SWAP && button >= 0 && button <= 8 && !canMove(button)) {
            notice(player);
            return true;
        }
        return false;
    }

    /** Команда /ah sell продаёт предмет из руки: блокируем, если выбранный слот хотбара заблокирован. */
    public static boolean blocksSellCommand(String command) {
        if (command == null || !cfg().isSlotLockEnabled()) return false;
        String c = command.trim().toLowerCase(java.util.Locale.ROOT);
        if (c.startsWith("/")) c = c.substring(1);
        if (!(c.equals("ah sell") || c.startsWith("ah sell "))) return false;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return false;
        if (canSell(mc.player.getInventory().getSelectedSlot())) return false;
        notice(mc.player);
        return true;
    }

    /** Вызывать в начале клиентского тика: гасит нажатия Q, пока выбранный слот защищён от выброса. */
    public static void tickStart(MinecraftClient mc) {
        if (mc == null || mc.player == null || !cfg().isSlotLockEnabled()) return;
        if (canDrop(mc.player.getInventory().getSelectedSlot())) return;
        boolean any = false;
        while (mc.options.dropKey.wasPressed()) any = true;
        if (any) notice(mc.player);
    }

    private static void notice(PlayerEntity player) {
        long now = System.currentTimeMillis();
        if (now - lastNotice < 1000) return;
        lastNotice = now;
        player.sendMessage(Text.literal("§cSpotHelper: слот заблокирован"), true);
    }
}
