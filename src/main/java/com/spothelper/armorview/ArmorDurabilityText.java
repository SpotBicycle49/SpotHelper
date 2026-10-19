package com.spothelper.armorview;

import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;

public final class ArmorDurabilityText {
    private static final EquipmentSlot[] SLOTS = {
        EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET };

    private ArmorDurabilityText() {}

    public static Text build(PlayerEntity p) {
        MutableText out = Text.literal(" §7[");
        for (int i = 0; i < SLOTS.length; i++) {
            if (i > 0) out.append(Text.literal("§7|"));
            ItemStack s = p.getEquippedStack(SLOTS[i]);
            if (s.isEmpty() || !s.isDamageable()) {
                out.append(Text.literal("§8-"));
                continue;
            }
            int pct = (s.getMaxDamage() - s.getDamage()) * 100 / s.getMaxDamage();
            String c = pct > 60 ? "§a" : pct > 30 ? "§e" : "§c";
            out.append(Text.literal(c + pct));
        }
        return out.append(Text.literal("§7]"));
    }
}
