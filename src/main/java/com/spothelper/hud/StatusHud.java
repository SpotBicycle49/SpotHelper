package com.spothelper.hud;

import com.spothelper.client.SpotHelperClient;
import com.spothelper.config.SpotHelperConfig;
import com.spothelper.holyworld.HolyWorldData;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.util.Identifier;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Элементы HUD: броня, эффекты, курс коина. У каждого своё положение (config: hud*X / hud*Y),
 * которое меняется перетаскиванием мышью на экране «Расположение HUD».
 */
public final class StatusHud {

    public enum Element {
        ARMOR("Броня"), EFFECTS("Эффекты"), COIN("Курс коина"), TNT("Таймеры ТНТ"), INVENTORY("Инвентарь");

        public final String label;

        Element(String label) {
            this.label = label;
        }
    }

    private record Line(ItemStack stack, Text text, int color, Identifier icon) {
        Line(ItemStack stack, Text text, int color) { this(stack, text, color, null); }
    }

    private static Identifier effectIcon(RegistryEntry<StatusEffect> e) {
        return e.getKey().map(k -> {
            Identifier id = k.getValue();
            return Identifier.of(id.getNamespace(), "mob_effect/" + id.getPath());
        }).orElse(null);
    }

    private static Line effectLine(RegistryEntry<StatusEffect> type, int amplifier, String time, int mode) {
        String lvl = " " + (amplifier + 1) + "  " + time;
        Text name = type.value().getName();
        Identifier icon = mode >= 1 ? effectIcon(type) : null;
        Text text = mode == 1 && icon != null
                ? Text.literal((amplifier + 1) + "  " + time)
                : name.copy().append(Text.literal(lvl));
        return new Line(null, text, 0xFFFFFFFF, icon);
    }

    private static final EquipmentSlot[] SLOTS = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS,
            EquipmentSlot.FEET, EquipmentSlot.OFFHAND };

    private static final int COIN_POLL_MS = 10_000;

    /** true, пока открыт экран перетаскивания: реальный HUD не рисуется (его заменяет предпросмотр). */
    public static boolean editing = false;

    private static long lastCoinPoll = 0L;

    private StatusHud() {}

    // ---------------------------------------------------------------- настройки и положение

    public static boolean enabled(Element e) {
        SpotHelperConfig c = SpotHelperConfig.INSTANCE;
        return switch (e) {
            case ARMOR -> c.statusHudArmor == 1;
            case EFFECTS -> c.statusHudEffects == 1;
            case COIN -> c.statusHudCoin == 1;
            case TNT -> c.isTntTimerEnabled() && c.isTntTimerInHud();
            case INVENTORY -> c.invHud == 1;
        };
    }

    public static int x(Element e) {
        SpotHelperConfig c = SpotHelperConfig.INSTANCE;
        return switch (e) {
            case ARMOR -> c.hudArmorX;
            case EFFECTS -> c.hudEffectsX;
            case COIN -> c.hudCoinX;
            case TNT -> c.hudTntX;
            case INVENTORY -> c.hudInvX;
        };
    }

    public static int y(Element e) {
        SpotHelperConfig c = SpotHelperConfig.INSTANCE;
        return switch (e) {
            case ARMOR -> c.hudArmorY;
            case EFFECTS -> c.hudEffectsY;
            case COIN -> c.hudCoinY;
            case TNT -> c.hudTntY;
            case INVENTORY -> c.hudInvY;
        };
    }

    public static void set(Element e, int x, int y) {
        SpotHelperConfig c = SpotHelperConfig.INSTANCE;
        switch (e) {
            case ARMOR -> { c.hudArmorX = x; c.hudArmorY = y; }
            case EFFECTS -> { c.hudEffectsX = x; c.hudEffectsY = y; }
            case COIN -> { c.hudCoinX = x; c.hudCoinY = y; }
            case TNT -> { c.hudTntX = x; c.hudTntY = y; }
            case INVENTORY -> { c.hudInvX = x; c.hudInvY = y; }
        }
    }

    public static void resetPositions() {
        set(Element.ARMOR, 4, 40);
        set(Element.EFFECTS, 4, 132);
        set(Element.COIN, 4, 24);
        set(Element.TNT, 4, 180);
        set(Element.INVENTORY, 4, 220);
        SpotHelperConfig.INSTANCE.save();
    }

    // ---------------------------------------------------------------- отрисовка в игре

    public static void render(DrawContext ctx) {
        MinecraftClient mc = MinecraftClient.getInstance();
        SpotHelperConfig c = SpotHelperConfig.INSTANCE;
        if (editing || !c.isStatusHudEnabled() || mc.player == null || mc.options.hudHidden) return;

        TextRenderer tr = mc.textRenderer;
        int sw = mc.getWindow().getScaledWidth();
        int sh = mc.getWindow().getScaledHeight();

        for (Element e : Element.values()) {
            if (e == Element.INVENTORY || !enabled(e)) continue; // Inventory HUD рисует InventoryHud.render
            if (e == Element.COIN) pollCoins();

            List<Line> lines = lines(e, mc, false);
            if (lines.isEmpty()) continue;
            int[] size = measure(lines, tr);
            int px = clamp(x(e), 0, Math.max(0, sw - size[0]));
            int py = clamp(y(e), 0, Math.max(0, sh - size[1]));
            draw(ctx, tr, lines, px, py);
        }
    }

    /** Курс обновляется в фоне, пока элемент включён (не чаще раза в 10 с). */
    private static void pollCoins() {
        long now = System.currentTimeMillis();
        if (now - lastCoinPoll < COIN_POLL_MS) return;
        lastCoinPoll = now;
        if (!SpotHelperClient.API.isBusy()) SpotHelperClient.API.refreshCoins(null);
    }

    // ---------------------------------------------------------------- предпросмотр для редактора

    /** Размер элемента в редакторе (с образцом, если реальных данных нет). */
    public static int[] previewSize(Element e) {
        if (e == Element.INVENTORY) return InventoryHud.size();
        MinecraftClient mc = MinecraftClient.getInstance();
        return measure(lines(e, mc, true), mc.textRenderer);
    }

    /** Рисует элемент в редакторе и возвращает {ширина, высота}. */
    public static int[] drawPreview(DrawContext ctx, Element e, int x, int y) {
        if (e == Element.INVENTORY) {
            InventoryHud.draw(ctx, x, y);
            return InventoryHud.size();
        }
        MinecraftClient mc = MinecraftClient.getInstance();
        List<Line> lines = lines(e, mc, true);
        draw(ctx, mc.textRenderer, lines, x, y);
        return measure(lines, mc.textRenderer);
    }

    // ---------------------------------------------------------------- содержимое

    private static List<Line> lines(Element e, MinecraftClient mc, boolean preview) {
        SpotHelperConfig c = SpotHelperConfig.INSTANCE;
        List<Line> out = new ArrayList<>();
        switch (e) {
            case ARMOR -> {
                if (mc.player != null) {
                    for (EquipmentSlot slot : SLOTS) {
                        ItemStack s = mc.player.getEquippedStack(slot);
                        if (s.isEmpty()) continue;
                        if (s.isDamageable()) {
                            int left = s.getMaxDamage() - s.getDamage();
                            out.add(new Line(s, Text.literal(left + " / " + s.getMaxDamage()), pctColor(left, s.getMaxDamage())));
                        } else {
                            out.add(new Line(s, Text.literal(""), 0xFFFFFFFF));
                        }
                    }
                }
                if (out.isEmpty() && preview) {
                    ItemStack helmet = new ItemStack(Items.NETHERITE_HELMET);
                    helmet.setDamage(120);
                    ItemStack chest = new ItemStack(Items.NETHERITE_CHESTPLATE);
                    chest.setDamage(40);
                    out.add(new Line(helmet, Text.literal((helmet.getMaxDamage() - 120) + " / " + helmet.getMaxDamage()),
                            pctColor(helmet.getMaxDamage() - 120, helmet.getMaxDamage())));
                    out.add(new Line(chest, Text.literal((chest.getMaxDamage() - 40) + " / " + chest.getMaxDamage()),
                            pctColor(chest.getMaxDamage() - 40, chest.getMaxDamage())));
                }
            }
            case EFFECTS -> {
                if (mc.player != null) {
                    for (StatusEffectInstance ef : mc.player.getStatusEffects()) {
                        String t;
                        if (ef.isInfinite()) {
                            t = "∞";
                        } else {
                            int sec = ef.getDuration() / 20;
                            t = String.format("%d:%02d", sec / 60, sec % 60);
                        }
                        out.add(effectLine(ef.getEffectType(), ef.getAmplifier(), t, c.statusHudEffectsMode));
                    }
                }
                if (out.isEmpty() && preview) {
                    out.add(effectLine(StatusEffects.SPEED, 1, "1:30", c.statusHudEffectsMode));
                    out.add(effectLine(StatusEffects.STRENGTH, 0, "0:45", c.statusHudEffectsMode));
                }
            }
            case TNT -> {
                List<com.spothelper.tnt.TntTimerManager.Entry> list = com.spothelper.tnt.TntTimerManager.entries();
                int max = com.spothelper.tnt.TntTimerManager.maxHudLines();
                for (int i = 0; i < list.size() && i < max; i++) {
                    com.spothelper.tnt.TntTimerManager.Entry en = list.get(i);
                    String label = en.name != null && !en.name.isEmpty() ? en.name : "ТНТ";
                    String dist = "";
                    if (mc.player != null && en.pos != null) {
                        dist = " · " + Math.round(Math.sqrt(mc.player.squaredDistanceTo(en.pos))) + " бл.";
                    }
                    out.add(new Line(null,
                            Text.literal(label + " " + com.spothelper.tnt.TntTimerManager.seconds(en.remaining) + " с" + dist),
                            com.spothelper.tnt.TntTimerManager.color(en.remaining)));
                }
                if (list.size() > max) out.add(new Line(null, Text.literal("+" + (list.size() - max) + " ещё"), 0xFFAAAAAA));
                if (out.isEmpty() && preview) {
                    out.add(new Line(null, Text.literal("ТНТ 1.5 с · 6 бл."), 0xFFFFFF55));
                    out.add(new Line(null, Text.literal("ТНТ 3.2 с · 12 бл."), 0xFF55FF55));
                }
            }
            case COIN -> {
                HolyWorldData.Snapshot snap = SpotHelperClient.API.getSnapshot();
                double rate = snap.coinRate;
                if (!Double.isNaN(rate)) {
                    String arrow = snap.coinTrend > 0 ? " ▲" : snap.coinTrend < 0 ? " ▼" : "";
                    int col = snap.coinTrend > 0 ? 0xFF55FF55 : snap.coinTrend < 0 ? 0xFFFF5555 : 0xFFFFFFFF;
                    out.add(new Line(null, Text.literal("Коин: " + fmtCoin(rate) + arrow), col));
                } else if (preview) {
                    out.add(new Line(null, Text.literal("Коин: 12.34 ▲"), 0xFF55FF55));
                } else {
                    out.add(new Line(null, Text.literal("Коин: —"), 0xFFAAAAAA));
                }
            }
        }
        return out;
    }

    private static int[] measure(List<Line> lines, TextRenderer tr) {
        int w = 0;
        int h = 0;
        for (Line l : lines) {
            int lw = (l.stack() != null || l.icon() != null ? 20 : 0) + tr.getWidth(l.text());
            w = Math.max(w, lw);
            h += l.stack() != null ? 18 : l.icon() != null ? 20 : 10;
        }
        return new int[]{Math.max(1, w), Math.max(1, h)};
    }

    private static void draw(DrawContext ctx, TextRenderer tr, List<Line> lines, int x, int y) {
        for (Line l : lines) {
            if (l.stack() != null) {
                ctx.drawItem(l.stack(), x, y);
                HealthOverlay.drawDurabilityBar(ctx, l.stack(), x, y); // полоска как в инвентаре
                ctx.drawTextWithShadow(tr, l.text(), x + 20, y + 4, l.color());
                y += 18;
            } else if (l.icon() != null) {
                ctx.drawGuiTexture(RenderPipelines.GUI_TEXTURED, l.icon(), x, y, 18, 18);
                ctx.drawTextWithShadow(tr, l.text(), x + 20, y + 5, l.color());
                y += 20;
            } else {
                ctx.drawTextWithShadow(tr, l.text(), x, y, l.color());
                y += 10;
            }
        }
    }

    private static int pctColor(int left, int max) {
        float pct = max <= 0 ? 1f : left / (float) max;
        return pct > 0.6f ? 0xFF55FF55 : pct > 0.3f ? 0xFFFFFF55 : 0xFFFF5555;
    }

    private static String fmtCoin(double v) {
        return String.format(Locale.ROOT, "%,.2f", v).replace(',', ' ');
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}